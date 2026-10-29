# WeChat Notification Headings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display Markdown H1 and H2 markers in WeChat notification text as different weights at the system default size.

**Architecture:** Extend the bounded `InlineMarkdown` parser with line-start heading ranges. `NotificationMarkdown` applies native spans and the existing MessagingStyle cleanup hook restores only the module-owned H2 medium font after Android sanitization.

**Tech Stack:** Java, Android `Spannable`, `MessagingStyle`, existing LSPosed hook, JUnit and Android instrumentation.

**Spec:** `docs/superpowers/specs/2026-10-30-wechat-notification-headings-design.md`

**Execution status (2026-10-30):** Local implementation, 222 host tests and Debug/AndroidTest compilation completed. The phone was disconnected before the new framework cases and visual checks could run. Those device steps remain open. Signed Release packaging is also unavailable without local `RELEASE_*` properties; an unsigned Release was compiled for inspection only. The parser tests were written before implementation, but their expected failure was not run.

## Global Constraints

- Keep the system MessagingStyle layout and default font size; H1 is bold, H2 uses `sans-serif-medium`.
- Recognize only `# ` and `## ` at line start after at most three spaces; H3 and deeper stay literal.
- Keep raw text for reply, archive, recall, repeated rebuild and disabling Markdown.
- Do not change the existing hook target, version mapping, signature validation, or feature gates.
- Save raw logs and APKs only in ignored `.debug-artifacts/`; clean up the exact test APK after instrumentation.

## Review Focus

- A marker inside fenced code must remain literal; Task 1 tests this.
- An escaped or mid-line hash must remain literal; Task 1 tests this.
- Empty headings, four-space indentation and H3 must remain literal; Task 1 tests these.
- Android can strip `TypefaceSpan` during MessagingStyle cleanup; Task 2 tests H2 recovery after actual framework cleanup.
- Rebuilding then disabling Markdown must restore `#`/`##` and not duplicate spans; Task 2 tests this.

---

### Task 1: Parse H1 and H2 lines

**Files:**
- Modify: `src/main/java/com/oasisfeng/nevo/decorators/wechat/InlineMarkdown.java`
- Test: `src/test/java/com/oasisfeng/nevo/decorators/wechat/InlineMarkdownTest.java`

**Interfaces:**
- Produces: `InlineMarkdown.HEADING_1`, `InlineMarkdown.HEADING_2` style bits in `Result.spans`; `Result.removed` contains each heading prefix.

- [x] Add parser tests for `# First\n ## Second\n~~123~~`, UTF-16 offsets and inline emphasis; assert marker removal and both heading ranges.
- [x] Add boundary tests for fenced code, escaped/mid-line hashes, empty headings, four leading spaces and `### `.
- [ ] Run `testDebugUnitTest --tests '*InlineMarkdownTest'` and confirm the new tests fail for missing heading behavior.
- [x] Implement line-start heading parsing within existing length, span, depth and work limits, preserving newline characters and existing inline parse rules.
- [x] Run `testDebugUnitTest` (including `InlineMarkdownTest`) and confirm all parser tests pass.

### Task 2: Apply and restore native heading styles

**Files:**
- Modify: `src/main/java/com/oasisfeng/nevo/decorators/wechat/NotificationMarkdown.java`
- Modify: `src/main/java/com/oasisfeng/nevo/decorators/wechat/NotificationMarkdownHooks.java`
- Test: `src/androidTest/java/com/oasisfeng/nevo/decorators/wechat/NotificationMarkdownInstrumentation.java`
- Test: `src/androidTest/java/com/oasisfeng/nevo/decorators/wechat/MessageIdentityInstrumentation.java`
- Modify: `docs/wechat-notification-markdown.md`, `docs/wechat-version-adaptation-playbook.md`, `docs/wechat-8.0.79-findings.md`

**Interfaces:**
- Consumes: `InlineMarkdown.HEADING_1` and `HEADING_2` ranges.
- Produces: `NotificationMarkdown.restoreStylesAfterCleanup(CharSequence, Bundle)` for the existing hook callback.

- [x] Add framework checks for H1 bold, H2 medium and unchanged size, actual cleanup/restore, repeated rebuild, disabling Markdown, and H2 with `~~123~~` in one message.
- [ ] Build the AndroidTest APK, run the exact runner with test-package cleanup, and confirm the new heading check fails on the old main APK.
- [x] Implement span application and narrowly scoped H2 font recovery with the existing owner, raw-text equality and signature guards; keep code style recovery intact.
- [x] Update the runner and Markdown/adaptation/version documentation with complete hook semantics, failure behavior and verification boundaries.
- [x] Build and run all host tests, and compile Debug APK and AndroidTest APK; confirm zero host-test failures.

### Task 3: Device acceptance and cleanup

**Files:**
- Modify: `docs/wechat-notification-markdown.md`, `docs/wechat-8.0.79-findings.md` with actual results.

**Interfaces:**
- Consumes: Task 2 Debug and AndroidTest APKs.
- Produces: verified framework results and a real private-chat visual result.

- [ ] Verify the default Debug certificate matches the already installed Debug; install the new main APK under the user's existing test authorization.
- [ ] Run the exact instrumentation runner with `try/finally` removal of `com.oasisfeng.nevo.xposed.test`, then verify the package and runner are gone.
- [ ] Restart only the target WeChat process; have the user restart protected SystemUI, then request a real two-line heading/strike message and record the visible result.
- [ ] Recheck tests, APK hash/signature, `git diff --check`, final diff and privacy-sensitive staged paths before any completion claim.
