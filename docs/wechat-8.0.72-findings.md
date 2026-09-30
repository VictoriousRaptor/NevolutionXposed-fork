# WeChat 8.0.72 reply findings (2026-09-11)

## Device and app facts

| Item | Value |
| --- | --- |
| Device | OnePlus PJZ110, Android 16 (SDK 36), arm64-v8a, `PJZ110_16.0.10.501(CN01)` |
| WeChat | `versionName=8.0.72`, `versionCode=3085`, `minSdk=24`, `targetSdk=35` |
| Installer | `com.android.vending` (Google Play) |
| Module installed during diagnosis | `com.oasisfeng.nevo.xposed` 2.0.2 (versionCode 4), installer `com.android.chrome`, signing certificate SHA-256 `28041a0d…35808` |
| Root framework | SukiSU Ultra (`com.sukisu.ultra`) with LSposed; the LSPosed manager app is **not** installed |

Collected artifacts (all under `.debug-artifacts/`, never committed):
`device/20260910-234552/` holds `device.txt`, `wechat-package.txt`,
`wechat-apk-paths.txt`, `apks/` (6 APKs) and `apk-sha256.txt`.

| APK | SHA-256 |
| --- | --- |
| base.apk (194,770,301 B) | `59cfc54474ed23ff7276d1eea36ea779db7e6893d7b2cb925f28a0f3301784be` |
| split_config.arm64_v8a.apk | `d8b163761b6fb0ad47ac73e078cf918de5677ae1560eaa1cf40e0ee83f82e6c6` |
| split_config.xxxhdpi.apk | `b9c032120540bd0d5fb45a69a74e24f47301a622b1674c1fba10ac6ad1827110` |
| split_config.zh.apk | `af74ef81d7ffd37fe7bc1d7d73ef66df474e2b08be22e086ea49b20918101aa3` |
| split_delivery.apk | `30dfa865394787521be64347a47249f1b87ae22b4cb292feda372cec535531b1` |
| split_delivery.config.arm64_v8a.apk | `9212a004e7e03a4c60b5f8d449648e7ba4efbf63274871c74aea017c632070d2` |

Decompilation: JADX 1.5.6, `--no-res`, 156,965 classes, 712 method-level errors
(normal for a build this size). Sources live in
`device/20260910-234552/sources/` and are not committed.

## Verified 8.0.72 reply mapping

`com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver.onReceive`:

```java
String user = c2.l(intent, "key_username");              // key_username
Bundle results = z2.s1.b(intent);                        // RemoteInput.getResultsFromIntent
CharSequence text = results.getCharSequence("key_voice_reply_text");
if (a.f()) { if (!a.h()) { log "not open car mode"; return; }
             if (!a.c()) { log "not install auto app"; return; }
             ((lj5.s5) dg3.t1.a()).ci(user, text.toString(), e2.D(user), 0); }
```

| Descriptor | 8.0.72 |
| --- | --- |
| Receiver | `com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver` (`classes12.dex`) |
| Username extra | `key_username` |
| Reply result key | `key_voice_reply_text` |
| RemoteInput helper | `z2.s1.b(android.content.Intent) : android.os.Bundle` |
| Car-mode gates | `dn1.a.f()`, `dn1.a.h()`, `dn1.a.c()` — all `public static boolean` |
| Car actions | `com.tencent.mm.permission.MM_AUTO_REPLY_MESSAGE`, `…MM_AUTO_HEARD_MESSAGE` |
| Send call | `lj5.s5.ci(String, String, String, int)` via `dg3.t1.a()` |

`dn1.a` is the car/auto logic class: `f()` reads the `clicfg_android_auto` flag,
`h()` requires car UI mode plus a USB AOAP device, `c()` checks that the Google
Android Auto package is installed with a specific MD5. `dn1.a.b(String, String)`
is what builds the car notification and attaches the `key_voice_reply_text`
RemoteInput.

## Root cause of "text entered but nothing sent"

The synthetic path validates the receiver, then tries to bypass the car-mode
gates before delegating to the receiver. The bypass looked for the obfuscated
class `rn1.a` (the 8.0.76 name) and its "dynamic search" was a stub: it logged
`signature_missing` and returned `null`, after which the hardcoded fallback tried
`rn1.a` again. On 8.0.72 the class is `dn1.a`, so no gate was bypassed and
`h()` returned `false`; the receiver logged `not open car mode` and returned
without sending. The bypass list `{f, g, c}` also omitted `h`, which is the gate
that actually blocks on 8.0.72.

