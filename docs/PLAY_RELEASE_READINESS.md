# BPM Player — Google Play release readiness (API 36)

Use this checklist for the first **1.0.0-rc1** R3-enabled Internal testing
release. Android compilation and native verification must run locally.

## Requirements (verified November 2026)

- Google Play: new mobile apps must target **API 36+** from 2026-08-31.
  https://support.google.com/googleplay/android-developer/answer/11926878
- Native Android apps must support **16 KiB memory pages**.
  https://developer.android.com/guide/practices/page-sizes
- This project pins AGP 8.10.1, Android API 36 and NDK **r28c
  (28.2.13676358)**, with explicit 16 KiB ELF linker flags.
- Android 16 brings edge-to-edge and predictive Back behavior changes:
  https://developer.android.com/about/versions/16/behavior-changes-16

In Android Studio SDK Manager install Android SDK Platform 36, NDK (Side by
side) 28.2.13676358, CMake and SDK Build-Tools 35.0.0. Use JDK 17.

## 1. Freeze the release commit after merging this PR

Run in Windows PowerShell from the project root:

    git switch main
    git fetch origin
    git pull --ff-only origin main
    git status --short
    git rev-parse HEAD
    .\tools\dsp\prepare-rubberband.ps1

The worktree must be clean. Local Android Studio settings are untracked.

## 2. Prepare and validate the full source archive BEFORE Google registration

No AAB, signing key or Google Play registration is needed yet.

    .\tools\release\package-corresponding-source.ps1

Output: dist/BPM_Player_corresponding-source_<Git-SHA-first-12>.zip

The packager runs the archive verifier automatically. Or manually:

    $rev = (git rev-parse HEAD).Trim()
    $zip = ".\dist\BPM_Player_corresponding-source_$($rev.Substring(0,12)).zip"
    .\tools\release\verify-corresponding-source.ps1 -ArchivePath $zip -ExpectedRevision $rev

The ZIP contains app sources, build scripts, Gradle wrapper, licences,
the full **169-file Rubber Band 4.0.0 source snapshot**, and the matching
revision information. It excludes local.properties, .idea, signing keys,
personal file paths and untracked files.

This validates structure, presence, revision and basic provenance, but
does NOT prove legal compliance for all transitive dependencies.
Check the release dependency graph and third-party notices:

    .\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath -Prubberband=true

## 3. Publish corresponding source via GitHub Releases

This can be done BEFORE the Google developer account is verified, provided
that the app source will not change before the AAB build. Install GitHub CLI
(https://cli.github.com/), run gh auth login locally, then:

    .\tools\release\publish-corresponding-source.ps1 -Tag v1.0.0-rc1

The script requires final clean/up-to-date main, refuses an existing release,
verifies the ZIP and asks for explicit PUBLISH confirmation before it
creates a public **source-only GitHub Release**. The uploaded asset contains
all app source and bundled native R3 source, not any executable or secret.

Alternatively: GitHub → Releases → Draft a new release → select a tag pointing
to the exact app revision → upload the verified corresponding-source ZIP.

Open https://github.com/Wombatriy/androidPlayer/releases in a signed-out
browser and confirm that recipients can download the complete source ZIP.
Place its release URL near the Android binary distribution information.

If ANY source code changes before building the AAB, create a new source
archive and release with a NEW tag. Never pretend an older package matches.

The repository's automatically generated "Source code (.zip)" does not
contain the gitignored Rubber Band source and is not enough by itself.

## 4. Build and verify the Android AAB

    .\tools\release\create-keystore.ps1  # ONLY the first time, if key missing
    .\tools\release\build-release.ps1 -Bundle

The script embeds the exact Git SHA, verifies that every native .so has
16 KiB ELF PT_LOAD alignment, and creates the matching source ZIP.
For APK builds it checks 16 KiB APK ZIP alignment as well.

For full AAB ZIP packaging verification, download official bundletool
(https://github.com/google/bundletool/releases) and run:

    .\tools\release\verify-android-release.ps1 -PackagePath .\dist\BPM_Player-1.0.0-rc1-r3-arm64.aab -BundletoolJar C:\Tools\bundletool-all.jar

Verify that bundletool reports PAGE_ALIGNMENT_16K. If bundletool is
absent, the verifier WARNS that AAB packaging has not been checked;
ELF checks alone are NOT sufficient. Inspect App Bundle Explorer in
Google Play Console for 16 KiB compatibility warnings too.

## 5. Phone and Play Console acceptance tests

- Android 16: predictive Back, edge-to-edge status/navigation insets, dialogs.
- Rubber Band R3, smooth seeks, BPM detection, A–B loops and MediaSession.
- SAF folder access, Library Delete/Exclude with disposable test files.
- Test both ordinary and 16 KiB page-size runtime environments if possible.
- Correct key, versionCode, app label/icon, 16 KiB bundle packaging.
- APK/AAB must be compiled from the source revision published in GitHub Releases.
- Full upstream third-party rights/notice review and Google Play App Content.

These scripts are technical safeguards, not a legal opinion.
