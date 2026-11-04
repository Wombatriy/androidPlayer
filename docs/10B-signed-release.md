# Signed Android APK / future Google Play release

## First local RELEASE build

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
matching corresponding-source ZIP in `dist/`.
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