## Fix in this working tree

- New `WeChatReplyProfile` maps version → receiver, RemoteInput helper, gate class
  and gate methods, and validates each descriptor by full signature at runtime.
- 8.0.72 profile: gates `dn1.a` with `f`, `h`, `c`; helper `z2.s1`.
- Legacy profile (8.0.76 and older): gates `rn1.a` / `AutoLogic` with `f`, `g`, `c`.
- `MainHook.isSyntheticReplyAvailable()` now requires a validated profile, so a
  build whose gates cannot be validated no longer offers a reply action that
  cannot send.
- `hookCarModeBypass`, `hookAutoLogicMethods` and `invokeMMAutoReply` use the
  validated profile instead of guessing obfuscated names.

## Open items

- The module installed on the phone is release-signed (`28041a0d…`), while local
  builds use the Android debug key (`05a79f72…`), so replacing it requires
  uninstall + install rather than an update.
- The LSPosed manager app is missing on the device; `manager.apk` is available
  inside `Download/Zygisk - LSPosed-v1.11.0.zip` if it has to be reinstalled to
  re-enable the module.

## Reproduction log (installed release build, 2026-09-11 00:16)

The reply action did dispatch, and the car-mode bypass failed at the class-name
mismatch, exactly as predicted:

```text
00:16:02.824 2384 2384 D WeChatDecorator: Directly invoking MMAutoMessageReplyReceiver with reply: 00
00:16:02.825 2384 2384 D MainHook: Added RemoteInput results to intent
00:16:02.826 ... RemoteInput.getResultsFromIntent: Bundle[{key_voice_reply_text=00}]
00:16:02.826 ... hookAutoLogicMethods: hooking rn1.a
00:16:02.827 ... hookAutoLogicMethods: f hook failed: rn1.a#f[]#exact
00:16:02.827 ... hookAutoLogicMethods: g hook failed: rn1.a#g[]#exact
00:16:02.827 ... hookAutoLogicMethods: c hook failed: rn1.a#c[]#exact
00:16:02.829 ... hookCarModeBypass: RemoteInputHelper.b() hook added
00:16:02.830 ... MMAutoMessageReplyReceiver.onReceive: action=com.tencent.mm.permission.MM_AUTO_REPLY_MESSAGE
00:16:02.830 ... MMAutoMessageReplyReceiver.onReceive: reply_content=00
00:16:02.830 ... MMAutoMessageReplyReceiver.onReceive: completed
00:16:02.830 2384 2384 D MainHook: Directly invoked MMAutoMessageReplyReceiver.onReceive
```

Reading: the text reached the receiver with the correct result key, the receiver
ran to completion, but no car-mode gate was bypassed — `rn1.a` exists in 8.0.72
as some other class, so `f`/`g`/`c` did not match and `dn1.a.h()` still returned
`false`. Nothing was sent, which matches the reported symptom.

Raw dump: `.debug-artifacts/logs/dump-20260911-001613.log`.

## Second reproduction after a manual reinstall (2026-09-11 00:22)

The reinstalled APK is byte-identical to the previous one
(SHA-256 `878762b7215227ef306be3d8101e10b9ed55565c69535942206dce1f8d432a01`,
signing certificate `28041a0d…`), so it is still the release build installed from
`com.coloros.filemanager` and does not contain the profile fix. The log shows the
same failure:

```text
00:22:04.039 WeChatDecorator: Synthetic reply: <text> for id=-1149184867
00:22:04.039 WeChatDecorator: Directly invoking MMAutoMessageReplyReceiver with reply: <text>
00:22:04.040 RemoteInput.getResultsFromIntent: Bundle[{key_voice_reply_text=<text>}]
00:22:04.041 hookAutoLogicMethods: hooking rn1.a
00:22:04.041 hookAutoLogicMethods: f hook failed: rn1.a#f[]#exact
00:22:04.044 MMAutoMessageReplyReceiver.onReceive: reply_content=<text>
00:22:04.044 MMAutoMessageReplyReceiver.onReceive: completed
```

Useful side effect: the uninstall + reinstall cycle kept the module active in
WeChat, so swapping the APK again is safe.

