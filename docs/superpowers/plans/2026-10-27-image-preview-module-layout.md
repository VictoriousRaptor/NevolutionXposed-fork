# Module-owned image preview layout repair

**Goal:** Build the fitStart image layout when WeChat cannot query the module package, preserve the existing notification appearance changes, and commit and package signed Release 3.4.0 on master.

**Architecture:** Read the module ApplicationInfo from the attached libxposed framework. Pass it explicitly into image-preview construction. Resolve the hidden RemoteViews(ApplicationInfo, int) constructor by its exact signature, avoiding a direct SDK linkage or a package-name fallback. Constructor failures retain the existing text notification. No changes to image selection, downloads, identity, reply actions or fitStart geometry.

**Execution:** Work in the existing master checkout as explicitly requested. Keep the pre-existing appearance changes; include their complete file set in the final commit. Local evidence and signing inputs stay in ignored directories.

## Tasks

- [x] Add and run host regressions for a blocked package lookup, protected/public ApplicationInfo constructors, missing signatures and constructor failures; observe failure before implementation.
- [x] Implement the constructor helper, framework ApplicationInfo accessor and preview call sites. Update framework regressions to pass module information explicitly and add a nonexistent-package scenario without modifying global application context.
- [x] Update the adaptation playbook and image-preview documentation, distinguishing host checks, compiled framework tests and unperformed device validation.
- [x] Run offline testDebugUnitTest, assembleDebugAndroidTest and assembleRelease with requireReleaseSigning=true and the existing local signing environment. Check API 26 manifest floor, release debuggability, certificate and APK alignment.
- [ ] Review every staged file and diff for scope, baseline preservation, secrets, private paths, identifiers and author/committer noreply identity. Commit the repair and existing appearance changes to master. Do not push or install.
- [ ] Report commit, changed files, tests, signed APK and remaining runtime coverage; mark the thread goal complete. On a blocking failure, write the local night-queue report with completed work and next action.

## Review focus

Package lookup must never run while creating the module layout. Protected legacy constructors need exact reflective access without calling newer public APIs. A missing or throwing constructor must not retry with a package name. Module ApplicationInfo must reach both initial publication and preview reattachment. Existing appearance hooks and notification identity/reply actions must survive the combined change.
