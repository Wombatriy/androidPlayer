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

The script prompts securely for the keystore password, then builds
`app:assembleRelease`. The resulting APK is copied to
`dist/AndroidPlayer-1.0.0-rc1-r3-arm64.apk` (ignored by Git).

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

## Rubber Band licence checklist BEFORE sharing with friends

Rubber Band R3 v4.0.0 is **GPL-2.0-or-later** or commercially licensed.
The bundled R3 APK is a combined application. Sharing it with even a few
friends counts as distribution. This repository has NOT yet been relicensed.

Before sharing with friends, either:

1. Select an appropriate GPL-compatible open-source licence for the
   **entire combined application** (for example GPL-3.0-or-later, subject
   to reviewing ownership and third-party dependencies); include licence
   notices, copyright statements and all corresponding source code
   (including official Rubber Band release and build scripts) for the
   recipients, and convey their rights to modify/redistribute; or
2. Acquire an appropriate Rubber Band commercial licence and distribute
   in compliance with all other dependencies.

Do not merely tell recipients to "find the GPL code online"; prepare a
complete, reproducible source release or another GPL-compliant offer.
Verify the precise licence and source-distribution obligations for the
chosen release path. This document is not legal advice.

Until that decision is made, use this APK only for **private testing
on your own devices**.
