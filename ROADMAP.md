# BPM Player — iterative roadmap

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
- [x] Manual phone verification reported successful

## 03 — Single-track BPM (PR #6)
- [x] Automatically estimate on first selection even when filename BPM exists; reuse a saved estimate and allow Detect again
- [x] Detect BPM from a single selected audio file via MediaExtractor/MediaCodec on a worker thread
- [x] Compute a first-pass attack-envelope autocorrelation estimate over at most 70 seconds
- [x] Store filename, detected and manual sources independently for side-by-side comparison; default to filename unless manually overridden
- [x] Keep detection, Tap Tempo, direct input and multipliers inside a dismissible BPM adjustments dialog
- [x] Present half/double-tempo alternative and avoid claiming an estimate is exact
- [x] Keep adjusted playback BPM synchronized with actual speed
- [x] Tap Tempo: ordered nanosecond intervals, drop fastest/slowest 25%, average middle 50% after 9 taps
- [x] Reset the tap window on dialog close or after 3 seconds idle with a message
- [x] Compact Player: one mode button with three uniform PNG icons, A/B/clear-loop icon controls, reset-speed PNG; shorter speed ruler
- [x] Remove BPM dialog layout jump: keep Clear manual override visible but disabled until needed, reserve status space
- [x] Show detected tempo alternatives for BOTH half (×0.5) and double (×2), useful for fast jazz
- [x] Remove the always-visible Tap BPM instructions and keep only brief status/error messages
- [x] JVM tests for synthetic rhythms, trimmed tap window and missed/double taps
- [ ] Validate detection quality on real swing/jazz/pop/dance tracks on a phone
- [ ] Consider spectral-flux or a more accurate onset model if rhythmic confidence is low

## 04 — Batch BPM and scan progress (PR #7)
- [x] Explicit **Batch BPM** action in Library, with Cancel and determinate progress
- [x] Analyze missing detected BPM even when filename has a BPM prefix; keep filename/manual priority
- [x] Skip previously saved detected results, so interrupted batches resume without repeating work
- [x] Limit concurrent MediaCodec decoders to **two total**, including foreground one-track analysis
- [x] Decode off the UI thread, persist results in small durable batches and throttle progress updates
- [x] Color-code BPM provenance in recycled Library rows (F filename, D detected, M manual)
- [x] Show scanned-file count and indeterminate progress during both initial folder pick and Rescan
- [x] Keep library scroll position, playback controls and saved cache behavior unchanged
- [x] Add JVM policy tests for skipping saved detections and progress arithmetic
- [ ] Verify real-device performance with hundreds/thousands of tracks, playback running, cancellation and retry
- [ ] Future: optional auto-enqueue newly added tracks on Rescan and playlist additions (user opt-in)
- [ ] Future: stronger audio onset model when detection confidence is low

## 05 — Safe rename and Library polish (PR #8)
- [x] Accept filename BPM up to 800 (fixes `F 312` disappearing due to the old 30–300 filter)
- [x] Use compact Library badges `F 312`, `D 128.5`, `M 140` without repeating BPM
- [x] Add three vertical dark dividers between the four top-level tab buttons
- [x] Long-press a Library track to preview a new `BPM - existing-name.ext` using its active BPM
- [x] Replace an existing BPM prefix rather than duplicating it; preserve extension and do not invent artist metadata
- [x] Require confirmation, persist SAF write grant and the provider's supports-rename flag
- [x] Check same-folder filename collisions before preview and again before rename
- [x] Rename via SAF on a worker; never copy media files or ask for blanket storage access
- [x] Migrate saved BPM metadata when SAF changes the file URI; synchronize cached Library and playback queue
- [x] Record parent document URI in a version 2 Library cache for fast sibling checks; first launch after upgrade performs one fresh scan
- [x] Allow rename of the currently loaded track: after confirmation stop/release media, rename, restart from 00:00, and recover original playback on failure
- [x] Keep rename blocked during active folder scans or batch BPM analysis
- [x] Clear persisted manual BPM after a successful rename because the value is now in the filename; retain detected BPM for comparison
- [x] A second tap on the currently loaded song is a no-op (does not restart audio or clear the A–B loop)
- [x] Selecting a different song from Library starts it without navigating away from Library
- [x] Use numeric BPM ascending for ALL filename/manual/detected sources, with unknown tempos afterward alphabetically
- [x] Apply batch BPM ordering in coalesced updates, preserve scroll by URI and update virtual All Tracks playback queue without restarting the current track
- [x] Add JVM tests for above-300 BPM parsing, naming, collisions and queue-reference updates
- [ ] Verify Android SAF rename on real storage providers and check cache/BPM across a full app restart
- [ ] Later: bulk rename preview/confirm when a reliable batch workflow is needed

