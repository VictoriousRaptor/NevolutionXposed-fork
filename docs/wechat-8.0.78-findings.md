# WeChat 8.0.78 reply findings (2026-09-13)

## Device and app facts

| Item | Value |
| --- | --- |
| Device | OnePlus 7 Pro (GM1910), Android 16, arm64-v8a, `BP4A.251205.006 release-keys` |
| WeChat | `versionName=8.0.78`, `versionCode=3180`, `minSdk=24`, `targetSdk=34` |
| Installer | `com.android.packageinstaller` (China build, side-loaded 2026-09-12) |
| Base APK | 280,302,239 B, SHA-256 `ff507d8ca93d735342a5e29d374beadce166e1c3cba8302f655f0c194331fb3b` |
| Root framework | ReSukiSU + Zygisk LSPosed v2.2.0 (7854); no LSPosed manager app installed |
| Module before the fix | 3.1.0 (versionCode 8), release-signed `7a707a90…` |

Decompilation: JADX 1.5.6, `classes11.dex`. Device artifacts live under
`.debug-artifacts/device/20260913-wechat-8078-3180/` and are never committed.

## Symptom

WeChat 8.0.78 notifications had no reply action. The module refused to expose
one because its version profile could not be validated:

```text
NX_REPLY stage=profile_rejected version=8.0.78/3180 profile=wechat-legacy
  receiver=com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver
  helper=z2.s1 gates=none usable=false
NX_REPLY stage=car_mode_bypass_skipped reason=profile_unusable
```

## Verified 8.0.78 reply mapping

`com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver.onReceive`:

```java
String user = f2.l(intent, "key_username");            // key_username
Bundle results = z2.s1.b(intent);                       // RemoteInput.getResultsFromIntent
CharSequence text = results.getCharSequence("key_voice_reply_text");
if (bs1.a.c()) {                                        // auto-mode config flag
    if (!bs1.a.g()) { log "not open car mode"; }
    else {
        if (!bs1.a.b()) { log "not install auto app"; return; }
        u1.a().mj(user, text.toString(), d2.C(user), 0);   // send
    }
}
```

| Descriptor | 8.0.78 |
| --- | --- |
| Receiver | `com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver` (`classes11.dex`) |
| Username extra | `key_username` |
| Reply result key | `key_voice_reply_text` |
| RemoteInput helper | `z2.s1.b(android.content.Intent) : android.os.Bundle` |
| Car-mode gates | `bs1.a.c()` config flag, `bs1.a.g()` car UI mode + AOAP USB, `bs1.a.b()` Android Auto package MD5 — all `public static boolean` |
| Car notification builder | `bs1.a.a(String, String)` attaches the `key_voice_reply_text` RemoteInput and the `MM_AUTO_REPLY_MESSAGE` / `MM_AUTO_HEARD_MESSAGE` PendingIntents |
| Send call | `rn3.u1.a().mj(String, String, String, int)` |

## Root cause

`WeChatReplyProfile.forPackage()` knew only two layouts: 8.0.72
(`dn1.a` with `f`/`h`/`c`) and the legacy 8.0.76 layout (`rn1.a` or
`com.tencent.mm.booter.auto.AutoLogic` with `f`/`g`/`c`). 8.0.78 fell into the
legacy profile, whose gate class no longer carries those methods, so validation
returned `gates=none`. Per design, an unvalidated profile must not offer a reply
action that cannot dispatch, so the reply button was hidden and the car-mode
bypass was skipped. 8.0.78 moved the gates to `bs1.a` with `c`, `g`, `b`.

## Fix in this working tree

`WeChatReplyProfile` gained a verified `wechat-8.0.78` profile (gates `bs1.a`
`[c, g, b]`, helper `z2.s1`, unchanged receiver) and `forPackage()` maps
`8.0.78` / versionCode `3180` to it. The receiver, helper and gate signatures
are still validated at runtime, so an unvalidated build keeps withholding the
action instead of dispatching into a dead receiver.

## Verification (2026-09-13 23:34)

```text
NX_REPLY stage=profile_ready version=8.0.78/3180 profile=wechat-8.0.78
  receiver=com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver
  helper=z2.s1 gates=bs1.a[c, g, b] usable=true
```

- No `hookAutoLogicMethods` / `hookCarModeBypass` failure lines after the restart.
- The APK pulled back from `/data/app` hashes identically to the local build
  (SHA-256 `179f4cf8ba4e0ead0ff1beb1bc5f58f93de3a675714aaff59e46a789e3c42410`).
- The module was updated in place with the release key (`adb install -r`), so
  the LSPosed activation and scope were preserved.
- `testDebugUnitTest` passes.

The first notification posted after the update carried the reply action and the
car conversation, and a reply typed into the shade ran the whole native chain:

```text
actions={ [0] "回复" -> PendingIntent{… com.tencent.mm broadcastIntent …} }
extras: android.car.EXTENSIONS=Bundle
NX_REPLY stage=native_receiver notificationId=-1149184867 resultKey=key_voice_reply_text inputLength=2
NX_REPLY stage=native_remote_input_attached resultKey=key_voice_reply_text inputLength=2
NX_REPLY stage=helper_result keys=[key_voice_reply_text]
NX_REPLY stage=native_pending_intent_callback notificationId=-1149184867 resultCode=0
```

## Open items

- Delivery is confirmed: the tester verified that the text sent from the
  notification shade arrived in the conversation, and the module side completed
  the whole native chain with the pending-intent callback returning 0.
- `WeChatImageEvents` still gates on 8.0.72/3085, so the image-preview feature
  stays off on 8.0.78. Unrelated to notification replies.

