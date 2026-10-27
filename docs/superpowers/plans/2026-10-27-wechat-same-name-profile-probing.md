# WeChat Same-Name Profile Probing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically enable each WeChat hook component on an unlisted `versionCode` when exactly one profile with the same `versionName` passes its complete runtime signature probe.

**Architecture:** Keep exact build mappings first. A shared selector evaluates only same-name candidates and persists positive candidate IDs by installed-build identity; every new WeChat process revalidates the selected candidate before installing hooks. Each hook owner resolves its own complete descriptors before mutation, then installs callbacks with an inactive-until-ready guard and per-feature rollback.

**Tech Stack:** Java 17, Android/Xposed, JUnit 4, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-27-wechat-same-name-profile-probing-design.md`

## Global Constraints

- Preserve exact `versionName && versionCode` profiles, current reply OR/legacy fallbacks, native `PendingIntent` reply, feature switches, and process scopes.
- New matching only uses profiles with the same known `versionName`; zero or multiple complete matches disable that experimental component.
- Probe before installing hooks. Complete signatures include class, inheritance, exact methods/fields and modifiers required by the component.
- Cache only positive candidate IDs in module-owned storage under `versionName`, long `versionCode`, WeChat `lastUpdateTime`, and an adapter revision. Revalidate in each process; never cache reflection objects or treat a signature match as behavior verification.
- No 3183 APK is available. Do not claim offline bytecode, hook runtime, real message, image, or OEM verification. Preserve private artifacts in ignored directories and follow `AGENTS.md` signing/privacy rules.
- Before each Gradle invocation in PowerShell set `$env:GRADLE_USER_HOME=(Join-Path (Get-Location) '.gradle')`; the default user-directory wrapper lock is outside this sandbox. Signing inputs come only from the existing ignored local signing files or process environment and must never be echoed.
- Update `docs/wechat-version-adaptation-playbook.md` and affected findings/topic documents in the same change as each mapping or hook adjustment.

## File Structure

- Create `src/main/java/com/oasisfeng/nevo/decorators/wechat/WeChatProfileSelector.java`: build identity, typed candidates, exact-first/unique experimental choice, probe result, and cache keys.
- Create `src/main/java/com/oasisfeng/nevo/decorators/wechat/WeChatProfileCache.java`: module-owned `SharedPreferences` adapter for positive candidate IDs.
- Modify `src/main/java/com/oasisfeng/nevo/xposed/MainHook.java`: supply a dedicated remote cache preference group and make synthetic reply gate installation all-or-nothing.
- Modify `WeChatReplyProfile.java`, `WeChatEmojiHooks.java`, `WeChatAppReplyEvents.java`, `WeChatRecallEvents.java`, `WeChatImageProfile.java`, `WeChatImageEvents.java`, `WeChatImageDownloader.java`, and `WeChatDecorator.java`: same-name candidate lists, full descriptor probes, selected-profile install, diagnostics and per-feature fallback.
- Add focused JUnit tests beside current WeChat tests. Update adaptation playbook, 8.0.79/8.0.77 findings, and affected reply/image/Emoji topic documents.

## Review Focus

- An exact known build also fits another candidate structurally: exact mapping must win and retain its old label and fallback.
- 8.0.77 has two same-name profiles: both passing must yield `ambiguous`, and neither may install.
- Cache points to a profile whose signature changed: discard it, evaluate all same-name candidates once, then persist only a unique positive result.
- Failure on the second of several hook installations: earlier callbacks must remain inert or be unhooked, and no ready state may be exposed.
- Emoji settings become enabled after WeChat starts: an installed compatible hook must honor the new value without reinstallation.

---

### Task 1: Selector and persistent candidate cache

**Files:** Create `WeChatProfileSelector.java`, `WeChatProfileCache.java`; test `src/test/java/com/oasisfeng/nevo/decorators/wechat/WeChatProfileSelectorTest.java`.

**Interfaces:** `BuildId(String versionName, long versionCode, long lastUpdateTime)` with `fromPackageInfo(PackageInfo)` using `getLongVersionCode()` where available; `Candidate<P>(String id, String versionName, long verifiedCode, P profile)`; `Probe<P>.valid(P profile) throws Throwable`; `Selection<P>(P profile, String id, MatchKind kind)` where `MatchKind` is `EXACT`, `EXPERIMENTAL`, `NO_MATCH` or `AMBIGUOUS`; `select(String feature, BuildId build, List<Candidate<P>> candidates, Probe<P> probe, Cache cache)`. `Cache` reads/writes/removes a candidate ID for feature, build and adapter revision; `WeChatProfileCache` backs it with a dedicated module `SharedPreferences` group.

- [ ] Write JUnit tests for exact-first, 3183 unique match, 8.0.77 zero/ambiguous match, crossed-name rejection, a positive cache reused by a new selector instance, stale cache re-probing, and negative result retry on the next process.
- [ ] Run `.\gradlew.bat testDebugUnitTest --tests '*WeChatProfileSelectorTest' --console=plain`; confirm missing API or failing assertions.
- [ ] Implement the interfaces above. Exact matches bypass candidate ambiguity but still use the caller's signature probe; experimental cache hits re-probe that candidate and enumerate only if it fails. Cache key includes `lastUpdateTime` and adapter revision. `Probe` exceptions count as a failed candidate.
- [ ] Run the focused test; require all cases green. Run `.\gradlew.bat testDebugUnitTest --console=plain`; require exit 0. Review only task files and commit with noreply identity after staged privacy checks.

### Task 2: Notification reply and Emoji

**Files:** Modify `MainHook.java`, `WeChatReplyProfile.java`, `WeChatEmojiHooks.java`, `WeChatDecorator.java`; test `WeChatReplyProfileTest.java`, `WeChatEmojiHooksTest.java`; update `docs/wechat-version-adaptation-playbook.md`, `docs/wechat-8.0.72-findings.md`, `docs/wechat-8.0.77-findings.md`, `docs/wechat-8.0.78-findings.md`, `docs/wechat-8.0.79-findings.md`, `docs/wechat-emoji-notifications.md`, `docs/wechat-reply-debug.md`.

**Interfaces:** Consume Task 1 `select(...)` and `Cache`. `MainHook` obtains `getRemotePreferences("wechat_profile_cache")`, wraps it in `WeChatProfileCache`, and passes it through `WeChatDecorator.Local.configureProfileCache(Cache)` before `hook(...)`; later tasks pass that same cache to their component installers. `WeChatReplyProfile` exposes same-name candidates and `Resolved` with receiver, helper and all three gates; `MainHook` accepts only a unique experimental result with every required descriptor. `WeChatEmojiHooks` selects the 8.0.79 candidate for 3183 after its two exact instance signatures; its existing live enabled predicate remains unchanged.

- [ ] Add failing tests for 3183 accepting a fully resolved 3182 reply/Emoji candidate, changed or missing helper/gate rejecting it, legacy/native fallback, atomic gate failure, and live Emoji toggle.
- [ ] Run `.\gradlew.bat testDebugUnitTest --tests '*WeChatReplyProfileTest' --tests '*WeChatEmojiHooksTest' --console=plain`; confirm expected failure.
- [ ] Resolve all descriptors before installing any reply or Emoji hook. Make gate installation failure close synthetic reply availability and inactivate/unhook any partial callbacks. Preserve the 8.0.72/8.0.78 OR rules and other legacy rules. Update every Task 2 document with experimental semantics and absent 3183 APK evidence.
- [ ] Run focused tests, then all `testDebugUnitTest`; require exit 0. Review staged paths/content/privacy and commit this task with noreply identity.

### Task 3: Application reply and recall events

**Files:** Modify `WeChatAppReplyEvents.java`, `WeChatRecallEvents.java`, `WeChatDecorator.java`; test `WeChat8079AdaptersTest.java`, `WeChat8077AdaptersTest.java`, new `WeChatAppReplyEventsProfileTest.java` and `WeChatRecallEventsProfileTest.java`; update `docs/wechat-version-adaptation-playbook.md`, `docs/wechat-8.0.72-findings.md`, `docs/wechat-8.0.77-findings.md`, `docs/wechat-8.0.78-findings.md`, `docs/wechat-8.0.79-findings.md`, `docs/wechat-in-app-reply-round-plan.md`, `docs/wechat-recall-history.md`.

**Interfaces:** Consume Task 1 selector/cache. Each component exposes `resolve(Profile, ClassLoader)` that returns all checked methods/fields without installing hooks; its install method accepts only the one selected resolved bundle. Both components retain separate ready flags.

- [ ] Add failing tests for 3183 unique selection, 8.0.77 per-component unique/ambiguous outcomes, a wrong method return/field type, and failure after the first hook leaving both callbacks inert.
- [ ] Run `.\gradlew.bat testDebugUnitTest --tests '*WeChatAppReplyEventsProfileTest' --tests '*WeChatRecallEventsProfileTest' --console=plain`; confirm expected failure.
- [ ] Extract non-mutating descriptor resolution, select same-name candidates, then install guarded hooks. Preserve exact mappings, event timing, notification identity, and existing error fallbacks. Update affected docs at the mapping change.
- [ ] Run focused tests and all `testDebugUnitTest`; require exit 0. Review staged paths/content/privacy and commit this task with noreply identity.

### Task 4: Image events and large download

**Files:** Modify `WeChatImageProfile.java`, `WeChatImageEvents.java`, `WeChatImageDownloader.java`, `WeChatDecorator.java`; test `WeChat8079AdaptersTest.java`, `WeChat8077AdaptersTest.java`, `WeChatImageDownloaderTest.java` and new `WeChatImageProfileProbeTest.java`; update `docs/wechat-version-adaptation-playbook.md`, `docs/wechat-8.0.72-findings.md`, `docs/wechat-8.0.77-findings.md`, `docs/wechat-8.0.79-findings.md`, `docs/wechat-image-large-preview-plan.md`.

**Interfaces:** Consume Task 1 selector/cache. Image event and download probes each validate their own full descriptor set and may choose different outcomes. The existing image-preview and large-preview switches still decide installation; selection never starts a download or queries user data.

- [ ] Add failing tests for 3183 event/download unique match, 8.0.77 ambiguity, wrong state getter/download signature, and partial hook failure that leaves `available=false`.
- [ ] Run `.\gradlew.bat testDebugUnitTest --tests '*WeChatImageProfileProbeTest' --tests '*WeChatImageDownloaderTest' --console=plain`; confirm expected failure.
- [ ] Extract complete non-mutating descriptor resolution before each component installs hooks; select only same-name candidates; keep the existing local-image and timeout fallback. Update affected docs at the mapping change.
- [ ] Run focused tests and all `testDebugUnitTest`; require exit 0. Review staged paths/content/privacy and commit this task with noreply identity.

### Task 5: Integration and delivery verification

**Files:** Adjust tests/docs from Tasks 1–4 only where integration evidence requires it; keep build artifacts and logs under `.debug-artifacts/`.

**Interfaces:** Consume every selected component and Task 1 cache; no new runtime API.

- [ ] Re-run Task 1 cache tests across fresh selector/cache instances and the focused tests from Tasks 2–4; inspect failures before the release build.
- [ ] Run `.\gradlew.bat -PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest --console=plain` using existing local signing configuration; require exit 0. Verify Debug and androidTest APK signer SHA-256 equals the existing release certificate. Run `git diff --check`.
- [ ] Review all intended files, staged diff, commit metadata and all commits to be pushed for private identifiers, local absolute paths and credentials. Record that 3183 APK/device behavior was unavailable; do not install, push, or publish as part of this plan.
- [ ] Commit only needed final integration changes with noreply identity; request a whole-branch code review, fix important findings with a failing test first, and rerun the full verification command.
