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

## 02D — Precise speed and compact transport (PR #5)
- [x] Tap the centered speed value to enter an integer percentage; start with a blank, focused field and open the numeric keyboard
- [x] Silently clamp values below 25% to 25% and above 150% to 150%; blank input does not change playback
- [x] Keep slider, label, Reset and effective BPM synchronized
- [x] Show filename BPM and resulting BPM at non-default playback speeds
- [x] Arrange Prev, −10s, Play/Pause, +10s, Next in one compact row
- [x] Remove the experimental Android mixer backend after device testing revealed crackling; keep Sonic as the stable default
- [ ] Manual phone verification before merge

## 03 — Single-track BPM
- [ ] Prefer valid leading `BPM - ` in filename
- [ ] Estimate BPM only if filename has no BPM, store detected/manual/filename source
- [ ] Manual correction with ×0.5, ×1.5, ×2, ×4 and tap tempo
- [ ] Persist results and preserve half-/double-tempo ambiguity for human correction

## 04 — Batch BPM
- [ ] Automatically process newly added tracks and playlists
- [ ] Cache BPM, source and confidence; mark rows by source color
- [ ] Progress, cancel, retry and batch scheduling

## 05 — Safe rename
- [ ] Preview `BPM - Artist - Track.ext`
- [ ] Explicit confirmation, SAF rename, collision handling and error reporting

## 06 — Playlists
- [ ] Create/rename/delete playlists
- [ ] Add tracks from Library using a context menu
- [ ] Persist URI-based entries

## 07 — Queue
- [ ] Fourth tab reflects selected playlist
- [ ] Reorder with drag handles; select and play any item
- [ ] Synchronize player controls and end-of-track modes with active queue

## 08 — Waveform (deferred from 02E)
- [ ] Asynchronously calculate and cache waveform preview
- [ ] Visually place and adjust A–B markers
- [ ] Tighten A–B timing to avoid audible transition artifacts

## 09 — Background playback and lock-screen controls (deferred from 02F)
- [ ] Extract playback from Activity into MediaSessionService
- [ ] Media notification and lock-screen Play/Pause, Previous and Next
- [ ] Keep playback alive with screen off / app backgrounded
- [ ] Test Bluetooth, headsets, audio focus and interruptions
- [ ] Add permissions and foreground-service behavior for supported Android versions

## 10 — High-quality time-stretch DSP (moved to the end)
- [ ] Prioritize fidelity for **0.8×–1.1×**; outside that range, current Sonic is acceptable
- [x] Trial Android audio mixer and Sonic; tester reported audible crackling from mixer, so experimental path was removed
- [ ] Compare Rubber Band / SoundTouch or another proven DSP with license review
- [ ] Benchmark latency, CPU, pitch preservation, percussion transients and A–B looping
- [ ] Integrate a better real-time time-stretch engine only after listening tests

## Engineering constraints
- Kotlin, AndroidX Media3, SAF (no blanket storage permission).
- Keep `MainActivity` small: orchestration only; isolate playback logic and screens.
- Keep scanning and later BPM computation off the UI thread.
- A cached library snapshot may be stale; use Rescan to pick up new/deleted files.
- GitHub Actions build runs only when manually dispatched.
- Keep targetSdk=35 and minSdk=26 unless a specific feature requires otherwise.
- CI tests don't replace manual listening on a physical phone.
