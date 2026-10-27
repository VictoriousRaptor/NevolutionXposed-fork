# WeChat notification reply compatibility

## 2026-10-28 同版本名新代码实验性回复排查

精确已知组合优先；未知代码仅枚举同 `versionName` 的已核对通知回复画像，完整签名唯一命中才标 `experimental`。8.0.77 的 3141/3160 候选若都通过则记录 `ambiguous` 并不选实验性画像；8.0.72/8.0.78 的名称**或**代码历史回退及其他 legacy 回退仍在，原生 `PendingIntent` 回复不受合成门禁失败影响。须同时验证接收器 `onReceive(Context, Intent): void`、静态 RemoteInput helper `b(Intent): Bundle`、画像指定的三道无参数静态 `boolean` 门禁；门禁批量 hook 任一失败会撤销已有句柄，合成入口保持关闭。`NX_REPLY stage=profile_ready` 只表示解析通过；还需检查 `car_mode_bypass_ready` 与真实消息投递，不能把前者视为后者。

8.0.79/3183 没有 APK，无法记录该构建来源、SHA-256、反编译位置或实际原始混淆名；只有 3182 候选的原证据。此次主机测试与编译不证明 3183 运行时 hook、`key_username` 投递或设备通知行为。缓存读键包括功能、版本名、长版本码、更新时间、修订版；hook 进程远程偏好只读，模块侧正缓存写入 IPC 尚待接入和跨启动验证，不能因进程内 `experimental` 日志就称为持久匹配。

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
- WeChat 8.0.79/3182: exact name AND code mapping, gates `lt1.a[c,d,b]`;
  independent image, app-reply and recall mappings and validation boundaries are in
  `wechat-8.0.79-findings.md`. Runtime readiness does not prove message delivery.
- WeChat 8.0.78 (China build, versionCode 3180): verified and delivered, see
  `wechat-8.0.78-findings.md`.
- WeChat 8.0.76 legacy profile: mapping preserved but not live-tested.
- WeChat 8.0.77: exact 3141 and 3160 profiles have distinct reply gates, image,
  app-send and recall descriptors. The installed 3141 APK passed 40 descriptor
  checks and all five hook paths reported ready. Actual message behavior and
  3160 device acceptance are pending; see `wechat-8.0.77-findings.md`.

## Reply button disappears after notifications accumulate

If ordinary notifications still appear but inline replies stop until WeChat restarts,
search LSPosed logs for `before hook failed`, `LruCache`, and
`sizeOf() is reporting inconsistent results!`. On 2026-09-22 this exception occurred
in `LocalDecorator.cache()` before reply actions were added. Mutating a cached list
in place had undercounted its size; removing history then corrupted the total.

`LocalDecorator` uses `NotificationArchive`: it counts notifications explicitly,
retains at most 120 notifications and 20 per conversation, and publishes stable list
snapshots. Token matching and history removal run atomically. Regression coverage
includes repeated reply recasts/removal, LRU eviction, concurrent updates, and an
Android instrumentation case preserving a RemoteInput action through rebuild and
parceling. Building the instrumentation APK does not mean the device test was run.

The unwritten SystemUI archive was removed on 2026-10-20. The listener weak
reference, `setNLS()`, `getNLS()` and listener-backed `cancelNotification(String)`
remain available for debugger or reflective use in the SystemUI process with the
module's class loader. Every listener constructor callback refreshes the weak
reference; `getNLS()` may return null, in which case cancellation does nothing.
These entry points have no in-repository read/cancel callers, but that alone is
not evidence that removing them preserves manual debugging capabilities.
The active local archive, SystemUI posted/removed hooks and WeChat round-reset
broadcast remain in place. Source review and builds do not verify live listener
availability or notification cancellation on a device.
