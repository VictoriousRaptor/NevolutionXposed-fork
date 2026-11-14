# WeChat notification reply compatibility

## 2026-11-13 当前诊断链路

当前通知栏发送链路为：有效请求 → 捕获目标 → 本地轮次结束 → 独立原生已读/派发 → 结果诊断。结果不保存本人消息或修改轮次。已删除发送状态、应用内正文、来源预约和待回应组；下文 app_reply/wave 补偿描述保留作历史调查记录。

独立已读能力使用 read_hook_ready/unavailable；成功目标捕获与提交分别记录 read_event_captured/committed，原方法失败记录 ignored，观察器异常记录 error。仅精确 8.0.79/3182、3183。上述事件仅使用 Debug logcat 记录结构字段；ready 不代表实际未读数或多端同步通过。分析旧语音是否复活时，同时检查可信身份首次代次、结束边界和新来信；现有日志不能直接证明通知发布者。

### 后续发送按钮复位

`onReplyFinished` 在派发处理的 finally 中运行，独立于网络结果；仅静默更新匹配的仍活动卡片，刷新异常通过 logcat 记录 `reply_ui_failed`。派发成功时追加提交文本为当前卡片的本人行，派发失败不追加；本地回显不表示网络送达。该路径不恢复跨轮本人历史，不改变结束边界；已移除或被新代次替换时跳过。排查“发送成功但转圈”时，区分本地轮次结束、实际派发以及 UI 更新，不将微信已发送等同于 SystemUI 已收到更新。

活动卡片可能滞后于模块已观察的发布/取消，完成确认另要求本地已接受的 tag/代次匹配且未取消。C 已通过 Local 或 cancel/cancelAll 已进入 BEFORE 时，旧 A 的完成被拒绝，即使系统活动列表暂未变化；此发布失效标记独立于未读轮次，不记录消息内容。

## 2026-11-07 Device wave follow-up

The generation-order merge now treats only active `app:` replies as leading
in-app context. A notification reply acknowledged by a late callback keeps its
notification generation among the peers; an undated C followed by that reply B2
and then D stays C/B2/D. Host regressions cover this distinction and capacity
trimming; Android controller regressions also check zero/rollback/equal-time D
and Parcel rebuilds. The updated Android framework suite subsequently passed
104/104 cases with no skips, including the original failing controller case;
46 installed-WeChat descriptor checks also passed. This is synthetic framework
and signature evidence, not real-message delivery or unread-state acceptance.

## 2026-11-03 Unified conversation history and click-removal read requests

Text and media placeholders now take the same conversation-building path. Reply selection keeps the CarExtender → original free-form Action → verified synthetic priority, but no reply target is required to render history. Replacing a reply preserves non-reply actions and their order. Body translation preserves media markers and notification-provided file/link text. Message serialization belongs to `NotificationMessages`; the independent `ReplyTarget` preserves the existing generation, conversation instance, pending reply IDs and native read target without changing wire fields or dispatch timing.

The existing SystemUI removal callback requests native read only for a WeChat non-summary notification with a nonzero module token and `REASON_CLICK`. It reads the removed notification's raw `car_conversation.on_read`, validates the PendingIntent creator package and UID, and never substitutes an arbitrary delete action or looks up the latest notification by ID. Missing/cancelled/failed read actions do not prevent the independent round-reset broadcast. Swipe and Clear All still rely on Android sending `deleteIntent`; replies still request read before dispatch. This does not add a private click hook or cover clicks that do not remove the notification. A sent action is not proof of WeChat's actual unread count or read watermark.

Synthetic framework tests cover these contracts without contacting WeChat. This round passed 296 host tests and compiled the Debug and androidTest APKs, both verified against the existing release certificate and checked with zipalign. `lintDebug` passed with 0 errors and 39 warnings in an isolated copy with 148 identical source/build input hashes; the original workspace test-source analysis was cancelled while traversing files. The 100-case framework runner was compiled only. Actual unread counts, notification appearance and reply input retention require separate device validation; prior runtime evidence does not validate the changed paths.

## 2026-10-28 同版本名新代码实验性回复排查

精确已知组合优先；未知代码仅枚举同 `versionName` 的已核对通知回复画像，完整签名唯一命中才标 `experimental`。8.0.77 的 3141/3160 候选若都通过则记录 `ambiguous` 并不选实验性画像；8.0.72/8.0.78 的名称**或**代码历史回退及其他 legacy 回退仍在，原生 `PendingIntent` 回复不受合成门禁失败影响。须同时验证接收器 `onReceive(Context, Intent): void`、静态 RemoteInput helper `b(Intent): Bundle`、画像指定的三道无参数静态 `boolean` 门禁；门禁批量 hook 任一失败会撤销已有句柄，合成入口保持关闭。`NX_REPLY stage=profile_ready` 只表示解析通过；还需检查 `car_mode_bypass_ready` 与真实消息投递，不能把前者视为后者。

8.0.79/3183 没有 APK，无法记录该构建来源、SHA-256、反编译位置或实际原始混淆名；只有 3182 候选的原证据。此次主机测试与编译不证明 3183 运行时 hook、`key_username` 投递或设备通知行为。缓存读键包括功能、版本名、长版本码、更新时间、修订版；hook 进程远程偏好只读，正缓存已改经模块侧受 UID 校验的 provider 同步写入，真实跨启动复用仍待设备验证，不能因进程内 `experimental` 日志就称为持久匹配。

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

### 2026-10-28 合成回复接收器完整预检与安装门禁

微信主进程的通知栏合成回复（已知画像、legacy 回退及同版本名未知代码的实验性候选）现在统一要求接收器继承 `android.content.BroadcastReceiver`、类为 public 且非 abstract，并具有 public 无参数构造函数；入口仍为 public、非 static、非 abstract 的 `onReceive(android.content.Context, android.content.Intent): void`。构造检查仅用反射读取描述符，不创建接收器或派发消息，对齐实际 `getDeclaredConstructor().newInstance()` 不调用 `setAccessible` 的派发约束。RemoteInput helper 和三道 static boolean 无参车载门禁的原有完整签名校验不变。

`Application.onCreate(): void` AFTER 的初始化只有在该画像接收器 hook 成功且三道门禁全部安装成功后，`MainHook.isSyntheticReplyAvailable()` 才返回 true；接收器 hook 失败保留 `hookAutoReplyReceiver failed` 诊断并关闭合成动作，不能仅凭 `profile_ready` 或门禁 ready 判为可用。原生 `PendingIntent` 回复独立于上述条件，现有聊天、会话 key、contentIntent 和功能开关条件继续生效；应用内回复、图片和撤回也各自独立。版本名与代码的既有精确/OR/legacy 规则以及同名未知代码唯一匹配规则不变。

主机回归覆盖 receiver hook 未成功而门禁已 ready、成功状态恢复，以及 abstract、无无参构造、private 无参构造和非 public 接收器的拒绝；覆盖构造与安装门禁不代表消息已送达。3183 仍无 APK 来源、SHA-256 或反编译证据，本轮未安装设备、未验证运行时 hook、真实回复或 OEM 展示。
