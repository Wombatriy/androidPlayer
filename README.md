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
