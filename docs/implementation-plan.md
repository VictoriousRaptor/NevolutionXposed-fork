# NevolutionXposed WeChat 8.0.72 adaptation plan

## Goal and current decisions

- Fix notification replies for Google Play WeChat 8.0.72, where text can be
  entered but no message is sent.
- Preserve compatibility with WeChat 8.0.76.
- Finish the local toolchain and compile a debug APK before connecting or
  reading the phone.
- Do not uninstall, downgrade, clear, or otherwise modify WeChat data.
- Keep device APKs, decompiled code, and logs under `.debug-artifacts/`; never
  commit them.

## Repository baseline

- Remote: `https://github.com/helenlawrence2104-luo/NevolutionXposed.git`
- Baseline: `master` at `4baa311` (`v2.0.2`)
- Working branch: `fix/wechat-8.0.72-reply`

Verification commands:

```powershell
git remote -v
git status --short --branch
git log -1 --oneline
```

## Windows build environment

### Required tools

- Microsoft OpenJDK 17 x64
- Android SDK Command-line Tools
- Android platform 34
- Android Build Tools 34.0.0
- JADX 1.5.6
- Git (already installed)

System Gradle, NDK, CMake, emulator, and Android Studio are not required for
the command-line build.

### JDK 17

Install with Winget:

```powershell
winget install --id Microsoft.OpenJDK.17 --exact --source winget `
  --accept-source-agreements --accept-package-agreements
```

Verify:

```powershell
java -version
javac -version
Get-Item Env:JAVA_HOME
```

### Scoop aria2 configuration

Use only the Scoop installation of aria2. Do not install aria2 with Winget.

```powershell
scoop install aria2
scoop config aria2-enabled true
scoop config aria2-split 16
scoop config aria2-max-connection-per-server 16
scoop config aria2-min-split-size 1M
aria2c --version
scoop config | Select-String -Pattern 'aria2'
```

The current Scoop proxy is `localhost:1080`; retain it while it remains
reachable.

### Android command-line tools

```powershell
scoop install android-clt
```

The Scoop manifest configures `ANDROID_HOME` and the SDK command paths. After
installation, reopen PowerShell 7 and verify:

```powershell
sdkmanager --version
Get-Item Env:ANDROID_HOME
```

Accept licenses and install only the components needed for compilation:

```powershell
sdkmanager --sdk_root="$env:ANDROID_HOME" --licenses
sdkmanager --sdk_root="$env:ANDROID_HOME" `
  'platforms;android-34' `
  'build-tools;34.0.0'
```

If SDK Manager cannot download through the existing proxy, read the official
Google repository XML, download the exact package archives with Scoop aria2,
verify their published SHA-1 checksums, and only then extract them into the SDK.
Never install an unverified or partial archive.

Known official package metadata used for the fallback:

| Component | Archive | SHA-1 |
| --- | --- | --- |
| Android platform 34 | `platform-34-ext7_r03.zip` | `1f2e9478d6a7601425ceaa553311dc43191f103d` |
| Build Tools 34.0.0 | `build-tools_r34-windows.zip` | `62cfde1b6fcc3ad12a4d2ba1b537e752768bfd47` |

Both archives were re-verified against these SHA-1 values on 2026-09-10 and
installed offline; see "Offline SDK completion" below.

### JADX

Prefer Scoop:

```powershell
scoop install jadx
jadx --version
```

Do not install a second JADX copy with Winget when the Scoop package is
available.

### Offline SDK completion (2026-09-10)

Platform 34 and Build Tools 34.0.0 were installed from the two SHA-1 verified
archives already stored in `.debug-artifacts/sdk-packages/`, without any SDK
download:

| Component | Archive | Installed result |
| --- | --- | --- |
| `platforms;android-34` | `platform-34-ext7_r03.zip` | `$ANDROID_HOME/platforms/android-34`, `android.jar` = 26,361,808 bytes |
| `build-tools;34.0.0` | `build-tools_r34-windows.zip` | `$ANDROID_HOME/build-tools/34.0.0`, 169 files, `aapt2 version` = `2.19-10229193` |

- The interrupted `build-tools/34.0.0` directory (which contained only
  `.installer/.installData`) was moved to
  `.debug-artifacts/backup/build-tools-34.0.0-incomplete-20260910-230255`
  instead of being deleted.
- Readback check: `sdkmanager --sdk_root="$env:ANDROID_HOME" --list_installed`
  lists both `build-tools;34.0.0` and `platforms;android-34`.

### Verified domestic mirror download plan (2026-09-10)

Goal: serve the Gradle distribution, Android SDK archives, and Maven
dependencies from reachable mirrors, while every downloaded byte is still
checked against an official checksum.

#### Endpoints measured as working

| Purpose | Endpoint | Measured result |
| --- | --- | --- |
| Gradle 8.14.5 distribution (preferred) | `https://mirrors.cloud.tencent.com/gradle/gradle-8.14.5-bin.zip` | HTTP 206, Content-Length 138,068,841; ~45 MiB/s with `aria2c -x 8`; full file SHA-256 equals the official value |
| Gradle 8.14.5 distribution (backup) | `https://mirror.nju.edu.cn/gradle/gradle-8.14.5-bin.zip` | HTTP 206, same file, ~17 MiB/s |
| Official Gradle checksum | `https://services.gradle.org/distributions/gradle-8.14.5-bin.zip.sha256` | HTTP 206; `6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854` |
| Android SDK repository index | `https://mirrors.cloud.tencent.com/AndroidSDK/repository2-3.xml` | Byte-identical to Google's file (SHA-256 `726d5625e45f84e9bca815330e6cf06e4a334330988a90ed4c75ed4a51b3cce6`, 414,703 bytes) |
| Android SDK archives | `https://mirrors.cloud.tencent.com/AndroidSDK/<archive>.zip` | HTTP 206 for `platform-34-ext7_r03.zip` and `build-tools_r34-windows.zip` |
| Maven Central proxy | `https://maven.aliyun.com/repository/public` | HTTP 206 for `kotlin-gradle-plugin`, `kotlin-stdlib` |
| Google Maven proxy | `https://maven.aliyun.com/repository/google` | HTTP 206 for AGP 8.7.3, `androidx.core:1.12.0`, `palette:1.0.0` |
| Gradle plugin portal proxy | `https://maven.aliyun.com/repository/gradle-plugin` | HTTP 206, includes the `de.robv.android.xposed:api:82` jar |
| Official hosts (baseline) | `services.gradle.org`, `dl.google.com` | Reachable but slow, ~0.8 MB/s |

