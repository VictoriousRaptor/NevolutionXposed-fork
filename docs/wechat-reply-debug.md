# WeChat notification reply compatibility

The full procedure lives in
[微信版本适配手册](wechat-version-adaptation-playbook.md). Read that playbook
first; this file only keeps the rules that must never be broken.

## Redlines

- Any obfuscated class or method in a compatibility profile must be validated by
  its full parameter and return types at runtime. An unknown profile must never
  expose a reply action that cannot dispatch successfully.
- Do not commit device APKs, LSPosed logs, logcat captures, or decompiled
  sources. They belong under `.debug-artifacts/`, which is ignored by Git.
- Diagnostic logs must contain stage names, class and method signatures,
  notification IDs, result keys, and input lengths only. They must not contain
  message text, contact names, account IDs, or other notification contents.

## Reply stages

The reply path is diagnosed in this order:

1. `MainHook` loads in the `com.tencent.mm` main process.
2. The notification is decorated and retains a free-form `RemoteInput` action.
3. The module receiver obtains the result key and input length.
4. The native `PendingIntent` path or synthetic receiver path is selected.
5. WeChat's receiver and RemoteInput helper are invoked without an exception.
6. The message is visible in the conversation.

## Currently verified targets

- WeChat 8.0.72 (Google Play, versionCode 3085): verified, see
  `wechat-8.0.72-findings.md`.
- WeChat 8.0.78 (China build, versionCode 3180): verified and delivered, see
  `wechat-8.0.78-findings.md`.
- WeChat 8.0.76 legacy profile: mapping preserved but not live-tested.
