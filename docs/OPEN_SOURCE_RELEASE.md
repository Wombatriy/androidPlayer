# BPM Player — GPL source and release checklist

This is the release checklist for an Android APK or AAB containing
Rubber Band R3. **Do not distribute the R3 build until every release-specific
requirement below has been completed.** This guidance is practical information,
not a substitute for a legal review.

## 1. Scope, chosen GPL version and attribution

- The original BPM Player source and newly created app artwork are offered
  under **GPL-3.0-or-later**, to the extent they are owned by the respective
  contributors. See `LICENSE` (full unmodified GPLv3 text) and
  `legal/COPYRIGHT.md`.
- Rubber Band 4.0.0 is **GPL-2.0-or-later** (Particular Programs Ltd.),
  making GPLv3 a permitted version for its combined use in this application.
  Its own copyright notices and original `COPYING` are retained in the
  complete upstream source snapshot; it does not become "owned" by this app.
- AndroidX Media3 / DocumentFile and Kotlin are supplied separately under
  their permissive **Apache License 2.0** terms. See
  `THIRD_PARTY_NOTICES.txt` and `third_party/licenses/Apache-2.0.txt`.
- JUnit 4 is an **EPL-1.0 test-only** dependency; it is not intentionally
  packaged in production APKs.
- Ensure that every preexisting source file, font, image, and contribution
  can legally be offered under the proposed licence. Copyright ownership
  must be verified before the first public distribution.
- This licence does not apply to users' selected audio files.

## 2. Inspect resolved distribution dependencies

The list in `THIRD_PARTY_NOTICES.txt` is a manually reviewed *direct*
dependency list, NOT a complete dependency scan.

For each release, review the entire runtime graph (including transitive
AndroidX artifacts), native upstream bundled FFT/resampler notices and any
binary resources:

```powershell
.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath -Prubberband=true
```

Record any extra copyright and licence notices that accompany artifacts.
If dependency versions change, repeat the review. Apache 2.0 is
compatible with GPLv3, but compatibility is not a blanket assumption
about every transitive dependency.

## 3. Use one immutable source revision per distributed binary

1. Merge the open-source PR, ensure a clean worktree, and use the exact
   source commit to be released. Tag it for discovery (for example
   `v1.0.0-rc1`) after verifying that its code matches the binaries.
2. On Windows with Android Studio / NDK / CMake installed:

   ```powershell
   .\tools\dsp\prepare-rubberband.ps1
   .\tools\release\create-keystore.ps1  # ONLY if you do not have a key yet
   .\tools\release\build-release.ps1 -Bundle
   ```

   The build script embeds `-PsourceRevision=<exact-Git-SHA>` in the
   application and creates the matching corresponding-source ZIP alongside
   the signed AAB.

3. On subsequent releases, reuse the same upload key and increment
   `versionCode`. A Git SHA in a release binary must match the tree
   the distributor actually built. The script refuses a dirty worktree.
4. For a direct signed APK, use `build-release.ps1` without `-Bundle`.

## 4. What the source archive contains

`tools/release/package-corresponding-source.ps1` constructs
`dist/BPM_Player_corresponding-source_<Git-SHA-prefix>.zip`. It includes:

- All tracked BPM Player app source, tests, documentation, resources,
  Gradle wrapper and build scripts at the exact release Git SHA;
- The **full pinned Rubber Band 4.0.0 checkout** at
  `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`, including
  `COPYING`, `src/ext/kissfft/COPYING`, `src/ext/speex/COPYING`,
  and `single/RubberBandSingle.cpp`;
- `RELEASE_SOURCE_INFO.txt` with the app and upstream revisions plus
  basic build instructions.

The archive intentionally excludes local signing keys, passwords,
untracked files, personal data, and users' music.

A GitHub "Download source ZIP" button on this project alone is NOT
sufficient for the R3 build, because the native upstream source is
gitignored in the normal repository.

## 5. Publication and recipients

- Upload the complete source archive **before or at the same time** as
  the corresponding APK/AAB release. A GitHub Release asset is one
  practical host; ensure your chosen distribution method meets GPLv3
  section 6 requirements, including access, format and durability.
- Make the matching source archive clearly accessible to the people
  receiving a binary. Include the source link in your release page and
  appropriate listing/documentation. The app's About & licences view
  provides the public repository and embedded exact source SHA.
- Keep notices and complete licence texts with the release; do not
  place restrictions on recipients' GPL rights.
- If hosting source through another server, verify it remains accessible
  as required. Do not assume that a temporarily available GitHub link
  alone discharges every GPLv3 obligation.
- Test rebuilding from the ZIP on a separate machine, with a standard
  Android SDK/NDK and appropriately configured signing (your private
  signing key is not included).

## 6. Final Play Store readiness

- Check current Google Play SDK, foreground-service declarations,
  privacy/data-safety policy and licensing rules when submitting.
- Use `build-release.ps1 -Bundle` for an upload-signed .aab, then
  Google Play internal testing for a small group. Do not share the
  R3 APK in advance without meeting the GPL conditions.
- The app's top-right ⓘ button must show GNU GPL text, third-party
  notices, source/warranty, and the exact source revision. Verify
  offline and on a smaller screen.
- Keep the permanent signing/upload key private; licence compliance
  does not imply you must share your secret signing password. Special
  GPLv3 installation-information questions may require separate review
  for particular devices/distribution models.
- Preserve a copy of the matching APK/AAB, SHA-256, full source ZIP
  SHA-256, licence texts, upstream sources, and release date.

## Important legal sources

- GPLv3 text and sections 4–6:
  https://www.gnu.org/licenses/gpl-3.0.html
- Rubber Band official dual licensing:
  https://breakfastquay.com/rubberband/license.html
- Apache 2.0 and GPLv3 compatibility:
  https://www.apache.org/licenses/GPL-compatibility.html
- Android app signing:
  https://developer.android.com/studio/publish/app-signing

This PR prepares compliance. It is not a certification of legal
compliance and does not automatically publish the source archive.