The fixed build produced by this checkout is
`build/outputs/apk/debug/NevolutionXposed-debug.apk`
(SHA-256 `936825e7dd1b00f6e08f346c058bef0132aaa08bd897e98ee642ad69c8875f9e`,
debug certificate `05a79f72…`) and still has to be installed.

## Fixed build installed and validated (2026-09-11 00:24-00:27)

The fixed debug build was installed on the phone (`adb uninstall` +
`adb install`); the APK pulled back from `/data/app` hashes identically to the
local artifact, so the device really runs the patched code. After restarting
WeChat the module loads in the WeChat process and the profile validates:

```text
NX_REPLY stage=profile_ready version=8.0.72/3085 profile=wechat-8.0.72
  receiver=com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver
  helper=z2.s1 gates=dn1.a[f, h, c] usable=true
```

One unrelated warning appears at WeChat startup:
`WeChatDecorator: Failed to create module context: Application package
com.oasisfeng.nevo.xposed not found`, followed by a NullPointerException in
`NevoDecoratorService.getString()` from `WeChatDecorator$Local.onCreate`. It
concerns the decorator's own module context, not the reply dispatch, and the
decorator still reports `disabled false`. Watch it if the reply test regresses.

## Second blocker: the synthetic path never had `key_username`

With the gates bypassed, the reply still did not arrive, and the log shows why:

```text
NX_REPLY stage=car_mode_bypass_ready class=dn1.a methods=[f, h, c]
NX_REPLY stage=wechat_receiver_extras keys=[notification_id, reply_content]
NX_REPLY stage=wechat_receiver_complete throwable=false
```

`MMAutoMessageReplyReceiver.onReceive` starts with
`String user = c2.l(intent, "key_username"); if (user == null) return;`, and
`MessagingBuilder`'s synthetic intent only carries `reply_content` and
`notification_id` (see `MessagingBuilder` around line 936). The receiver
therefore returned on its first statement — no exception, no send, which is
exactly the "text entered but nothing sent" symptom. WeChat's own notification
extras do not contain the talker either (`dumpsys notification --noredact` shows
only title/text/messages), so the username cannot be recovered from the
notification.

The username is only available from the CarExtender reply `PendingIntent` that
WeChat builds in `dn1.a.b(String, String)` for the car conversation. The module
already reads it there (the native path), so the fix is to bypass the car-mode
gates **at process start** instead of lazily at reply time: WeChat then builds
the car conversation itself on the next incoming message, and the native reply
path handles the username, the result key and the dispatch.

Implemented in `MainHook.hookWeChat`: the `Application.onCreate` hook now calls
`hookCarModeBypass()` right after the profile resolves. Log confirms the early
install:

```text
NX_REPLY stage=profile_ready version=8.0.72/3085 profile=wechat-8.0.72
  receiver=…MMAutoMessageReplyReceiver helper=z2.s1 gates=dn1.a[f, h, c] usable=true
hookAutoLogicMethods: hooking dn1.a [f, h, c]
hookCarModeBypass: car mode bypass hooks added
```

Note for the update workflow: after `adb install -r` of the module, LSPosed kept
serving the previous APK path until WeChat was restarted a second time, several
seconds after the install. Restart WeChat once more if the module looks absent.

## Verified working (2026-09-11 00:56)

A reply typed into the notification ("测试00" style test text) was delivered. The
log shows the full native chain with the user name present:

```text
NX_REPLY stage=native_receiver notificationId=-1149184867 resultKey=key_voice_reply_text inputLength=4
NX_REPLY stage=native_remote_input_attached resultKey=key_voice_reply_text
NX_REPLY stage=wechat_receiver_enter remoteInputKeys=[key_voice_reply_text]
NX_REPLY stage=wechat_receiver_action action=com.tencent.mm.permission.MM_AUTO_REPLY_MESSAGE
NX_REPLY stage=wechat_receiver_extras keys=[key_username]
Bypassed a.f() -> true
Bypassed a.h() -> true
Bypassed a.c() -> true
NX_REPLY stage=wechat_receiver_complete throwable=false
NX_REPLY stage=native_pending_intent_callback notificationId=-1149184867 resultCode=0
```

The user confirmed the message arrived in the conversation, so both blockers
(wrong obfuscated gate class, missing `key_username`) are resolved.