## 06 — User playlists and playlist playback (PR #9)
- [x] Library remains the virtual **All Tracks** playlist, sorted by BPM; custom playlists use explicit URI order
- [x] Store each user playlist as an app-private list of SAF content URI strings, without duplicating audio files
- [x] Create, rename, delete, select and persist multiple playlists
- [x] Long-press a Library track → **Add to playlist** or **Rename with BPM**
- [x] Add/remove tracks without duplicates, move items up/down and preserve insertion/manual order
- [x] Play a track from a selected playlist and keep its ordering for Previous/Next and Stop/Next/Repeat
- [x] Present the active source in Queue, with tap-to-play while preserving queue context
- [x] Update all playlist URI references when SAF rename changes an audio file URI
- [x] Show unavailable tracks after deletion/folder change, skip missing files during playback
- [x] Reconcile live playlist changes without restarting the current track
- [x] Separate PlaylistCatalog, PlaylistStore, PlaylistCoordinator, PlaylistsScreen and QueueScreen
- [x] JVM tests for ordering, uniqueness, rename/delete, URI migration, and queue selection
- [ ] Verify on a real phone, especially playlist persistence across process restarts and rename/missing-file handling

## 07 — Advanced queue UX (PR #10)
- [x] Fourth tab displays the active playback queue (All Tracks or the selected user playlist)
- [x] Select and play any available queue item without losing source order
- [x] Synchronize Previous/Next and end-of-track modes with the chosen playback source
- [x] Long-press-and-drag ≡ handle to reorder songs **in Playlists**, alongside existing Move up / Move down actions; save the order immediately
- [x] Queue stays read-only for every source: it shows the active playing order and allows choosing a track; All Tracks remains BPM-sorted
- [x] Reorder by URI relative to the drop target so missing files keep their positions in saved playlists
- [x] Auto-scroll long playlist editors near the top/bottom while dragging, highlight the drop target, provide haptic feedback
- [x] Preserve the currently playing song and position when editing its active playlist, update Queue and Previous/Next
- [x] Gesture navigation: horizontal swipes between Player ↔ Library ↔ Playlists ↔ Queue without crossing boundaries
- [x] Exclude buttons, seek bars, edit fields, playlist drag handles, system Back edges and vertical ListView gestures
- [x] Queue "Current" button jumps to the playing track; active playlist source is shown clearly
- [x] Refresh the Playlists editor on tab entry so saved playlist edits remain visible without rebuilding views
- [x] Pure JVM tests for URI-relative reorder and horizontal/vertical swipe discrimination
- [ ] Verify drag/drop hit zones, long-list auto-scroll and swipe conflicts on a real Android device
- [ ] Future: Android Back source-aware history once playback is service-owned (stage 09)

