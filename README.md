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

## Iteration 03 — single-track BPM analysis and compact Player

BPM detection starts **automatically** when you select a track for the first
time, including tracks with a BPM prefix in their filename. A saved detected
estimate is reused on later playback; **Detect again** in the BPM window
reruns the analysis. Android MediaExtractor/MediaCodec decodes at most 70 s
on a worker thread while music continues. BeatEstimator uses an onset-energy
autocorrelation: it is a useful estimate but can confuse half/double tempo,
complex swing, quiet intros or weak percussion.

The Player screen now shows only a **compact BPM summary**. The **BPM
adjustments** button below the loop controls opens a scrollable dialog with
the full controls:

- **Filename BPM**, **Detected BPM**, **Manual BPM** are shown separately.
  The active BPM is filename-first by default. A deliberate manual correction
  overrides the filename; detection alone never overrides it.
- **Detect again**, **Enter BPM**, **×0.5 / ×1.5 / ×2 / ×4**, and **Clear manual
  override** are available in the dialog.
- **Tap BPM** uses a monotonic nanosecond timestamp for every tap. Each
  inter-tap interval is inserted into a **sorted ascending list**. After at
  least **9 taps / 8 intervals**, the fastest 25% and slowest 25% are discarded
  and the arithmetic average of the middle 50% produces BPM. Additional taps
  refine the estimate. Accidental double taps and missed beats tend to be
  rejected as outliers.
- The tap sequence resets when the BPM window closes. It also resets after
  **3 seconds without taps**, immediately showing an explanatory message.
  A completed Tap BPM measurement is saved as a manual override.

Other Player controls are shortened to fit a normal phone screen:
**Prev | −10s | Play/Pause | +10s | Next** remain one row; playback end mode
cycles **⏹ Stop → ⏭ Next → 🔁 Repeat** via one icon button. The A–B row has
compact **A**, **B**, and **🗑 Clear** buttons. Redundant `At track end` and
`Loop inactive` text is removed, and the speed ruler is shorter.

### Device verification

1. Open a file `128 - Artist - Track.mp3`: **Filename 128** must remain
   active even after automatic detection finishes. In BPM adjustments,
   **Detected** should appear separately; Detect again must work.
2. Open a file without BPM: an estimate should appear automatically without
   blocking playback. Reopen it: its saved result should display immediately.
3. In Tap BPM, make at least **9 taps** at ~120 BPM. Deliberately tap once too
   early and skip one beat; the middle-window average should stay near 120.
4. Wait idle for 3 seconds: the tap window clears with a message. Close and
   reopen the dialog: it starts empty.
5. Apply ×0.5 or Enter BPM and check the explicit manual override wins.
   Clear it: the filename value returns, or detected if no filename exists.
6. Confirm all main Player controls fit without scrolling on your phone.

Playlists will later be **ordered cached URI lists** (not automatically sorted
or copied music). Background MediaSessionService is planned to keep playback
running after Android Back closes the Activity, with lock-screen controls.

GitHub Actions remains manual-only and has not been dispatched for this PR.
