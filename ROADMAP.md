# Android Player — iterative roadmap

We work in small feature branches and PRs based on `main`.
A PR requires successful Android CI and a manual phone test before merging.

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

## 02C — Next/previous and playback modes
- [ ] Previous and next track controls
- [ ] Explicit current queue, initially based on selected library folder
- [ ] Stop after current track
- [ ] Advance to next track, stop after final item
- [ ] Repeat current track
- [ ] Later apply same modes to playlists

## 02D — Higher-quality time stretching
- [ ] **Prioritize sound fidelity for 0.8×–1.1×**; 0.25×–0.79× and 1.11×–1.50× can use a faster/less accurate algorithm
- [ ] Baseline Sonic on rhythmic percussion and vocals
- [ ] Compare Rubber Band / SoundTouch or other suitable algorithm, checking Android performance, real-time latency and license
- [ ] Benchmark transitions into/out of high-quality range and looping
- [ ] Select processor only after listening comparisons, not from benchmarks alone

## 02E — Waveform
- [ ] Asynchronously calculate and cache waveform preview
- [ ] Visually place and adjust markers A/B
- [ ] Tighten A/B timing to avoid audible transition artifacts

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
- Keep targetSdk=35 and minSdk=26 unless a specific feature requires otherwise.
- CI tests don't replace manual listening on a physical phone.