## Recommended scope and release build (2026-09-11 01:00)

- `AndroidManifest.xml` now declares
  `<meta-data android:name="xposedscope" android:resource="@array/xposed_scope" />`
  with `src/main/res/values/arrays.xml` listing `com.android.systemui` and
  `com.tencent.mm`. Read back from the built APK with
  `aapt2 dump resources`: `array/xposed_scope = ["com.android.systemui", "com.tencent.mm"]`.
- Release artifact: `build/outputs/apk/release/NevolutionXposed-release.apk`,
  1,338,059 bytes, SHA-256
  `5f88c1c75cf72d3ea311b945db0e14359c821ccb5a844a1d6eb7b847efa16887`,
  signed with the local debug key (`05a79f72…`) so it can update the debug
  install in place. A real release key would require uninstalling first.
- Installed on the phone; the APK pulled back from `/data/app` hashes identically
  and WeChat reports `profile_ready … gates=dn1.a[f, h, c] usable=true` on the
  release build.
- Updated modules take effect only after WeChat is restarted a few times /
  tens of seconds later: LSPosed kept serving the previous APK path for a while
  after each `adb install -r`.

## Regression: notifications showed "[消息]" (2026-09-11 01:15)

Confirmed as a side effect of the fix above, not a pre-existing behaviour:

| Time | Installed build | Module-observed notification text |
| --- | --- | --- |
| 00:31:51 | before the always-on car-mode bypass | `[2条]微信ClawBot: 正常，我在。` (real content) |
| 00:56:16 | after the always-on car-mode bypass | `[消息]` (placeholder) |

`[消息]` is WeChat's own resource `string/a4p`; the only relevant producer is the
fallback in WeChat's car-conversation preview builder (`dn1.a`, the same class we
bypass). Because the gates now report "car mode on" for every notification,
WeChat builds a car conversation whose message previews fall back to that
placeholder, and `MessagingBuilder.buildFromExtender` rendered the notification
from the car conversation, replacing the real text.

Fix: `MessagingBuilder` now checks the car conversation content. When every
message is a placeholder it logs `NX_REPLY stage=car_content_placeholder` and
builds the notification body from the notification's own text, using the car
conversation only for the reply intent (which still carries `key_username`).
Release APK rebuilt (SHA-256
`ef1a5d17a4c441bbfe39464da523ec9ee0806bf5298f387bf277df7f2bfcce06`) and installed.

Confirmed by the user on 2026-09-11: a new incoming message shows its real text
again, and the notification reply is still delivered to the conversation.

## Final state

