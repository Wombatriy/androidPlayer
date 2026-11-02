# Roadmap — Android Player

The project is developed in small branches / pull requests off main.
Each PR must pass Android CI and be tested on a real phone before merge.

## Current: 02A — Player UI reliability
Branch: feature/02-player-ux-fixes
- [x] Avoid rebuilding Player view on Play/Pause, seek, and A/B marker changes
- [x] Avoid showing an intermediate zero position when seeking or looping
- [x] Respect Android status and navigation bar insets (especially Android 15+)
- [x] Speed range 0.25x to 1.50x
- [x] Speed reset to 1.00x
- [x] Visual speed ruler with marks at 0.1x and highlighted 1.0x
- [ ] Phone test: check player responsiveness, no visible slider reset, loop and speed behavior

## 02B — Large music library responsiveness
- [ ] Replace eagerly-created rows within ScrollView with a recycled ListView or RecyclerView
- [ ] Preserve library list position when changing tabs and returning to Library
- [ ] Preserve position when opening a track, then returning to Library
- [ ] Keep folder scan off the UI thread; safely handle rescans and cancellation
- [ ] Test with hundreds/thousands of tracks and nested directories

## 02C — Playback navigation and end-of-track modes
- [ ] Previous / next track buttons on Player screen
- [ ] Define active playback queue (selected folder until playlists are implemented)
- [ ] Stop after current track
- [ ] Automatically advance to the next track, stop at queue end
- [ ] Repeat current track
- [ ] Later: integrate playlist-based queue without breaking navigation

## 02D — Better time-stretch quality
- [ ] Baseline: compare Media3 Sonic with a high-quality time stretcher on representative tracks
- [ ] Evaluate native DSP options: Rubber Band / SoundTouch, compatibility and license constraints
- [ ] Test 0.25x, 0.50x, 0.75x and 1.00x on drums, vocals and layered music
- [ ] Investigate platform-dependent output and buffer behavior
- [ ] Keep correct pitch and avoid regressions to A/B looping
- [ ] Choose and integrate an engine only after listening and performance tests

## 02E — Waveform and refined A/B loops
- [ ] Generate waveform overview without blocking UI
- [ ] Display A and B markers on the waveform
- [ ] Seek/adjust markers visually
- [ ] Improve A/B timing beyond 200 ms polling, test seamless looping

## 03 — BPM analysis of one track
- [ ] Prefer filename BPM prefix when present
- [ ] Automatically estimate BPM otherwise, store source and confidence
- [ ] Manual correction, x0.5, x1.5, x2, x4 and tap tempo
- [ ] Explicitly handle half/double-tempo ambiguity

## 04 — Batch BPM processing
- [ ] Automatically analyze tracks added to playlists and newly scanned tracks
- [ ] Persist results with source: filename / detected / manually corrected
- [ ] Color mark track rows according to BPM source
- [ ] Show progress and allow cancel/retry of batch work

## 05 — File renaming
- [ ] Preview desired name: BPM - Artist - Track.ext
- [ ] Rename via SAF with write permission and explicit confirmation
- [ ] Handle naming conflicts / read-only files and report failures clearly
- [ ] Never overwrite an existing file

## 06 — Playlists
- [ ] Create, rename and delete playlists
- [ ] Add from Library via a per-track menu (drag and drop optional later)
- [ ] Persist playlist entries by URI plus display metadata
- [ ] Show playlists in third tab

## 07 — Queue
- [ ] Display selected playlist on fourth tab
- [ ] Reorder tracks using drag handles
- [ ] Play from selected position, synchronize queue and Player
- [ ] Apply playback modes in 02C to the selected playlist

## Constraints
- Kotlin + Media3 unless a tested audio processor is added.
- Folder access through SAF; no blanket storage permissions.
- Retain minSdk 26 and current targetSdk unless there is a documented reason to change.
- A passing CI build doesn't replace device audio-quality testing.