## 08 — Local waveform with swipe seeking (PR #11)
- [x] Build and cache waveform asynchronously using MediaCodec/MediaExtractor and one background worker
- [x] Share the fair two-decoder limit with foreground/Batch BPM to keep the player responsive
- [x] Improve sample resolution to one amplitude bucket per **50 ms** instead of 512 buckets for the whole track
- [x] Persist version-2 waveform cache atomically, up to ~30 KB for 25-minute songs; old low-resolution cache is recalculated once
- [x] Eliminate periodic waveform shimmer by anchoring drawn peak groups to **absolute 50ms audio bins**, instead of regrouping around the moving viewport
- [x] Animate the visible waveform at display refresh (vsync), sampling ExoPlayer's live media position rather than stepping with the 120ms Player UI ticker
- [x] Replace raw per-frame player-position sampling with an interpolated visual clock, preserving the screen position across pause/resume and tempo changes
- [x] Distinguish **seek requested** from Media3's **seek confirmed** so stale positions cannot cause a brief backwards jump after a tap or swipe
- [x] Ignore old Media3 position samples for a short bounded interval during seek settlement; do not snap on tiny SEEK_ADJUSTMENT events
- [x] Regression tests for seek-settling grace period, repeated seeks, stale Play/Pause position and tempo transitions
- [x] Offload waveform rasterization to a dedicated low-priority worker; both played/muted colors are prepared in the bitmap rather than GPU-tinted each frame
- [x] Eliminate green shimmer from alpha overdraw: clip the **played** and **upcoming** waveform into two strictly nonoverlapping regions in both bitmap and scrub fallback rendering
- [x] Remove remaining green alias shimmer by replacing narrow antialiased bars with a **solid per-pixel waveform envelope** in background bitmaps and fast-scrub fallback
- [x] Fix tile-switching brightness changes by aligning new raster tiles to one **absolute pixel-time grid** from the beginning of the song
- [x] Regression-test peak sampling, preservation of transients and identical absolute sampling for overlapping tiles
- [x] Add regression tests for continuous played/unplayed color split and offscreen playhead clamping
- [x] Never blank a waveform during rapid SeekBar/waveform dragging: show a bounded batched-line preview while the target bitmap is unavailable
- [x] Suppress costly tile rebuild churn during dragging; request the final background tile after finger release, and reject stale tiles that no longer cover the viewport
- [x] Separate decode progress/status callbacks from waveform data so progress text cannot repeatedly reset preview tiles
- [x] Cache second labels to avoid repeated costly String.format/Formatter allocations during animation
- [x] Cap waveform animation to ~30 fps so playback controls and the tempo SeekBar retain main-thread time
- [x] Bound rasterization to one running and one pending tile; discard obsolete renders on fast scrubs and cancel after track/size changes
- [x] Limit tile size to at most two screen widths, including when adjusting the viewport-radius constant
- [x] Test the tile-window budget with normal zoom, tight zoom and track boundaries
- [x] Reduce decoder/UI competition: explicit low Android scheduling priority, periodic worker yield and throttled decode percentage updates
- [x] Rate-limit Logcat "WaveformPerf" diagnostics for >=90ms frame gaps / slow waveform draws so device-specific bottlenecks can be identified
- [x] Add JVM tests for interpolation, speed changes, pausing, seek/loop jumps and reuse of buffered geometry over hundreds of frames
- [x] Stop frame scheduling during pause/buffering, when Player is hidden, while scrubbing (waveform or full SeekBar), and on Activity teardown
- [x] Keep playback-rate changes, A–B loop seeks and other position discontinuities in sync with the actual media clock
- [x] Add JVM regression tests that reject stale 120ms ticker positions during playback and enforce pause/visibility/seekbar gating
- [x] Add generous space between waveform, full-track SeekBar and transport buttons to reduce accidental +10s/−10s presses
- [x] Add regression tests proving the same audio bins retain their identities, heights and smooth X translation as the viewport moves
- [x] Show only a configurable local window around playback (**±10 seconds by default**, see `WaveformView.WINDOW_RADIUS_SECONDS`), now with taller 176dp display
- [x] Keep a playhead and thin, read-only A/B loop markers; shaded region is clipped to the visible window
- [x] Swipe waveform **left to advance**, **right to rewind**; throttle seeks (~80 ms), keep the waveform moving with the finger
- [x] Tap within the visible waveform to seek precisely; no waveform long-press menu or marker dragging
- [x] Set loop points A and B exclusively with existing Player buttons; Clear-loop continues to work as before
- [x] Prevent waveform swipes from switching app tabs; other tab gestures remain unchanged
- [x] Maintain ~25 ms loop-boundary checks while keeping UI position updates near ~120 ms
- [x] JVM tests for local window edge clamping, time mapping, swipe direction/bounds, bin resolution and amplitude normalization plus viewport-stability regressions
- [ ] Verify real-device zoomed waveform, seek responsiveness, waveform caching and no gesture conflicts
- [ ] Future: sample-accurate gapless A–B loop editing (requires DSP-level loop control)
- [ ] Future: support waveform previews beyond 25 minutes with careful resource caps

