# Android Player

An Android music player designed for DJ practice and future BPM analysis.

## Iteration 01 — Player foundation

- Four tabs: **Player**, **Library**, **Playlists**, **Queue** (the last two are placeholders).
- Choose a music directory through Android's Storage Access Framework. Permission persists after restart.
- Browse audio files in that folder and its subfolders.
- Tap a track to play it using Media3 ExoPlayer.
- Seek, pause, skip ±10 seconds, change speed from 0.50× to 2.00× with pitch preservation.
- Set A and B at the current position; playback repeats from A after reaching B.
- Show BPM extracted from a filename such as `128 - Artist - Track.mp3`. No automatic tempo measurement yet.

## Open and test

1. Open the repository root as a project in Android Studio (JDK 17, Android SDK 35).
2. Sync Gradle, then run the `app` configuration on Android 8.0+ (API 26+).
3. Go to **Library → Choose music folder**, select a folder with audio.
4. Tap an audio file, test Play/Pause, seeking, speed control and A/B markers.
5. Navigate between tabs and confirm playback continues while the activity is open.

**Known limitations:** UI is functional but deliberately basic; playback stops when the activity is destroyed (no foreground playback service yet). Very large directories may take time to enumerate. No waveform, BPM detection, playlist persistence, rename or queue ordering yet. A/B looping has ~200 ms polling precision; a sample-accurate loop is future work.

The app uses SAF and does not request unrestricted file access. On Android 11+, system folder picker restricts selecting some system/root directories.

## Next iterations

1. Proper player UI, playback service, waveform, refined A/B loop.
2. Single-track BPM detection and confidence / ×0.5, ×1.5, ×2, ×4 corrections.
3. Batch BPM measurement, persistent results and filename source indicators.
4. SAF-based safe file renaming with preview/confirmation.
5. Persistent playlists and add-to-playlist actions from Library.
6. Playlist-based sortable Queue and drag-and-drop ordering.

## Gradle wrapper / Android Studio setup

All four official Gradle 8.11.1 wrapper files are checked into the repository.
Do **not** install Gradle globally just to build this project.

On Windows PowerShell:

```powershell
git switch feature/01-player-foundation
git pull origin feature/01-player-foundation
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug
```

In Android Studio, use **Sync Project with Gradle Files**. Select **Gradle Wrapper** as the Gradle distribution and **JDK 17** as Gradle JDK. Install Android SDK 35 when prompted.

The repo has a GitHub Actions workflow that attempts a clean debug build for each PR. Local machine-specific files such as `local.properties` should not normally be committed; the workflow discards the checked-in `local.properties` before building.

## Iteration 02B — architecture and tests

This iteration separates `MainActivity` (tab navigation, SAF and scanning),
`player/PlaybackController.kt` (Media3 and A–B constraints),
`player/LoopRange.kt` (pure loop boundary logic), and `ui/PlayerScreen.kt`,
`ui/LibraryScreen.kt`, `ui/SpeedRulerView.kt` (retained views).

The Library tab now uses recycled `ListView` rows rather than eagerly constructing
one view for every file. Its scroll position persists when switching tabs or playing
an item, and the selected scroll anchor is kept on rescan.