| Item | Value |
| --- | --- |
| WeChat | 8.0.72, versionCode 3085, Google Play |
| Profile in use | `wechat-8.0.72`: receiver `…MMAutoMessageReplyReceiver`, helper `z2.s1`, gates `dn1.a[f, h, c]` |
| Reply path | native (WeChat's own car reply `PendingIntent`, real `key_username`) |
| Notification body | WeChat's own text, with the car-conversation placeholder path as fallback |
| Recommended scope | `com.android.systemui`, `com.tencent.mm` |
| Installed APK | release, debug-key signed, SHA-256 `ef1a5d17…` |

Not covered by live testing: WeChat 8.0.76. Its mapping (gates `rn1.a`/`AutoLogic`
with `f`, `g`, `c`) is preserved in the profile but was not exercised on a
device, so 8.0.76 regression status is *pending*, not passed.

## 应用内回复轮次的离线映射（2026-09-27）

以下是新增轮次功能的**离线源码证据**，不属于上文已经实测送达的通知栏回复结果。源 APK 是本文开头记录的 Google Play 8.0.72/3085 `base.apk`（SHA-256 `59cfc54474ed23ff7276d1eea36ea779db7e6893d7b2cb925f28a0f3301784be`）；本地 JADX 位置为 `.debug-artifacts/device/20260910-234552/sources/sources/com/tencent/mm/storage/f9.java:893`。

| 依赖点 | 离线核对结果 |
| --- | --- |
| 状态更新入口 | `com.tencent.mm.storage.f9.q1(int): void`，先调用父类状态更新方法 |
| 发送成功分支 | `C0(): int == 1`、未命中 `b3()/N2()/F2(): boolean` 且 `M0(): int == 2` 时构造 `SendMsgSuccessEvent` |
| 发送失败分支 | 同一方向与排除条件下，`M0(): int == 5` 时构造 `SendMsgFailEvent` |
| 会话与匿名身份 | `O0(): String`、`getMsgId(): long`、`getCreateTime(): long` |

`WeChatAppReplyEvents` 仅在版本名为 8.0.72 **且**版本号为 3085、完整方法签名校验通过时安装该钩子；回调不读取回复正文。钩子安装、成功/失败事件时序与下一条来信后的轮次切换均**尚未在真实微信上验证**。详见[版本适配手册](wechat-version-adaptation-playbook.md)。

## 撤回 hook 与回复资格变更（2026-09-30）

源 APK 仍为上述 Google Play 8.0.72/3085，SHA-256 `59cfc54474ed23ff7276d1eea36ea779db7e6893d7b2cb925f28a0f3301784be`。离线位置均相对于 `.debug-artifacts/device/20260910-234552/sources/sources/`：

| 依赖/证据 | 运行时完整映射与核对位置 |
| --- | --- |
| 通知发布 BEFORE hook | `com.tencent.mm.booter.notification.NotificationItem.a(android.content.Context): void`；原始字段 `f: android.app.Notification`、`h: java.lang.String`、`i: long`；见同名类源码 |
| 服务端 ID 来源 | `com/tencent/mm/booter/notification/x.java` 将 `com.tencent.mm.storage.f9.I0(): long` 传入 `m0.a(...)`，`m0.java` 写入 `NotificationItem.i`；不能与本地 `getMsgId()` 混用 |
| 撤回事件 BEFORE hook | `com.tencent.mm.sdk.event.IEvent.e(): boolean`，仅处理直接继承 `IEvent` 的 `com.tencent.mm.autogen.events.RevokeMsgEvent` |
| 原始字段与 accessor | `RevokeMsgEvent.g: pm.ds`、`pm.ds.c: com.tencent.mm.storage.f9`、`f9.O0(): String`、`f9.I0(): long`；见事件、payload 和消息类源码 |
| 撤回生产者 | `b01/u.java` 填充 `RevokeMsgEvent.g.c` 后调用 `e()`；通知监听器使用同一撤回记录 |

版本名 **且** 版本号匹配并通过全部字段/方法/父类校验后才安装。钩子在微信主进程 `Application.onCreate` 后、图片预览开关判断前安装；模块禁用时不安装。JADX 别名不能代替运行时原名。`source=recall_ready` 仅证明安装；未知版本、签名失败、其他通知入口或异步事件路径不承诺精确裁剪。

此次回复资格统一为 ticker 含位置至少为 1 的英文冒号，不依赖原生回复对象存在。原生回复优先；合成回退还要求点击目标、已知会话 key 和可用接收器画像，并转发 `key_username`。非聊天通知不新增/代理模块回复。SystemUI 的轮次重置仅接受点击、手动清除、全部清除；微信本地 notify hook 遵守 `StopPost`，避免空撤回提示重发。

首次构建时的证据为离线源码核对、本地 106 项单元测试及签名 Debug/仪器 APK 构建；当时 33 项仪器用例只编译未执行，未安装或操作真机。本次撤回、合成回复与 SystemUI 清理的真实微信行为**尚未验收**，不能沿用本文历史原生回复的通过结果。无精确服务端 ID 的消息（含只有模块 UUID 的本人通知回复）保留，通知清除后不复活和图片文字回退仍待微信端设备验证。详见[适配手册](wechat-version-adaptation-playbook.md)、[撤回专题](wechat-recall-history.md)及[项目修改计划](project-modification-plan.md)。

同日后续验证：按用户授权在 PJZ110 / Android 16 安装同 release 证书的 Debug 与测试 APK，模块 framework 仪器结果为 **33/33 通过**，微信和 SystemUI 未重启。测试发现普通进程缺少 Xposed API 时合成资格检查会抛出 `NoClassDefFoundError`，现已保守关闭合成入口并保留原生路径；本测试进程使用 `--no-hidden-api-checks` 访问 framework 隐藏字段，不修改全局设置。版本映射和微信 hook 签名未改变。本结果取代上段“仪器仅编译”的执行状态，仍不代表 8.0.72 真实微信 hook/收发/撤回已验收，详见[项目执行记录](project-modification-plan.md)。