## Next version

The repeatable, one-pass procedure for the next WeChat release (device
collection, profile probe, install, verification and recording) lives in
`docs/wechat-version-adaptation-playbook.md`.

## 应用内回复轮次的离线映射（2026-09-27）

以下是新增轮次功能的**离线源码证据**，不属于上文已经实测送达的通知栏回复结果。源 APK 是本文开头记录的中国版 8.0.78/3180 `base.apk`（SHA-256 `ff507d8ca93d735342a5e29d374beadce166e1c3cba8302f655f0c194331fb3b`）；本地 JADX 摘录位于 `.debug-artifacts/device/20260913-wechat-8078-3180/e9.java:936`。

| 依赖点 | 离线核对结果 |
| --- | --- |
| 状态更新入口 | `com.tencent.mm.storage.e9.t1(int): void`，先调用父类状态更新方法 |
| 发送成功分支 | `z0(): int == 1`、未命中 `Z2()/L2()/E2(): boolean` 且 `M0(): int == 2` 时构造 `SendMsgSuccessEvent` |
| 发送失败分支 | 同一方向与排除条件下，`M0(): int == 5` 时构造 `SendMsgFailEvent` |
| 会话与匿名身份 | `N0(): String`、`getMsgId(): long`、`getCreateTime(): long` |

`WeChatAppReplyEvents` 仅在版本名为 8.0.78 **且**版本号为 3180、完整方法签名校验通过时安装该钩子；回调不读取回复正文。钩子安装、成功/失败事件时序与下一条来信后的轮次切换均**尚未在真实微信上验证**。详见[版本适配手册](wechat-version-adaptation-playbook.md)。

## 撤回 hook 与回复资格变更（2026-09-30）

源 APK 仍为上述中国版 8.0.78/3180，SHA-256 `ff507d8ca93d735342a5e29d374beadce166e1c3cba8302f655f0c194331fb3b`。以下位置相对于 `.debug-artifacts/`，记录运行时原名，不能采用 JADX 生成别名：

| 依赖/证据 | 运行时完整映射与核对位置 |
| --- | --- |
| 通知发布 BEFORE hook | `com.tencent.mm.booter.notification.NotificationItem.a(android.content.Context): void`；原始字段 `f: android.app.Notification`、`h: java.lang.String`、`i: long`；`recall-8078/sources/com/tencent/mm/booter/notification/NotificationItem.java` |
| 服务端 ID 来源 | `recall-map-8078/sources/com/tencent/mm/booter/notification/x.java` 将 `com.tencent.mm.storage.e9.F0(): long` 传入 `m0.a(...)`，同目录 `m0.java` 写入 `NotificationItem.i`；不能与本地 `getMsgId()` 混用 |
| 撤回事件 BEFORE hook | `com.tencent.mm.sdk.event.IEvent.e(): boolean`，仅处理直接继承 `IEvent` 的 `com.tencent.mm.autogen.events.RevokeMsgEvent`；见 `recall-map-8078/sources/` 下对应类 |
| 原始字段与 accessor | `RevokeMsgEvent.g: fm.ks`、`fm.ks.c: com.tencent.mm.storage.e9`、`e9.N0(): String`、`e9.F0(): long`；payload 见 `recall-data-8078/sources/fm/ks.java`，消息类另见已有 `device/20260913-wechat-8078-3180/e9.java` 摘录 |

版本名 **且** 版本号匹配并通过全部字段/方法/父类校验后才安装。钩子在微信主进程 `Application.onCreate` 后、图片预览开关判断前安装；模块禁用时不安装，8.0.78 图片事件不支持也不影响撤回门禁。`source=recall_ready` 仅证明安装；未知版本、签名失败、其他通知入口或异步事件路径不承诺精确裁剪。

此次回复资格统一为 ticker 含位置至少为 1 的英文冒号，不依赖原生回复对象存在。原生回复优先；合成回退还要求点击目标、已知会话 key 和可用接收器画像，并转发 `key_username`。这修正了“模块合成路径无法提供用户名”的旧结论，不代表新合成路径已实测。SystemUI 的轮次重置仅接受点击、手动清除、全部清除；微信本地 notify hook 遵守 `StopPost`。

首次构建时的证据为离线源码核对、本地 106 项单元测试及签名 Debug/仪器 APK 构建；当时 33 项仪器用例只编译未执行，未安装或操作真机。本次撤回、合成回复与 SystemUI 清理的真实微信行为**尚未验收**，不能沿用本文历史原生回复的通过结果。无精确服务端 ID 的消息（含只有模块 UUID 的本人通知回复）保留，通知清除后不复活和图片文字回退仍待微信端设备验证。详见[适配手册](wechat-version-adaptation-playbook.md)、[撤回专题](wechat-recall-history.md)及[项目修改计划](project-modification-plan.md)。

同日后续验证：按用户授权在 PJZ110 / Android 16 安装同 release 证书的 Debug 与测试 APK，模块 framework 仪器结果为 **33/33 通过**，微信和 SystemUI 未重启。测试发现普通进程缺少 Xposed API 时合成资格检查会抛出 `NoClassDefFoundError`，现已保守关闭合成入口并保留原生路径；本测试进程使用 `--no-hidden-api-checks` 访问 framework 隐藏字段，不修改全局设置。版本映射和微信 hook 签名未改变。本结果取代上段“仪器仅编译”的执行状态，仍不代表 8.0.78 真实微信 hook/收发/撤回已验收，详见[项目执行记录](project-modification-plan.md)。
