#include <jni.h>
#include <rubberband/RubberBandStretcher.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <vector>

using RubberBand::RubberBandStretcher;

namespace {
constexpr size_t kInputFrames = 1024;
constexpr size_t kOutputFrames = 4096;

void throwJava(JNIEnv *env, const char *type, const char *message) {
    jclass klass = env->FindClass(type);
    if (klass) env->ThrowNew(klass, message);
}

struct Bridge {
    RubberBandStretcher stretcher;
    int channels;
    std::array<std::vector<float>, 2> input;
    std::array<std::vector<float>, 2> output;
    std::array<const float *, 2> inputPointers{};
    std::array<float *, 2> outputPointers{};
    size_t startPadding;
    size_t outputDelay;

    Bridge(int rate, int ch, double ratio)
        : stretcher(
              rate, ch,
              RubberBandStretcher::OptionProcessRealTime |
              RubberBandStretcher::OptionEngineFiner |
              RubberBandStretcher::OptionThreadingNever,
              ratio, 1.0),
          channels(ch) {
        if (stretcher.getEngineVersion() != 3) {
            throw std::runtime_error("Rubber Band R3 engine not available");
        }
        stretcher.setMaxProcessSize(kInputFrames);
        startPadding = stretcher.getPreferredStartPad();
        outputDelay = stretcher.getStartDelay();
        for (int c = 0; c < channels; ++c) {
            input[c].assign(kInputFrames, 0.0f);
            output[c].assign(kOutputFrames, 0.0f);
            inputPointers[c] = input[c].data();
            outputPointers[c] = output[c].data();
        }
    }

    void prime() {
        while (startPadding > 0) {
            const size_t n = std::min(startPadding, kInputFrames);
            for (int c = 0; c < channels; ++c) {
                std::fill(input[c].begin(), input[c].begin() + n, 0.0f);
            }
            stretcher.process(inputPointers.data(), n, false);
            startPadding -= n;
        }
    }

    void process(const std::int16_t *pcm, size_t frames, bool finalBlock) {
        prime();
        if (frames > kInputFrames) throw std::runtime_error("Native input block is too large");
        if (frames == 0) {
            stretcher.process(nullptr, 0, finalBlock);
            return;
        }
        if (pcm == nullptr) throw std::runtime_error("Missing PCM input");
        for (size_t i = 0; i < frames; ++i) {
            for (int c = 0; c < channels; ++c) {
                input[c][i] = static_cast<float>(pcm[i * channels + c]) / 32768.0f;
            }
        }
        stretcher.process(inputPointers.data(), frames, finalBlock);
    }

    int drain(std::int16_t *destination, int capacity) {
        int written = 0;
        while (written < capacity) {
            const int available = stretcher.available();
            if (available <= 0) break;

            const size_t n = std::min(
                static_cast<size_t>(available),
                std::min(static_cast<size_t>(capacity - written) + outputDelay,
                         kOutputFrames));
            const size_t retrieved = stretcher.retrieve(outputPointers.data(), n);
            if (retrieved == 0) break;

            const size_t drop = std::min(outputDelay, retrieved);
            outputDelay -= drop;
            for (size_t i = drop; i < retrieved; ++i) {
                for (int c = 0; c < channels; ++c) {
                    const float clipped = std::max(
                        -1.0f, std::min(1.0f, output[c][i]));
                    const int sample = static_cast<int>(std::lround(clipped * 32767.0f));
                    destination[(written * channels) + c] =
                        static_cast<std::int16_t>(sample);
                }
                ++written;
            }
        }
        return written;
    }
};

Bridge *cast(jlong handle) {
    return reinterpret_cast<Bridge *>(static_cast<intptr_t>(handle));
}

template <typename Action>
void withBridge(JNIEnv *env, jlong handle, Action action) {
    try {
        Bridge *bridge = cast(handle);
        if (!bridge) throw std::runtime_error("Rubber Band native engine is closed");
        action(*bridge);
    } catch (const std::exception &e) {
        throwJava(env, "java/lang/IllegalStateException", e.what());
    }
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_wombatriy_androidplayer_player_audio_RubberBandNative_create(
    JNIEnv *env, jobject, jint sampleRate, jint channels, jdouble timeRatio) {
    if (sampleRate < 8000 || sampleRate > 192000 || channels < 1 ||
        channels > 2 || !std::isfinite(timeRatio) ||
        timeRatio < 0.5 || timeRatio > 4.01) {
        throwJava(env, "java/lang/IllegalArgumentException", "Invalid R3 audio format or speed");
        return 0;
    }
    try {
        return reinterpret_cast<jlong>(
            new Bridge(sampleRate, channels, timeRatio));
    } catch (const std::exception &e) {
        throwJava(env, "java/lang/IllegalStateException", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_wombatriy_androidplayer_player_audio_RubberBandNative_close(
    JNIEnv *, jobject, jlong handle) {
    delete cast(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_wombatriy_androidplayer_player_audio_RubberBandNative_setTimeRatio(
    JNIEnv *env, jobject, jlong handle, jdouble timeRatio) {
    withBridge(env, handle, [timeRatio](Bridge &b) {
        if (!std::isfinite(timeRatio) || timeRatio < 0.5 ||
            timeRatio > 4.01) {
            throw std::runtime_error("Invalid time ratio");
        }
        b.stretcher.setTimeRatio(timeRatio);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_wombatriy_androidplayer_player_audio_RubberBandNative_process(
    JNIEnv *env, jobject, jlong handle, jobject pcmBuffer, jint frames, jboolean finalBlock) {
    withBridge(env, handle, [&](Bridge &b) {
        if (frames < 0 || frames > static_cast<jint>(kInputFrames)) {
            throw std::runtime_error("PCM frames must be between 0 and 1024");
        }
        const std::int16_t *pcm = nullptr;
        if (frames > 0) {
            if (!pcmBuffer) throw std::runtime_error("Null PCM buffer");
            auto *bytes = static_cast<std::int16_t *>(
                env->GetDirectBufferAddress(pcmBuffer));
            const jlong capacity = env->GetDirectBufferCapacity(pcmBuffer);
            if (!bytes || capacity < static_cast<jlong>(frames * b.channels * 2)) {
                throw std::runtime_error("PCM input must be a sufficiently large direct buffer");
            }
            pcm = bytes;
        }
        b.process(pcm, static_cast<size_t>(frames), finalBlock == JNI_TRUE);
    });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wombatriy_androidplayer_player_audio_RubberBandNative_available(
    JNIEnv *env, jobject, jlong handle) {
    int result = -1;
    withBridge(env, handle, [&](Bridge &b) {
        result = b.stretcher.available();
    });
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wombatriy_androidplayer_player_audio_RubberBandNative_retrieve(
    JNIEnv *env, jobject, jlong handle, jobject pcmBuffer, jint capacityFrames) {
    int written = 0;
    withBridge(env, handle, [&](Bridge &b) {
        if (capacityFrames < 1 ||
            capacityFrames > static_cast<jint>(kOutputFrames)) {
            throw std::runtime_error("Invalid output frame count");
        }
        auto *bytes = static_cast<std::int16_t *>(
            env->GetDirectBufferAddress(pcmBuffer));
        const jlong capacity = env->GetDirectBufferCapacity(pcmBuffer);
        if (!bytes || capacity < static_cast<jlong>(capacityFrames * b.channels * 2)) {
            throw std::runtime_error("PCM output must be a sufficiently large direct buffer");
        }
        written = b.drain(bytes, capacityFrames);
    });
    return written;
}
