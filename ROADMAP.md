# Android Player — iterative roadmap

We work in small feature branches and PRs based on `main`.
A PR requires a manual phone test before merging. CI is manual-only: no push/PR-triggered runs.

## 01 — Player foundation (completed)
- SAF music directory picker and recursive file loading
- Media3 ExoPlayer, basic speed control and A–B loop
- Basic four-tab navigation

## 02A — Player UX (included in main)
- [x] Honor status/navigation bar insets
- [x] Keep player controls stable during state changes
- [x] Speed range 0.25×–1.50×, reset to 1.0×
- [x] Speed ruler marked at 0.1× intervals

## 02B — Smooth player and large library (PR #3)
- [x] Extract PlaybackController, PlayerScreen, LibraryScreen and LoopRange
- [x] Keep Play/Pause based on playWhenReady, not buffering-time isPlaying
- [x] Restart at A if seeking **before A** or **at/after B** while loop is active
- [x] Update player UI in place; do not recreate controls for seeks/loop callbacks
- [x] Virtualize library rows with ListView recycling
- [x] Keep library tab alive and retain scroll position when changing tabs
- [x] Preserve visible list anchor during rescans and Activity recreation
- [x] Ignore stale overlapping scan results
- [x] JVM unit tests for loop boundary handling
- [ ] Manual performance/behavior verification on a large phone library

## 02C — Fast startup and track navigation (PR #4)
- [x] Persist scanned track list in private atomic JSON cache (load cached snapshot at launch)
- [x] Only rescan on explicit Rescan / folder selection / missing cache
- [x] Persist Library scroll index and offset across full app restarts
- [x] Previous/next buttons with library-sorted queue, without wrapping
- [x] Stop, Next and Repeat on track end; persist selected mode
- [x] A–B loop takes priority over track-end mode
- [x] Separate PlaybackQueue and LibraryCache classes with pure queue tests
- [ ] Device verification with thousands of files and restart/force-stop
- [ ] Later apply same modes to user playlists

## 02D — Speed precision and audio comparison (PR #5)
- [x] Tap the centered speed value (e.g. 1.00×) to enter an integer **25–150%** via numeric keyboard
- [x] Keep slider, entered percent, Reset and the speed label synchronized
- [x] Show **filename BPM → effective BPM** whenever playback speed differs from 1.00×
- [x] Expose controlled comparison of built-in Media3 Sonic with experimental Android AudioTrack mixer; selection applies on next app launch
- [x] Validate BPM and percent rounding with JVM unit tests
- [ ] **Prioritize sound fidelity for 0.8×–1.1×**; 0.25×–0.79× and 1.11×–1.50× can use a faster/less accurate algorithm
- [ ] User listening comparison of Sonic vs Android mixer at 0.80×, 0.90×, 1.10× (percussion and vocals)
- [ ] Compare Rubber Band / SoundTouch, checking Android performance, real-time latency and license obligations
- [ ] Benchmark transitions into/out of high-quality range and looping
- [ ] Select/implement a proven high-quality processor only after listening comparisons, not from marketing claims alone

## 02E — Waveform
- [ ] Asynchronously calculate and cache waveform preview
- [ ] Visually place and adjust markers A/B
- [ ] Tighten A/B timing to avoid audible transition artifacts

## 02F — Background playback and lock-screen controls
- [ ] Extract playback from Activity into MediaSessionService
- [ ] Add a media session / notification for lock-screen and notification shade
- [ ] Show Play/Pause, Previous and Next on lock-screen media controls
- [ ] Keep playback working when screen is off or app is backgrounded
- [ ] Test Bluetooth controls, headset buttons, audio focus and interruptions
- [ ] Foreground-service permissions and notifications per Android API level

## 03 — Single-track BPM
- [ ] Parse a leading `BPM - ` from filename first
- [ ] Analyze BPM if not present, record automatic/manual/filename source
- [ ] Expose correction buttons ×0.5, ×1.5, ×2, ×4 and tap tempo

## 04 — Batch BPM
- [ ] Automatically process newly added tracks and playlists
- [ ] Cache BPM, source and confidence; mark rows by source color
- [ ] Progress, cancel, retry, and batch scheduling

## 05 — Safe rename
- [ ] Preview `BPM - Artist - Track.ext`
- [ ] Explicit confirmation and SAF rename with conflict/error handling

## 06 — Playlists
- [ ] Create/rename/delete playlists
- [ ] Add tracks from Library using a context menu
- [ ] Persist URI-based entries

## 07 — Queue
- [ ] Fourth tab reflects selected playlist
- [ ] Reorder with drag handles; select and play any item
- [ ] Synchronize player controls and playback modes with active queue

## Engineering constraints
- Kotlin, AndroidX Media3, SAF (no blanket storage permission).
- Keep `MainActivity` small: orchestration only; isolate playback logic and screens.
- Keep scanning and later BPM computation off the UI thread.
- A cached library snapshot may be stale; use Rescan to pick up new/deleted files.
- GitHub Actions build runs only when manually dispatched.
- Keep targetSdk=35 and minSdk=26 unless a specific feature requires otherwise.
- CI tests don't replace manual listening on a physical phone.
