# Stage 10A — Time-stretch DSP foundation

## Design

The working player has a MediaSessionService, queue, precise A–B loops,
waveform and background playback. The initial 10A foundation used **Media3/Sonic**;
following phone testing, **Rubber Band R3 is the default** and Sonic is an explicit fallback.
Moving its builder to `player/audio/AudioEngineFactory.kt` creates one
injection point for a future custom Media3 RenderersFactory. It deliberately
does NOT load or package Rubber Band and should not change sound.

`TimeStretchPlanner` defines the speed contract that a native library
must satisfy, including inverse duration and preserved pitch:

| Media speed | Output/input duration ratio | Pitch |
| --- | --- | --- |
| 0.80x | 1.25 | 1.0 |
| 0.90x | 1.111111... | 1.0 |
| 1.00x | 1.0 | 1.0 |
| 1.10x | 0.90909... | 1.0 |

Priority listening range: **0.80x–1.10x**. We must not change the decoder or
switch engines during a speed slider movement unless that transition is tested.

## Evaluate Rubber Band R3 before integrating a native library

Get the official [Rubber Band CLI](https://breakfastquay.com/rubberband/)
and [FFmpeg](https://ffmpeg.org/download.html). Neither is bundled or fetched
by an Android build. With both installed on your Windows machine, run:

    .\tools\dsp\render-r3-samples.ps1 -InputAudio "C:\Music\jazz.flac" -StartSeconds 20 -DurationSeconds 35 -RubberBandExe "C:\Tools\rubberband.exe"

The PowerShell script converts an excerpt to WAV, then calls Rubber Band
with `-3` (R3), `-R` (real-time algorithm) and `-T`
(tempo multiplier). Six exports: 80, 85, 90, 95, 105 and 110 percent.
It never changes the source file.

Listen to these WAVs **at 1.00x** and compare to the original played at
those speeds by Sonic on your Android device. Match the source excerpt and
listening volume. Check cymbal decay, percussion attacks, walking bass, brass
vibrato, vocals and swing feel. These rendered WAVs reveal processing
artifacts, but cannot prove realtime CPU suitability on a particular phone.

## Implemented: native Rubber Band R3 (now default)

1. Select GPL or commercial licensing first. Do not automatically bundle
   third-party GPL sources in a distributable APK.
2. Use Android NDK plus an optional native library with real-time R3
   (OptionProcessRealTime and OptionEngineFiner). Its time ratio is 1 / speed;
   pitch ratio remains 1.0.
3. Connect to Media3 via a custom PCM AudioSink and AudioProcessorChain,
   not by inserting an extra effect in front of Sonic (which would apply
   *two* stretches).
4. Use bounded, frame-aligned PCM blocks, handle source output backpressure,
   getSamplesRequired/available/retrieve and initial padding/start delay.
5. Implement media-position mapping via getMediaDuration; flush/delay handling
   after seeks, looping, changing track or changing speed.
6. Ensure Activity recreation and MediaSessionService background lifetime
   remain intact. Do not recreate ExoPlayer while an A–B loop is playing.
7. Only after sound and performance checks should the app display a choice
   between the working Sonic and real R3 implementations.

Official documentation:
- https://breakfastquay.com/rubberband/code-doc/index.html
- https://developer.android.com/reference/androidx/media3/common/audio/AudioProcessorChain
- https://breakfastquay.com/rubberband/license.html
- https://breakfastquay.com/technology/license.html
- https://www.surina.net/soundtouch/license.html

## Licensing options

- Purely private GPL experimentation needs no publication solely because
  the code is executed privately.
- Rubber Band is GPL version 2 or later. Distribution of an APK combining it
  with the player ordinarily requires licensing the application compatibly
  and delivering corresponding source code to recipients under GPL terms.
- Rubber Band also offers one-time proprietary commercial licences, with or
  without attribution; consult the developer's current price list.
- SoundTouch is LGPL v2.1. Closed-source programs may use it with relevant
  LGPL notices, source and replacement/relinking rights where applicable.
  SoundTouch also offers an alternative commercial licence on request.
- Do not infer that a private GitHub repository alone changes GPL obligations
  after distributing an APK. Seek legal review before public release.

## Acceptance gates

10A — Current Sonic must still build, play, loop, resume and handle Back.
10B — Blind comparison on familiar songs and 0.8–1.1 speeds.
10C — No new clicks, repeated samples, output lag, dropped frames, CPU spikes,
background crashes or waveform clock regressions.
10D — Consider enabling a different default only after measured benefits.

## 10A-native — Direct Android phone test (experimental)

The opt-in R3 implementation now resides in:
- `RubberBandRenderersFactory`: replaces the usual Media3 AudioSink
  chain; it does NOT run Sonic after R3 and it disables the Android mixer.
- `RubberBandPcmProcessor`: frame-aligned PCM16 streaming, end-of-stream
  draining, clock-duration accounting and native state reset on seek/track.
- `RubberBandNative` + `rubberband_jni.cpp`: use R3 with realtime
  processing, fixed-size blocks, start padding and output-delay trimming.
- `app/src/main/cpp/CMakeLists.txt`: builds the JNI bridge plus the official
  Rubber Band 4.0.0 single-source compilation unit only on demand.
- `AudioEngineFactory`: chooses **Sonic** in normal builds and **R3** with
  `-Prubberband=true`. The engine label was removed from Player to keep
  its layout compact; the native engine is selected at build time.

### Prepare your Windows Android Studio installation

Install **Android NDK (Side by side)** and **CMake** from Android Studio →
Settings → Languages & Frameworks → Android SDK → SDK Tools. Use an arm64
phone (most recent Android phones; the initial native build only targets
`arm64-v8a`).

In PowerShell, from the repository root:

    git switch feature/10a-dsp-foundation
    git pull origin feature/10a-dsp-foundation
    .\tools\dsp\prepare-rubberband.ps1
    .\gradlew.bat :app:assembleDebug -Prubberband=true

The prepare script downloads source from
`https://github.com/breakfastquay/rubberband.git`, checks out the
**v4.0.0** tag and verifies pinned commit
`1d95888bec3ae0a17c0c4af791810d5a63f6bc35`.
The source stays **gitignored** in
`app/src/main/cpp/vendor/rubberband`. No binary/native source is fetched
when building the standard Sonic APK.

Install the resulting
`app/build/outputs/apk/debug/app-debug.apk` as usual and start playback.
The Player does not show a separate R3 label. Play tracks at
0.80x, 0.90x, 1.00x and 1.10x; seek, test A–B, lock the screen, use
Bluetooth and vary the tempo while playing.

To return to Sonic and compare on the same phone, rebuild WITHOUT the
experimental flag (the same APK path will be replaced):

    .\gradlew.bat :app:assembleDebug

Install the normal build over the experimental app; preferences/library
should be preserved because the application ID has not changed. Never
install old and new builds concurrently with the same ID.

### Important experiment caveats

This is a **first real-time JNI prototype**, not a confirmed drop-in Sonic
replacement. R3 has substantial CPU cost. Some devices may underrun, skip
audio, fail to decode unusual input PCM, or exhibit initial latency. A–B
wrap and fast seeks are especially important tests. An APK that compiles
is NOT proof of glitch-free audio.

If R3 fails to build or sounds worse, use Sonic and share the complete
Gradle/Logcat error. **Do not merge this prototype until phone testing.**

### Licences before sending R3 APKs

Rubber Band 4.0.0 is GPL-2.0-or-later (unless commercially licensed).
Private evaluation on your own phone is fine, but **giving the R3 APK to
friends is distribution**. Before doing so, ensure the app is
GPL-compatible and recipients can receive its corresponding source, including
the exact Rubber Band release plus all modifications and build scripts.
A public release also needs compatible third-party notices. Alternatively,
purchase an appropriate commercial licence.

This branch intentionally does **not** relicense your pre-existing app
source or bundle third-party source code into the Git repository. You must
approve that release/licence decision before distributing R3-enabled APKs.
The ordinary Sonic APK has no new Rubber Band licence obligations.

## 10B: R3 default + signed release

R3 passed the first on-phone listening test at 0.70× (smoother than
Sonic on the same track). Consequently `-Prubberband=true` is the default
setting, and `-Prubberband=false` explicitly selects Sonic. Sources
still must be installed locally with `prepare-rubberband.ps1`.

For signed release steps, permanent-key security, Play Protect limitations
and GPL licensing before distributing APKs, see
[10B-signed-release.md](10B-signed-release.md).