A verified copy of the Gradle distribution is kept at
`.debug-artifacts/downloads/gradle-8.14.5-bin.zip` (ignored by Git) for offline
reuse or wrapper pre-seeding.

#### Measured as unusable — do not retry these

- Tsinghua TUNA: `/gradle/`, `/maven/`, and `/maven2/` all return 404. TUNA does
  not mirror the Gradle distribution or Maven repositories.
- USTC, BFSU, and Aliyun `/gradle/` all return 404. Aliyun only mirrors Maven.
- Dalian Neusoft `mirrors.neusoft.edu.cn/android/repository/` is unreachable
  from this machine.

#### Download and verification rules

1. Fetch the official checksum first, then download from the mirror; discard and
   re-download on any mismatch.
   - Gradle: official `.sha256` goes into `distributionSha256Sum` so the wrapper
     verifies the archive itself.
   - Android SDK: the mirror's `repository2-3.xml` must stay byte-identical to
     Google's, and its `<checksum>` values are used to verify downloaded zips.
2. Point the wrapper at the mirror while keeping the official checksum:

   ```properties
   distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.14.5-bin.zip
   distributionSha256Sum=6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854
   ```

   Fallback: restore `distributionUrl` to
   `https://services.gradle.org/distributions/gradle-8.14.5-bin.zip`; the
   checksum stays valid.
3. Large archives may be fetched in parallel (measured ~45 MiB/s):

   ```powershell
   aria2c -x 16 -s 16 -k 4M -c -d $env:TEMP -o gradle-8.14.5-bin.zip `
     'https://mirrors.cloud.tencent.com/gradle/gradle-8.14.5-bin.zip'
   ```

4. Redirect Maven traffic to the Aliyun mirrors from `~/.gradle/init.gradle` so
   the repository's own `build.gradle` stays untouched:

   ```groovy
   def mirrors = [
       'https://maven.aliyun.com/repository/public',
       'https://maven.aliyun.com/repository/google',
       'https://maven.aliyun.com/repository/gradle-plugin',
   ]
   def applyMirrors = { repos ->
       repos.clear()
       mirrors.each { m -> repos.maven { url m } }
   }
   settingsEvaluated { settings -> applyMirrors(settings.pluginManagement.repositories) }
   allprojects {
       buildscript.repositories { applyMirrors(delegate) }
       repositories { applyMirrors(delegate) }
   }
   ```

   Validated on 2026-09-10: AGP 8.7.3, Kotlin 2.0.21, and the AndroidX
   artifacts resolve from `maven.aliyun.com/repository/google` and
   `.../public`, and a full `--refresh-dependencies clean assembleDebug`
   succeeds in 45s (first build over the original repositories took 4m46s).
   The script is installed at `~/.gradle/init.gradle`; rename or delete it to
   go back to the original repositories.
5. When running inside a network-restricted session, add the local proxy
   explicitly: `aria2c --all-proxy=http://127.0.0.1:1080 ...`, and
   `systemProp.https.proxyHost=127.0.0.1` /
   `systemProp.https.proxyPort=1080` for Gradle. Note that `curl` and
   `Invoke-WebRequest` fail in that environment with
   `schannel: AcquireCredentialsHandle failed (SEC_E_NO_CREDENTIALS)`;
   use `aria2c` or a JVM-based downloader instead.

