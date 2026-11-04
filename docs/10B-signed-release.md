# Signed Android APK / Google Play AAB release

## First local RELEASE build

For release readiness use **target API 36**, NDK r28c (28.2.13676358),
AGP 8.10.1, and CMake. Install NDK r28c through Android Studio SDK Manager.
The native build explicitly requests 16 KiB ELF PT_LOAD alignment.

The app now defaults to **Rubber Band R3** for Android builds. Build Sonic
instead with `-Prubberband=false`. All R3 builds require the official,
pinned 4.0.0 source checkout and NDK/CMake. Before attempting the build:

    .\tools\dsp\prepare-rubberband.ps1

From PowerShell, create your **permanent** signing key (just once):

    .\tools\release\create-keystore.ps1

Store the `.jks` file and password in a private backup. Losing them prevents
normal updates to already-installed direct-release APKs. Never upload either
the key or passwords to GitHub, a cloud chat, or to friends.

Build a locally signed arm64 R3 release APK:

    .\tools\release\build-release.ps1

For an upload-signed Google Play **AAB** instead:

    .\tools\release\build-release.ps1 -Bundle

The script prompts for the keystore password, builds the release,
embeds the precise clean Git commit SHA and creates both the binary and
matching corresponding-source ZIP in `dist/`. It validates native
16 KiB ELF alignment; pass the official bundletool JAR to
`verify-android-release.ps1` to validate AAB PAGE_ALIGNMENT_16K
before uploading (and check App Bundle Explorer).
Outputs are named `BPM_Player-1.0.0-rc1-r3-arm64.apk` (or `.aab`)
and `BPM_Player_corresponding-source_<SHA>.zip`.
The ZIP includes the full pinned Rubber Band source tree. It must be
published with the matching release before distribution.

`versionCode` is incremented to 2, `versionName` is 1.0.0-rc1.
Future updates must increment `versionCode` and use the SAME signing key.

**Existing debug installations use a different signing certificate**. You
cannot simply update a debug install with this release APK. Back up your
playlists / data separately before uninstalling your debug app. Uninstalling
the debug app removes its app-private cache and may remove saved SAF folder
permissions; you may have to select the music directory again.

## Play Protect is not a signing certificate authority

Android requires a digital signature, but a valid, locally signed APK
downloaded from a message or file website may still trigger:
- permission to install from the chosen external source;
- a Play Protect scan or "unknown app" warning.

You do NOT need to disable Play Protect. Allow the safety scan where
offered; do not attempt to work around a malicious-app warning.

For a more normal install/update process, use **Google Play internal
testing** (up to 100 testers), followed by closed/open testing. Play Store
distribution requires a Play Console account and an Android App Bundle
(`bundleRelease`) signed for upload, with Play App Signing. Review target
SDK requirements and store policies at submission time.

References:
- https://developer.android.com/studio/publish/app-signing
- https://developers.google.com/android/play-protect/warning-dev-guidance
- https://support.google.com/googleplay/android-developer/answer/9845334

## Open-source and corresponding-source checklist

Original BPM Player source is offered under **GPL-3.0-or-later**.
Rubber Band R3 is licensed under **GPL-2.0-or-later**, which allows GPLv3
for the combined work. AndroidX/Media3 and Kotlin retain Apache 2.0.

Read [the complete release checklist](OPEN_SOURCE_RELEASE.md). In
particular, before distributing an APK/AAB to friends or publishing
through Google Play:

1. Verify ownership/right to relicense every contribution and inspect
   transitive third-party licence requirements.
2. Build from a clean Git tree with pinned official Rubber Band 4.0.0.
3. Create and verify the matching full corresponding-source ZIP with
   `package-corresponding-source.ps1` (included automatically by
   `build-release.ps1`).
4. Publish the source package under GPL-compatible conditions together
   with the corresponding binary, preserving original licence notices
   and the recipients' rights.
5. Verify that About & licences displays the exact source revision,
   the GNU GPL, third-party notices and source instructions.

Creating the ZIP alone is **not** public source delivery. Do not send
the R3 binary until the source package is actually accessible to its
recipients and the applicable GPL conditions are met.
This is release engineering guidance, not legal advice.

## First source-only public release (before Play Console registration)

It is safe to prepare the **complete R3 corresponding-source ZIP** and
publish it on GitHub ahead of receiving the Play developer verification,
provided the future AAB uses the **same final Git commit**.

After merging the final readiness PR to main:

    git switch main
    git pull --ff-only origin main
    .\tools\dsp\prepare-rubberband.ps1
    .\tools\release\package-corresponding-source.ps1

The source packager runs `verify-corresponding-source.ps1`, checks that
Rubber Band, COPYING and build scripts are present, and excludes private
Android Studio config. Optional publication via GitHub CLI
(`gh auth login` before first use):

    .\tools\release\publish-corresponding-source.ps1 -Tag v1.0.0-rc1

The publication script refuses to overwrite an existing release and asks
for explicit confirmation. No signing key is needed for source packaging.
The output is a public source-only GitHub Release with a ZIP asset.

**Do not reuse that source ZIP if another code commit follows**. Rebuild
and republish a matching ZIP under a different tag before distributing
a changed AAB. Full checklist:
[PLAY_RELEASE_READINESS.md](PLAY_RELEASE_READINESS.md).
