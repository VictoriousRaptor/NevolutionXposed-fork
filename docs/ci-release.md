# CI and signed releases

The Android workflow builds debug APKs and runs unit tests for pull requests to
master and pushes to master, fix/** and codex/**. Manual runs on master also
publish a signed release; manual runs on other branches build only.

Push and manual builds sign the debug APK with the same release keystore and
alias as release builds. Download the `debug-apk-release-signed` artifact to
switch between debug and release without a signature conflict (Android's version
code downgrade restrictions still apply). Both variants retain the same package
ID. PR builds do not receive signing secrets and keep the default debug key in
the `debug-apk` artifact; that APK cannot replace a release-signed installation.

Local debug builds also reuse the release signing config when all four Gradle
properties are configured: `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Keep credentials in the user's
Gradle properties or `ORG_GRADLE_PROJECT_*` environment variables, not in the
repository or command-line arguments. Without this config, local debug builds
warn and use the default debug key. Pass `-PrequireReleaseSigning=true` to fail
instead of producing a default-debug-signed APK; trusted CI builds require it.
Merging this workflow into master enables automatic releases on subsequent
master pushes, including the merge itself. Adding it to a feature branch alone
does not enable publishing from master.

After the build job passes, master pushes and manual master runs build and verify a signed release APK
and publish it with SHA256SUMS.txt. Tags use v<versionName>-build.<run number> so multiple
master updates at the same app version have distinct releases. Reruns preserve
an already published release. Android versionCode is 11 for v3.1.3; bump it for the next app
version. CI run numbers do not alter Android versionCode.

Configure these Actions repository secrets separately in each publishing repo:

| Secret | Value |
| --- | --- |
| RELEASE_KEYSTORE_BASE64 | Base64 contents of the existing release keystore |
| RELEASE_STORE_PASSWORD | Keystore password |
| RELEASE_KEY_ALIAS | Signing key alias |
| RELEASE_KEY_PASSWORD | Signing key password |

Use the existing signing key to preserve in-place updates. Missing secrets cause
the release job to fail before publication; no unsigned or debug-signed fallback
is published. The decoded keystore lives in the runner's temporary directory and
is removed when the signing step exits. Secrets are not provided to PR builds.
Fork secrets are not copied to the upstream repository when the PR is merged.

GitHub Actions must be enabled in the fork. The release job requests contents:
write for creating tags and releases; the build job has read-only permissions.
The workflow uses JDK 17, Android 34, Build Tools 34.0.0 and the project's checked
Gradle distribution checksum. Hosted runners use the official Gradle URL.