## 09 — MediaSessionService, background audio, lock-screen controls and Back (PR #12)
- [x] Keep a **single** ExoPlayer/PlaybackController in MusicPlaybackService rather than MainActivity
- [x] Retain playback queue, A–B loop, speed and end mode when Activity is recreated/closed; UI registers and unregisters a listener on service connect/destroy
- [x] Wake A–B ticker immediately on remote Play/Pause and buffering changes, avoiding a stale 1Hz background heartbeat at resume
- [x] Media3 MediaSessionService manages the foreground media notification, system transport controls and task removal while actively playing
- [x] Advertise title metadata; notification opens the Player tab through PendingIntent
- [x] Forward system/Bluetooth Play/Pause/Next/Previous to the same ExoPlayer and PlaybackQueue as the app UI
- [x] Forward system media scrub/seek through the same A–B-aware PlaybackController (not a raw ExoPlayer seek)
- [x] Handle audio focus, pause-on-headphone-disconnect and CPU wake lock for playback with screen off
- [x] Manifest registers mediaPlayback foreground service + required FGS and WAKE_LOCK permissions; add media3-session dependency matching media3-exoplayer
- [x] Separate internal app Binder access from exported MediaSession controllers with an unguessable private installation token
- [x] Android Back on Player returns to **Library** for All Tracks, or **Queue** for a user playlist
- [x] Android Back from Library moves the task to the background without pausing audio; Back from Queue or Playlists returns to Library
- [x] Use OnBackInvokedDispatcher on Android 13+ and onBackPressed on older versions
- [x] Back navigation never resets the queue, track position or audio service; reopening from notification restores UI from the service state
- [x] Add pure JVM BackNavigationPolicy tests for tab/source routing
- [ ] **Verify on physical devices** notification controls and queue navigation; Android Back, Home, lock/unlock, recent-app swipe, task recreation, Bluetooth and audio focus
- [ ] **Verify** system Stop, force-stop, playback after screen off, Bluetooth disconnect and phone interruption; Android/OEM battery-management differences
- [ ] **Verify** that a long A–B loop and non-default speed continue while the Activity is destroyed and re-created

## 10 — Higher-quality time stretching (incremental)

### 10A — Safe DSP foundation and realtime R3 on phone (PR #13)
- [x] Extract existing Sonic ExoPlayer construction into a single AudioEngineFactory, retaining current audio attributes and service lifetime
- [x] Add explicit, tested speed-to-duration ratio and 0.8×–1.1× priority contract
- [x] Add optional Windows PowerShell offline real-time-mode R3 listening comparison script; no GPL code bundled or downloaded by Android builds
- [x] Document GPL vs commercial Rubber Band and LGPL SoundTouch choices
- [x] On-device comparison: R3 was smooth at 0.70× while Sonic produced audible squeaks/artifacts on the same song
- [x] Make R3 the default DSP for local Android builds; keep `-Prubberband=false` as the explicit Sonic fallback
- [x] Implement an opt-in Android NDK + JNI R3 engine and Media3 PCM AudioProcessorChain, gated by -Prubberband=true
- [x] Keep ordinary APK on unchanged Sonic; separate GPL-source setup pins Rubber Band 4.0.0 and uses a local-only source checkout
- [ ] Build and listen on an actual arm64 Android phone, assess startup delay, rate changes and crackles
- [ ] Before distributing any R3-enabled APK to others, choose GPL-compatible app licensing and distribute complete corresponding source, or obtain commercial licensing