The loop is **end-exclusive**: seeking before A or at/after B restarts at A;
seeking within [A, B) stays at the requested position. Play/Pause uses
`playWhenReady` to avoid flashing while playback buffers after seeking.

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` to compile and test.
See [ROADMAP.md](ROADMAP.md) for future improvements. Higher-quality pitch-preserving
time stretching will prioritize playback speeds between **0.8× and 1.1×**.

## Iteration 02C — fast startup and playback queue

On first launch or a selected new folder, the app enumerates the SAF tree in a worker
thread and stores a snapshot in private app storage. **On later app launches the
snapshot is loaded without traversing the folder again**. Press **Library → Rescan**
when files have been added, deleted or renamed. A missing/invalid snapshot is
recovered by scanning again. Playlist and BPM features will build on this metadata.

Library scroll index and pixel offset are saved to preferences on pause and when
leaving the Library tab, so returning after closing/relaunching the app restores
its previous place (after the library snapshot loads).

Player has **Previous / Play-Pause / Next** buttons and the initial queue follows
the alphabetical Library order. The selected track-end mode is remembered:

- **Stop:** stop at the end of this track.
- **Next:** play the next Library item, stopping after the last one.
- **Repeat:** repeat the current track.

An active A–B loop takes priority over these end-of-track modes. Changing tracks
clears the A–B markers. Audio is still owned by the Activity: background playback
and lock-screen Play/Previous/Next controls are planned in ROADMAP.md (09).

### GitHub Actions notifications

The Android build workflow now uses only `workflow_dispatch`, not automatic
`push` or `pull_request` triggers. To run it manually, open **GitHub → Actions →
Android build → Run workflow** and select a branch. Build locally with
`./gradlew :app:assembleDebug :app:testDebugUnitTest` (PowerShell:
`.\\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`).

## Iteration 02D — precise speed and compact controls

- Tap the **centered speed value** to show an **empty input field**. It is focused
  immediately and the numeric keyboard opens without a second tap. Enter an
  integer percentage: `85` = 0.85×.
- Inputs below **25** clamp to **25**, and inputs above **150** clamp to **150**.
  No validation errors or pop-up hints appear. Applying a blank value leaves the
  current speed unchanged.
- Slider, Reset, speed label, and adjusted BPM use the same percentage.
- When a track has BPM in the filename, playback at a different speed displays
  e.g. `Filename BPM: 128 → 102.4 BPM` at 80%. This is a mathematical
  adjustment, not a measured BPM.
- Transport buttons are arranged **Prev | −10s | Play/Pause | +10s | Next**
  in one row, including on narrower phone screens.

### Audio processing

The experimental **Android mixer** backend was removed after phone listening
revealed crackling. **Media3/Sonic remains the default**, including if the old
experimental mode had been selected previously. A dedicated high-quality
real-time time-stretch engine is deferred to the **final roadmap stage**,
after BPM, playlists, queue, waveform, and lock-screen features. The
quality-sensitive speed range is **0.8×–1.1×**.

GitHub Actions remains manual-only; use Android Studio or
`./gradlew :app:assembleDebug :app:testDebugUnitTest` to build and test locally.

## Iteration 03 — single-track BPM analysis

This is an early, deliberately limited BPM estimator. The selected track's BPM
is read in this order: **manual override → BPM prefix in filename → saved
automatically detected BPM**. Automatic analysis is offered only when no BPM
prefix exists; the app does not overwrite or rename audio files.

When a track has no BPM, open **Player → Detect BPM**. On a background thread,
Android's MediaExtractor/MediaCodec decodes up to 70 seconds of local audio into
a small 100-Hz RMS-energy envelope. Pure Kotlin autocorrelation estimates the
attack repetition period. The app continues playing while analysis runs and
stores the estimate (plus source, confidence and a possible half/double
alternative) in app-private preferences. Decoding resources are closed on
completion, cancellation or error.

**Estimates are approximate**: intros, syncopation, halftime/double-time,
swing, low percussion levels and tempo changes can confuse simple onset-energy
autocorrelation. The result is explicitly marked as *detected (estimate)*.
Check against known tempos before relying on it for DJ mixing.

The Player BPM panel also provides:

- Multipliers **×0.5, ×1.5, ×2, ×4** to correct perceived metrical levels.
- **Tap BPM**: tap three or more times on beats, using monotonic timestamps.
- **Enter BPM** for exact manual input between 20 and 800.
- **Reset BPM**: clear the stored override/estimate, returning to the filename
  BPM if present.
- Show the effective tempo at non-1.0× playback speed, without modifying the
  original BPM record.

Results are keyed by the selected file's content URI. If a file is moved or
renamed and receives a new URI, its stored result may no longer apply. Batch
analysis, library-row status colors, confidence-driven reanalysis and file
renaming remain future iterations.

### Manual verification on phone

1. Test a track whose name begins with `128 - `; it should show 128 (filename)
   and not require automatic analysis.
2. Test an MP3 or FLAC without a BPM prefix. Tap **Detect BPM** and check the
   estimate against a known tempo. Music playback should continue.
3. Use ×0.5, ×1.5, ×2, ×4, Tap BPM and Enter BPM; reopen the app and verify
   that a manual correction persists.
4. Reset BPM; verify the filename value returns if the filename has one, or
   that the estimate is cleared for a track without a BPM prefix.
5. Change speed to 0.80×; verify the effective displayed BPM updates while the
   original value remains unchanged.
6. Change tracks while an analysis is running; results from the previous task
   must not overwrite the current track's UI.

The revised roadmap stores playlists as **ordered local URI lists** without
sorting or copying the underlying audio. A future MediaSessionService must
keep audio playing when Android Back closes the Activity and expose lock-screen
transport controls.

GitHub Actions stays **manual-only**. Test compilation locally with
`./gradlew :app:assembleDebug :app:testDebugUnitTest`.
