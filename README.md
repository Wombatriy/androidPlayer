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
cycles **Stop → Next → Repeat** using three matching teal PNG assets. The
A–B row has compact **A**, **B**, and **Clear** (matching PNG) controls.
Speed Reset uses the same icon family; PNG assets are in
`app/src/main/res/drawable-nodpi/`. Buttons retain accessible labels and
their previous actions. Redundant `At track end` and `Loop inactive` text is
removed, and the speed ruler is shorter.

In BPM adjustments, **Clear manual override** is always present but disabled
until a manual BPM has been saved, so tapping cannot shift the button rows.
The status line also reserves fixed space and the explanatory Tap BPM paragraph
is removed. Detected BPM shows both **½** and **×2** alternatives: a detected
118 BPM therefore displays 59 and 236 as possible metrical interpretations.
Detection itself still does not override filename BPM.

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

## Iteration 04 — large-library BPM and scan progress

The Library tab has **Choose folder | Rescan | Batch BPM**. The old redundant
`Music Library` heading is removed. The main tab bar is separated from
content by two narrow dark rules with a light gap, matching the requested
mock-up.

### Folder scans
Both **Choose folder** (first import or new folder) and **Rescan** use one SAF
scanner on a background executor. A thin **indeterminate progress bar** and a
live count of files/folders visited appear while scanning. SAF does not expose
an advance total for arbitrary directory trees, so showing a percent for
scanning would be misleading. Once enumeration completes, the regular
library snapshot and scroll restoration are retained. Relaunch still loads
the cached snapshot without scanning.

### Batch BPM
Tap **Batch BPM** to analyze the current library snapshot. The button changes
to **Cancel BPM**, and a **determinate progress bar** shows completed/total.
A compact status line shows how many tracks were detected, skipped because
a detection was already saved, and failed. The operation is deliberately
**user-triggered**, not launched automatically at startup or after Rescan.

The batch runs on up to **two worker decoders**, bounded by a semaphore shared
with foreground one-track detection. This limits contention with playback
and other phone tasks. Source lookups, decoder work and metadata writes stay
off the UI thread. Detected records are committed in groups of six to reduce
disk operations; UI updates are grouped and progress is throttled.

A previously detected track is skipped even if it also has a filename BPM.
A filename BPM **does not** mean the song has been analyzed. Filename/manual
priority remains intact; batch analysis never overwrites a manual override.
Canceled or interrupted jobs can be restarted, and previously saved detections
will be skipped. Failed/unreadable tracks can be retried.

Library BPM badges: **F** (teal) = filename, **D** (blue) = detected estimate,
**M** (amber) = manual. Rows remain recycled ListView items; no disk/codec work
is performed by a row renderer.

### Suggested phone checks
1. Choose a fresh folder and confirm the scan progress indicator and found
   count, then press Rescan and confirm the same behavior.
2. Start Batch BPM on a large folder and verify the progress advances while
   playback and navigation remain responsive.
3. Stop halfway through, restart Batch BPM, and confirm previously detected
   tracks count as saved/skipped rather than being decoded again.
4. Verify colored F/D/M badges and that filename BPM is still preferred over
   a detected estimate in Player. Manual corrections must keep priority.
5. Restart the app: the cached library and BPM data must reappear without
   rescanning or reanalyzing already-processed tracks.
6. Leave Batch BPM running during playback, A–B looping, speed changes and
   navigating tabs. Watch battery/heat; mobile hardware differs.

The current batch is owned by the Activity, not a foreground service: closing
the app interrupts it (partial committed results survive). Long-running
background processing is a separate MediaSessionService/work scheduling
consideration for a future step.

GitHub Actions remains manual-only and was not automatically dispatched.

## Iteration 05 — SAF rename, Library labels and navigation plan

- **Fixed F 312:** the old filename BPM parser accepted only 30–300 BPM,
  so a file named `312 - Artist - Title.flac` silently lost its filename
  badge. The accepted range is now **20–800 BPM**, consistent with manual BPM.
- Library's right column now displays just `F 312`, `D 128.5` or `M 140`
  in source-specific colors; the redundant `BPM` suffix is removed.
- The four main tab buttons have a thin black divider **between** each pair,
  in addition to the existing double horizontal line beneath the tab bar.

### Rename one audio file

**Long-press a track in Library**. If its active BPM is known, the app shows
the original name, the suggested replacement and the BPM source. For example:

`Artist - Title.flac` → `128 - Artist - Title.flac`

Existing BPM prefixes are replaced instead of duplicated; the original file
extension is preserved. Fractional BPM is rounded to the nearest whole number
for the filename. The app cannot manufacture missing artist information: a
file named `Title.mp3` becomes `128 - Title.mp3`.

The user must explicitly confirm the rename. Provider-side work is done on a
worker thread. The app checks persisted **write** access, the provider's rename
support, and same-folder filename conflicts (case-insensitively) before showing
the preview and again just before performing the operation. Audio is **not
copied, deleted or overwritten** by the app; however, arbitrary third-party
SAF providers control their own rename semantics, so concurrent external
file changes and provider-specific behavior must be tested on device.

Renaming the currently loaded track is deliberately blocked until playback
is moved into a MediaSessionService. Renaming is also blocked during a library
scan or BPM batch.

SAF may return a different document URI after renaming. The app moves saved
manual/detected BPM metadata to that URI, replaces the reference in its
playback queue and persists the updated Library snapshot. A failure to update
metadata or cache is reported explicitly.

**One-time upgrade note:** library cache format changes from v1 to v2 to
store the source file's parent document URI. On the first app launch after
upgrade, the music directory is scanned again. Subsequent launches use the
cached snapshot as before.

### Android Back behavior planned for step 09

Treat Library as one virtual **All Tracks** playlist. When the user later starts
playback from a custom playlist, the Player's **Back** navigates to Queue;
when started from Library, Back navigates to Library. Back from Library should
minimize the app, with audio continuing in a MediaSessionService. This is a
roadmap requirement only; the Activity still owns ExoPlayer in step 05.

No GitHub Actions jobs have been started automatically for this change.