## Compile-first milestone

Create ignored `local.properties` with the resolved SDK path:

```properties
sdk.dir=C:/Users/Burning/scoop/apps/android-clt/current
```

The repository currently has no `gradlew.bat`. Bootstrap its existing Gradle
Wrapper JAR with JDK 17:

```powershell
java -classpath '.\gradle\wrapper\gradle-wrapper.jar' `
  org.gradle.wrapper.GradleWrapperMain --version

java -classpath '.\gradle\wrapper\gradle-wrapper.jar' `
  org.gradle.wrapper.GradleWrapperMain clean assembleDebug
```

Add the standard Windows `gradlew.bat` matching Wrapper 8.14.5, then make the
repeatable build command:

```powershell
.\gradlew.bat clean assembleDebug
```

Verify the output before any phone work:

```powershell
Get-ChildItem -Path '.\build\outputs\apk' -Recurse -Filter '*.apk'
Get-FileHash -Algorithm SHA256 `
  '.\build\outputs\apk\debug\NevolutionXposed-debug.apk'
```

Compile-first acceptance criteria:

- JDK 17, Android 34, Build Tools 34.0.0, and JADX report valid versions.
- The project compiles from a clean checkout without Android Studio.
- The debug APK is non-empty, parseable, and has a recorded SHA-256 hash.
- No ADB command or phone connection occurs during this milestone.

### Compile-first status (2026-09-10)

Done, including the JADX prerequisite for the deferred phone phase.

- JDK 17.0.20.1 (`JAVA_HOME` machine level), `platforms;android-34`, and
  `build-tools;34.0.0` all report valid versions.
- `gradlew.bat`, `gradlew`, and `gradle/wrapper/gradle-wrapper.jar` were
  regenerated from the SHA-256 verified Gradle 8.14.5 distribution, so they are
  the official 8.14.5 wrapper files.
- `gradle/wrapper/gradle-wrapper.properties` now points at the Tencent mirror
  with `distributionSha256Sum` set; the wrapper downloaded and verified the
  distribution from it.
- Ignored `local.properties` contains
  `sdk.dir=C:/Users/Burning/scoop/apps/android-clt/current`.
- `.\gradlew.bat clean assembleDebug` succeeded, and a repeat run with
  `--refresh-dependencies` (dependencies pulled through the Aliyun mirrors)
  produced a byte-identical APK.

Output:

| Item | Value |
| --- | --- |
| APK | `build/outputs/apk/debug/NevolutionXposed-debug.apk` |
| Size | 1,709,668 bytes |
| SHA-256 | `a07d5e85b216f89ec1070ae7573cea0703251fb946f13a47491c6dff1fbbe77f` |
| package | `com.oasisfeng.nevo.xposed`, versionCode 4, versionName 2.0.2 |
| SDK levels | compileSdk 34, targetSdk 34, minSdk 26 |
| entry point | `assets/xposed_init` = `com.oasisfeng.nevo.xposed.MainHook` |

Side effects worth knowing:

- AGP installed `platform-tools` 37.0.1 into the SDK during the build, so `adb`
  now exists for the deferred phone phase.
- The build log shows intermittent `SSLHandshakeException` warnings while AGP
  probed Google's SDK manifest, and an "SDK XML version 4" warning from the
  older AGP. Both are non-fatal; the build and the installed packages are
  unaffected.
- JADX 1.5.6 is installed through Scoop (`jadx --version` reports `1.5.6`),
  satisfying the last acceptance item.

## Reply implementation

### Native reply path

- Always prefer WeChat's original reply `PendingIntent` and original
  `RemoteInput` result key.
- If `CarExtender` includes a valid reply `PendingIntent` but omits its
  `RemoteInput`, wrap that original `PendingIntent` with the known fallback
  result key instead of inventing a broadcast to an obfuscated receiver.
- Preserve ClipData, the RemoteInput bundle, target package, history, and
  required extras.
- Do not update the notification as if the reply succeeded when dispatch fails.

### Synthetic reply path

- Only expose a synthetic reply action after the target WeChat receiver has
  been found and its signature validated in the current process.
- Return a dispatch success/failure result from the synthetic invocation.
- On synchronous failure, retain the notification and log the failed stage.
- Unknown versions without a usable native PendingIntent must not show a reply
  action that cannot send.

### Version profiles

After the compile-first milestone and only after phone access is resumed, add
an internal `WeChatReplyProfile` for:

- Google Play WeChat 8.0.72
- WeChat 8.0.76

Each profile must contain the verified receiver class, action, RemoteInput
result key, helper method descriptor, and car-mode gate descriptors. Select a
profile using exact version information plus runtime class/method signature
validation. Do not identify obfuscated classes from a short method name alone.

### Safe diagnostics

Use consistent events such as:

```text
NX_REPLY stage=<stage> path=<native|synthetic> notificationId=<id>
```

Logs may include stage names, class/method signatures, result keys, input
lengths, and exception stacks. They must not include reply text, contact names,
account IDs, notification contents, or serialized intents containing private
data.

## Deferred phone phase

Do not begin this section until the compile-first milestone is complete and the
user explicitly resumes phone debugging.

1. Read Android build, model, ABI, WeChat version name/code, installer, APK
   paths, LSPosed version, and module scope.
2. Pull every base/split APK returned by `pm path com.tencent.mm` without
   modifying the installed app.
3. Record SHA-256 hashes and decompile local copies with JADX.
4. Search for these semantic anchors:
   - `MM_AUTO_REPLY_MESSAGE`
   - `key_voice_reply_text`
   - `RemoteInput.getResultsFromIntent`
   - `android.car.EXTENSIONS`
   - `MMAutoMessageReplyReceiver`
5. Capture privacy-safe `MainHook`, `WeChatDecorator`, `MessagingBuilder`,
   AndroidRuntime, and LSPosed logs while reproducing one reply.
6. Populate and validate the 8.0.72 profile from the installed Google Play APK.

### Device-phase runbook (ready to run)

Status 2026-09-11: steps 1-4 are done for the connected phone (WeChat 8.0.72,
versionCode 3085, Google Play). The verified mapping and the root cause of the
silent reply failure are written up in `docs/wechat-8.0.72-findings.md`.
Step 5-6 are also done: the notification reply was reproduced, fixed and
confirmed delivered, and a release APK (recommended scope
`com.android.systemui` + `com.tencent.mm`) is installed on the phone.
Final verification on 2026-09-11: incoming notifications show their real text and
the notification reply is delivered, so the compile, reply and phone milestones
are all complete. The 8.0.76 profile is preserved but not live-tested.

Prerequisites: the phone is connected with USB debugging enabled and this
computer is authorized; LSPosed is installed with `com.tencent.mm` in the module
scope. As of 2026-09-10 23:55 no device is attached (`adb devices` is empty), so
the phone phase has not started.

Read-only collection first — this never installs, uninstalls, force-stops, or
clears anything:

```powershell
.\tools\collect-device-info.ps1
.\tools\collect-device-info.ps1 -Decompile   # adds JADX output and anchor search
```

It writes `device.txt`, `wechat-package.txt`, `wechat-apk-paths.txt`,
`framework-packages.txt`, `apks/`, `apk-sha256.txt`, and, with `-Decompile`,
`sources/` plus `anchors.txt` under `.debug-artifacts/device/<timestamp>/`.
The script aborts with a clear message when no device, an unauthorized device,
or several devices are present.

Then, only after the collected facts confirm the 8.0.72 layout:

```powershell
.\gradlew.bat assembleDebug
adb install -r .\build\outputs\apk\debug\NevolutionXposed-debug.apk
```

Log capture for one reproduced reply stays privacy-safe (stage, class, method,
result key, and input length only; never message text or contact names):

```powershell
adb logcat -v threadtime | Select-String 'NX_REPLY|MainHook|WeChatDecorator|MessagingBuilder'
```

`NX_REPLY` stage events already exist in the working tree (`MainHook.logReply()`
plus emissions in `MessagingBuilder`), so this filter matches current builds.
Those two files still hold uncommitted reply-path work.

## Test matrix

- Build and static checks on Windows with JDK 17 and Android 34.
- Pure Java tests for profile selection, signature rejection, unknown-version
  fallback, and result-key propagation.
- Deferred 8.0.72 device tests: direct/group chat; Chinese, English, emoji;
  foreground/background/stopped/locked; concurrent notifications.
- Verify no duplicate send, false success, notification loss, or regression in
  read, click, voice-call, and video-call notifications.
- Preserve 8.0.76 mappings and automated coverage. If no 8.0.76 test device is
  available, report live regression testing as pending rather than claiming it
  passed.
