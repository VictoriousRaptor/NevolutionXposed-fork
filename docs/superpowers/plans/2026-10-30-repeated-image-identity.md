# Repeated Image Identity Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement the following task inline with red/green verification.

**Goal:** Repeated identical image messages select their own verified image event without weakening ambiguous fallback.

**Architecture:** Capture the existing verified notification talker/server ID before normalization. Extend the image index to select that exact identity, while retaining the current algorithm for requests without verified metadata.

**Tech Stack:** Java 17, Android, JUnit 4, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-30-repeated-image-identity-design.md`

## Global Constraints

- Reuse existing hook descriptors and exact version gates; no new settings or dependencies.
- Preserve TTL, request budget, source image assignment, reply and recall behavior.
- Use the existing release key and `-PrequireReleaseSigning=true`; no device operations.

## Review Focus

- Same image path must not merge separate message IDs.
- A known target without a path must not fall back to a different image.
- Missing verification must retain ambiguous rejection.
- A changed verified identity must replace an otherwise identical pending request.
- Appended text must continue checking the original image identity.
- The exact image message and its `[图片]` placeholder are mutually exclusive in the expanded render; source history stays intact.
- Mark the pending expanded RemoteViews for a fresh SystemUI inflate before image arrival; if the runtime API is unavailable, keep text and omit the image.
- Later text refreshes during image work get bounded token-checked reattachment retries; never hold the image-loader monitor over `NotificationManager.notify()`.

## Task: Bind image requests to verified notification identity

**Files:** `ImageEventIndex.java`, `ImageNotificationIdentity.java`, `ImagePreviewLoader.java`, `WeChatDecorator.java`, their relevant host/framework tests, and image adaptation documentation.

**Interfaces:**
- `ImageEventIndex.select(String talker, long serverId, long notificationTime, long observedAt, long now)` selects an exact positive server ID or delegates to the existing selection when zero.
- `ImageNotificationIdentity.verified(boolean verified, String talker, long serverId)` creates an immutable validated identity or returns null.
- Requests retain the identity captured by the decorator, include it in duplicate-request checks, and use it during initial selection and continuity checks.

- [x] Run the existing host suite as baseline.
- [x] Add regression tests for the specified selection, identity and duplicate cases; confirm failure.
- [x] Implement exact selection and request integration, retaining legacy fallback.
- [x] Run the complete Debug host suite and compare the repeated-image scenarios against the compiled Release classes. This project does not expose a `testReleaseUnitTest` task. Debug: 232/232; Release core scenario tests: 28/28.
- [x] Synchronize playbook, 8.0.79 findings and image implementation notes.
- [x] Validate the core image-identity patch with signed Debug/test/Release APKs, Release lint and signature checks. The later placeholder/reapply changes still require final validation below. Full Debug lint was stopped after over six minutes without new output during test analysis and is not counted as passed.
- [x] Implement mutually exclusive render-copy presentation and pending-stage SystemUI reapply guard, preserving text fallback.
- [x] Cover the target placeholder, source transcript, view rebind flag and identity through later text in framework fixtures; compile androidTest only because no phone is available.
- [x] Recheck image-resolution/notification-refresh ordering with a bounded retry; retain the documented cross-thread SystemUI boundary and unflagged-old-cache limitation.
- [x] Build and validate the final signed artifacts, independently review the scoped diff, perform the cached privacy audit, and commit the authorized change. No push or device actions.
