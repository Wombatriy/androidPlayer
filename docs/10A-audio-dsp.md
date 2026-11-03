# Stage 10A — Time-stretch DSP foundation

## Design

The working player has a MediaSessionService, queue, precise A–B loops,
waveform and background playback. Its audio engine is still **Media3/Sonic**.
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

## Next integration increment: opt-in native Rubber Band R3

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