### 10B — Release readiness and visual polish (in PR #13)
- [x] Rounded primary action buttons, darker slate background, and highlighted navigation tab
- [x] Remove Player's R3 experimental badge; pin BPM adjustments and loop actions to a fixed bottom dock
- [x] Move track ordinal above Library loaded count in a reserved app footer; show waveform generation status bottom-right without layout shifts
- [x] Space Playlists action row from the chooser and add one-shot, persisted Shuffle for the whole selected playlist
- [x] Keep active queue and ongoing playback stable when shuffling a currently playing playlist
- [ ] Verify compact footer on small-screen devices and shuffle persistence after restart
- [x] Prepare locally signed R3 release APK with keystore outside Git and passwords in temporary environment variables
- [x] Increment versionCode and keep signing key reusable for subsequent updates
- [x] Explain Google Play Protect scanning/unknown-source limitations and Google Play internal testing
- [x] Adopt GPL-3.0-or-later for original BPM Player sources, preserve Rubber Band's GPL-2.0-or-later and Apache-2.0 notices
- [x] Add accessible in-app legal notices and a source revision embedded into release builds
- [x] Add complete corresponding-source packaging with exact app SHA and pinned native Rubber Band source
- [ ] Verify copyright ownership, audit transitive licences and **publish** complete source package before distributing R3 APKs
- [ ] Build signed APK on Windows Android Studio and check install with Play Protect enabled
- [ ] Test UI readability and tabs on phone; preserve waveform and playback behavior

### 10C — Additional listening and A/B comparison
- [ ] Compare known rhythmic tracks at 0.80, 0.85, 0.90, 0.95, 1.05, 1.10
- [ ] Prioritize percussion attacks, cymbal decay, bass and vocals
- [ ] Compare actual realtime output to Sonic on the same phone

### 10D — Stability and performance
- [ ] Benchmark CPU, start latency, drift, buffering, A–B loop wrap, waveform clock and foreground service
- [ ] Preserve current Sonic as fall-back, never switch engines in the middle of a seek

### 10E — Selection and polishing
- [ ] Adopt a new DSP by default only after repeatable audible improvement
- [ ] Evaluate improvements outside 0.80×–1.10× if practical

Past experiment: Android platform mixer produced audible crackles and was removed.

## Engineering constraints
- Kotlin, AndroidX Media3, SAF (no blanket storage permission).
- Keep `MainActivity` small: orchestration only; isolate playback logic and screens.
- Keep scanning and later BPM computation off the UI thread.
- A cached library snapshot may be stale; use Rescan to pick up new/deleted files.
- GitHub Actions build runs only when manually dispatched.
- Keep targetSdk=35 and minSdk=26 unless a specific feature requires otherwise.
- CI tests don't replace manual listening on a physical phone.

## Pre-release Library maintenance
- [x] Library Current jumps to the playing track without changing playback
- [x] Confirmed SAF delete of actual file plus playlist/queue/cache cleanup
- [x] Exclude accepts an existing, user-selected destination folder directly (no parent-folder requirement)
- [x] Saved exclusion destination can be changed at any time; legacy invalid parent setting is ignored
- [x] Stream-and-verify copy using SHA-256 before deleting the original
- [x] Purge BPM and waveform caches for removed tracks; skip named excluded/ and configured destinations by SAF identity
- [x] JVM unit tests for queue deletion and excluded directory policy
- [ ] Verify destination selection, repeated exclusions and rescan on disposable files on Android
