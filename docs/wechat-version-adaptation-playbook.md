# 微信版本适配手册

本文档是一次性完成"微信新版本适配"的运行手册：从采集证据、定位混淆类/方法、生成版本画像，到装包、验证与记录。适用于本仓库的维护者与代行实施的 agent。

配合脚本：`tools/collect-device-info.ps1`（只读取设备与 APK，不在手机上做任何修改）。

## 1. 适用场景与决策原则

触发条件（满足任一即开始适配）：

| 现象 | 说明 |
| --- | --- |
| 微信版本号变化 | `versionName` 或 `versionCode` 任一变化 |
| 通知栏没有回复按钮 | 画像校验失败时模块会主动隐藏回复入口 |
| 输入回复后没有发送 | 门禁未旁路或 `key_username` 缺失 |
| 通知正文退化成 `[消息]` | 车机会话占位文本路径 |
| 图片通知不显示预览 | `WeChatImageEvents` 的版本门禁不匹配 |
| 在微信内回复后通知历史仍继续增长 | `WeChatAppReplyEvents` 的版本门禁或发送状态钩子不匹配，轮次边界未记录 |
| 撤回后目标消息或撤回提示仍残留 | `WeChatRecallEvents` 门禁、事件入口或精确服务端 ID 关联不匹配 |
| 通话通知类型/文案异常 | 通知文本解析规则变化 |

四条铁律：

1. **未知版本不得暴露无法投递的回复入口**：画像必须通过完整签名校验后才提供回复动作，宁可暂时不显示，也不能出现"能输入、发不出去"的假按钮。
2. **禁止凭短方法名猜混淆类名**：所有混淆类/方法都要在运行时按完整签名校验，并在文档中记录证据（APK 哈希 + 反编译位置）。
3. **先证据、后改动**：先只读采集（设备信息、APK、反编译、日志），确认布局后再改代码；结论必须能落到日志、哈希或反编译行。
4. **变更与文档同步**：hook、版本限定、功能开关或回退行为的变更必须同时更新本指南及相关版本 findings；记录完整签名、启用条件、验证层级和未验证项。该原则已写入项目 [AGENTS.md](../AGENTS.md)，适用于微信和 Android/SystemUI 链路。

## 2. 历史提交的版本适配记录

| 提交 | 版本适配相关内容 |
| --- | --- |
| `7bc719f` 初始版本 | 8.0.76 通知回复：synthetic reply + `MMAutoMessageReplyReceiver` hook，接收器类名硬编码，无版本画像、无门禁旁路 |
| `113e406` / `04bdbbf` | 8.0.76 调试 APK 与使用说明，建立"设备实测后才交付"的习惯 |
| `b31da8f` / `4b1e77c` / `4baa311`（v2.0.0–v2.0.2） | 通话通知修复、消息发送与通知显示修复：`[语音通话]`/`[视频通话]` 文案识别、通话通知 id=41 校正、消息文本格式解析 |
| `5f9ab0b` | 引入 `WeChatReplyProfile` 版本画像（8.0.72 `dn1.a[f,h,c]` + legacy `rn1.a`/`AutoLogic`）、运行时签名校验、进程启动即装门禁旁路、`key_username` 必备、车机占位文本回退、`tools/collect-device-info.ps1` 与三份文档 |
| `0b0abd2` / `1f15727` / `f26ee46` | 图片通知事件：`WeChatImageEvents` 硬门禁 8.0.72/3085，依赖 `v65.z`、`yh3.f`、`com.tencent.mm.storage.f9`、`b80.m`、`m90.b`、`com.tencent.mm.vfs.w6` 等混淆类/方法；事件索引与开关 |
| `ba97b34` | 媒体/文件通知保留内联回复：从 CarExtender 会话补回复动作 |
| `a62a67d` | 3.1.0 发布版本号与 CI 说明 |
| 8.0.78 适配（本次） | 中国版 8.0.78/3180：门禁类迁移到 `bs1.a[c,g,b]`，新增 `wechat-8.0.78` 画像；通知栏回复已实测送达 |
| 2026-09-27 应用内回复轮次 | 新增独立的 `WeChatAppReplyEvents` 精确版本映射：8.0.72/3085 的 `storage.f9.q1(int)` 与 8.0.78/3180 的 `storage.e9.t1(int)`；离线源码已核对，真实微信运行时行为待验证 |
| 2026-09-30 项目计划 1、2、3 | 统一聊天通知回复资格；合成回复携带已知会话 `key_username`；新增两版撤回精确 ID hook；标准 SystemUI 移除原因和 RemoteInput 清理，微信本地 notify hook 接受 `StopPost`。本地构建及单元测试、真机模块仪器测试通过；真实微信行为待验证 |

## 3. 版本敏感点总表

### 3.1 回复链路（每个版本都可能变）

| 描述符 | 8.0.76（legacy） | 8.0.72 | 8.0.78 |
| --- | --- | --- | --- |
| 接收器类 | `com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver` | 同左 | 同左 |
| RemoteInput 辅助类 | `com.tencent.mm.sdk.platformtools.RemoteInputHelper` / `z2.s1` | `z2.s1.b(Intent): Bundle` | `z2.s1.b(Intent): Bundle` |
| 门禁类 | `rn1.a` / `com.tencent.mm.booter.auto.AutoLogic` | `dn1.a` | `bs1.a` |
| 门禁方法 | `f`（自动模式开关）、`g`（车机模式）、`c`（Android Auto 包） | `f`、`h`、`c` | `c`、`g`、`b` |
| 用户名 extra | `key_username` | 同左 | 同左 |
| 回复结果键 | `key_voice_reply_text` | 同左 | 同左 |

补充事实（8.0.78 源码与既有实测）：接收器 `onReceive` 先对 `key_username` 判空，微信自己的车机回复 `PendingIntent` 携带该字段，其原生路径已实测送达。

2026-09-30 的实现优先使用 CarExtender 的原生回复 `PendingIntent`，其次使用通知 actions；没有原生动作时，仅在聊天通知、非空已知会话 key、存在 `contentIntent` 且 `MainHook.isSyntheticReplyAvailable()` 校验通过时提供合成回复。合成代理及转发接收器都携带 `key_username`，会话来自已解析的原生会话或第 3.5 节通知身份 hook，不能从标题、ticker 或通知 id 猜用户名。本次合成回退尚未真机验证。

普通模块/仪器测试进程没有 compile-only 的 Xposed API，不能直接加载继承 `XposedModule` 的 `MainHook`。`MessagingBuilder.isSyntheticReplyAvailable()` 对 `NoClassDefFoundError` 返回 false，并记录 `action_synthetic_skipped reason=xposed_api_unavailable`；保持消息重建及原生回复，不添加未经验证的合成入口。该回退不修改微信版本映射或签名门禁，也不向 APK 打包 Xposed API。

各功能的版本匹配不能混用：

| 功能 | 当前匹配语义 | 未匹配时行为 |
| --- | --- | --- |
| 通知栏回复 `WeChatReplyProfile.forPackage()` | 8.0.72 或 8.0.78 的版本名、对应版本号任一命中即选候选；再运行时校验接收器和门禁签名 | 选 legacy 候选并校验；不可用时不开放合成投递 |
| 图片事件 | 8.0.72 **且** 3085；完整签名校验 | 不启用图片事件 |
| 应用内回复事件 | 8.0.72 **且** 3085，或 8.0.78 **且** 3180；完整签名校验 | 保留原轮次行为 |
| 撤回身份和事件 | 同上两组精确版本；完整字段/方法及事件父类校验 | 不启用精确撤回关联 |

### 3.2 图片事件（版本绑定最深，当前仅 8.0.72/3085）

| 依赖点 | 8.0.72 | 失效表现 |
| --- | --- | --- |
| 版本门禁 | `versionName=8.0.72` 且 `versionCode=3085` | 其他版本直接 `events_unavailable` |
| 状态类 / 简单消息类 | `v65.z`、`yh3.f` | 类找不到或字段类型不符 |
| 消息类 | `com.tencent.mm.storage.f9`（`O0`/`getMsgId`/`getCreateTime`/`getType`/`C0`） | 事件索引拿不到消息 |
| 查询 / 路径类 | `b80.m.oi(...)`、`m90.b`、`com.tencent.mm.vfs.w6.i(...)` | 缩略图路径解析失败 |

### 3.3 应用内回复轮次发送事件（独立于通知栏回复画像和图片预览）

| 依赖点 | 8.0.72 / 3085（Google Play） | 8.0.78 / 3180（中国版） |
| --- | --- | --- |
| 本人消息类 | `com.tencent.mm.storage.f9` | `com.tencent.mm.storage.e9` |
| 状态更新入口 | `q1(int): void` | `t1(int): void` |
| 发送方向 | `C0(): int == 1` | `z0(): int == 1` |
| 发送状态 | `M0(): int == 2` | `M0(): int == 2` |
| 会话标识 | `O0(): String` | `N0(): String` |
| 排除条件 | `b3()`、`N2()`、`F2()`：均为 `boolean` | `Z2()`、`L2()`、`E2()`：均为 `boolean` |
| 匿名身份与时间 | `getMsgId(): long`、`getCreateTime(): long` | 同左 |
| 离线证据 | [8.0.72 findings](wechat-8.0.72-findings.md) | [8.0.78 findings](wechat-8.0.78-findings.md) |

`WeChatAppReplyEvents.Profile.forVersion()` 要求版本名与版本号**同时**匹配；安装前逐个校验完整方法签名。只有状态更新参数为 2、更新后本人消息状态仍为 2、未命中排除条件且消息 ID 与时间有效时，才把会话标识、消息 ID 和时间交给轮次逻辑。它不读取回复正文；模块通知栏回复通过 `NotificationReplyOrigins` 排除。未知版本或签名不匹配时不安装钩子，原有通知行为保留。此门禁与 `WeChatReplyProfile` 的通知栏回复门禁不同，也不随图片预览开关启停。

上述两版仅完成离线源码核对与构建测试，尚未在真实微信中验证钩子触发时序、失败发送、消息时间与通知时间对应关系，以及应用内发送和通知回复并发的归属。不能把既有的“通知栏回复已实测送达”视为轮次功能已实测。

### 3.4 通知解析与通话识别

| 依赖点 | 现状 | 失效表现 |
| --- | --- | --- |
| 通知文本前缀 | `微信xx: 文本`、`[N条]` 计数 | 正文解析错位、计数丢失 |
| 媒体标记 | `[表情]`/`[视频]`/`[文件]`/`[链接]`/`[音乐]`/`[位置]`/`[红包]`/`[转账]`/`[小程序]` | 被误当成纯文本处理 |
| 通话文案 | `[语音通话]`、`[视频通话]`、`语音通话中` | 通话通知类型或计时显示异常 |
| 通话校正来源 | 通话通知 id=41 | 视频通话被标成语音通话 |
| 车机会话 | `android.car.EXTENSIONS`、占位文本 `[消息]` | 通知正文退化为占位符 |
| 模块回复资格 | `WeChatMessage.isChat()`：ticker 非空且英文冒号 `:` 位置至少为 1；不要求原生回复对象存在 | 好友申请、杂项或不符合格式的通知不新增/代理模块回复，保留原消息重建和原动作 |
| 头像路径 | `/data/data/com.tencent.mm/MicroMsg/{hash}/avatar/...` | 通知头像缺失 |

### 3.5 撤回身份与事件（独立精确版本门禁）

`WeChatRecallEvents` 在微信主进程的 `Application.onCreate` 后由 `WeChatDecorator.Local.hook()` 安装，前提是模块未禁用；与应用内回复事件一起位于图片预览开关判断之前，关闭预览不会关闭这两个功能。

| 依赖点 | 8.0.72 / 3085（Google Play） | 8.0.78 / 3180（中国版） |
| --- | --- | --- |
| 通知发布 BEFORE hook | `com.tencent.mm.booter.notification.NotificationItem.a(android.content.Context): void` | 同左 |
| 通知原始字段与类型 | `f: android.app.Notification`、`h: java.lang.String`、`i: long` | 同左 |
| 撤回事件 BEFORE hook | `com.tencent.mm.sdk.event.IEvent.e(): boolean`；只处理 `com.tencent.mm.autogen.events.RevokeMsgEvent` 实例，校验其直接父类为 `IEvent` | 同左 |
| 原始 payload 字段 | `RevokeMsgEvent.g: pm.ds`，`pm.ds.c: com.tencent.mm.storage.f9` | `RevokeMsgEvent.g: fm.ks`，`fm.ks.c: com.tencent.mm.storage.e9` |
| 会话与服务端 ID | `storage.f9.O0(): String`、`storage.f9.I0(): long` | `storage.e9.N0(): String`、`storage.e9.F0(): long` |
| 离线来源与哈希 | [8.0.72 findings](wechat-8.0.72-findings.md) | [8.0.78 findings](wechat-8.0.78-findings.md) |

`NotificationItem.i` 的 `toString()` 标签虽为 msgId，`notification.x` → `m0.a(...)` 的调用链证明它取自上述服务端 ID accessor，不能与本地 `getMsgId()` 混用。记录的是运行时原名 `f/h/i/g/c`，不是 JADX 的生成别名。版本名与版本号必须**同时**匹配；8.0.76、8.0.77 及其他组合尚未启用此映射。

非空会话与正数服务端 ID 用于通知身份标记、撤回索引和归档裁剪。重建先过滤精确目标及已确认的撤回提示，再推进轮次；正文回退、MessagingStyle、RemoteInput 历史和可关联的回复 ID 一起清理。当前归档实例及活动通知 token 均匹配时才重新发布，已移除或替换的通知不能被旧事件复活；裁剪为空时取消通知并返回 `StopPost`。

无精确 ID 的旧消息保留；通知栏本人回复只有模块 UUID，不能凭发送顺序绑定服务端 ID。未经过该通知入口或改用异步事件入口的路径不承诺精确裁剪。进程索引最多 64 个会话、每会话 128 个撤回 ID；撤回重建取消旧图片预览并回退为剩余文字，剩余图片的视觉回归属于计划第 4 项。实现、证据路径及详细边界见[撤回通知历史裁剪](wechat-recall-history.md)。两版均未完成本次真机验收。

### 3.6 Android/SystemUI 与本地通知 hook 合约

- 微信主进程的 `android.app.NotificationManager.notify(String, int, Notification): void` BEFORE hook 必须遵守本地装饰返回的 `Decorating.StopPost`，阻止空撤回提示和已抑制快照再次发布；这属于 framework 合约，不新增微信混淆版本映射。
- 标准 SystemUI 移除通知仅在 `NotificationListenerService.REASON_CLICK`、`REASON_CANCEL`、`REASON_CANCEL_ALL` 时发出定向轮次重置；listener cancel 不清空回复轮次。沿用活动通知 token 和接收端代次校验，避免通知 id 复用时误清新通知。
- 原生回复转发只执行一次 `RemoteInput.addResultsToIntent()`；@ 前缀调整在 results Bundle 内完成，不再拷贝旧 ClipData。原生 PendingIntent 回调 `resultCode != 0` 时中止预留，不记成功回复历史。
- `WeChatDecorator.Local.onDestroy()` 关闭 `MessagingBuilder` 的接收器并停止应用内回复追踪、清理来源索引和轮次重置接收器。验证时覆盖生命周期与原生/合成回复的混合场景；本次仅有构建和自动化证据。

## 4. 一次性适配 Runbook（步骤 0–8）

### 步骤 0：准备

- 手机 USB 调试已授权，`adb devices` 只有一台设备。
- LSPosed 已激活且在 `com.tencent.mm` 有作用域；记录当前模块版本与签名（用于后续原地升级）。
- 记录当前可用 APK 路径作为回滚点：`build/outputs/apk/release/NevolutionXposed-release.apk`。

### 步骤 1：采集（只读）

```powershell
.\tools\collect-device-info.ps1 -ProbeProfile -RunTests
```

产物在 `.debug-artifacts/device/<时间戳>/`：`device.txt`、`wechat-package.txt`、`apks/`、`apk-sha256.txt`、`probe/dex-anchors.txt`、`probe/profile-report.md`、`probe/profile-proposal.java`、`probe/test-result.txt`。

预期：报告里给出 `versionName/versionCode`、APK 的 SHA-256、锚点命中的 dex，以及接收器/辅助类/门禁的候选与校验结论（`valid` / `missing static boolean`）。

### 步骤 2：定位回复链路并生成画像

阅读 `probe/profile-report.md`：

- `gate:` 行给出门禁类与方法（顺序即接收器调用顺序）。
- `helper:` 行给出 `X.b(Intent): Bundle`。
- 候选唯一且校验通过时，`probe/profile-proposal.java` 给出可直接粘贴的画像常量与 `forPackage()` 映射行。
- 若报告说 `patch: skipped, RELEASE_x_y_z already present and matches the probe`，说明当前代码已经覆盖该版本，无需改动。
- 若候选不唯一或方法缺失，**不要**猜：展开 `probe/sources/` 人工核对 `onReceive` 的调用点，必要时在报告中补充证据后再改代码。

### 步骤 3：核对图片事件锚点

图片事件只在 8.0.72/3085 上验证过。新版本按以下顺序核对（`WeChatImageEvents.install`）：

1. `v65.z.d(Object, String)`、`yh3.f` 的 `d` 字段与 `getString/getLong/getInteger`。
2. `com.tencent.mm.storage.f9` 的 `O0/getMsgId/getCreateTime/getType/C0`。
3. `b80.m.oi(...)`、`m90.b`、`com.tencent.mm.vfs.w6.i(...)`。

任一项不匹配时，保持门禁不通过（功能自动关闭）并在 findings 文档记录，不要放宽到"猜一个同名方法"。

应用内回复轮次另按第 3.3 节核对，不能沿用图片事件的消息类映射：

1. 用目标版本 APK 的哈希和反编译位置定位本人消息的状态更新方法，确认其覆盖父类方法、先更新状态，再在本人消息状态为 2 时发出 `SendMsgSuccessEvent`；同时确认状态 5 的失败分支和被排除的消息类型。
2. 核对方向、状态、会话标识、匿名消息 ID、创建时间及排除条件的完整返回类型。将**同时匹配**的 `versionName/versionCode` 映射写入 `WeChatAppReplyEvents.Profile.forVersion()`；未知版本不得借用旧版混淆名。
3. 增加版本映射测试，并在对应 findings 文件记录 APK 哈希、源码位置、签名与运行状态。此钩子不读取消息正文；安装 APK 与设备验证仍按用户当次授权执行。

撤回功能另按第 3.5 节核对：

1. 沿通知监听器 → `notification.x` → `m0.a(...)` → `NotificationItem.i` 验证服务端 ID 的来源，同时核对 `f/h/i` 原名和类型。
2. 沿撤回生产者 → `RevokeMsgEvent.g.c` → `IEvent.e()` 验证事件发布路径、payload 类型、会话 accessor 和同一个服务端 ID accessor；异步发布入口须另查，不能假定 `e()` 覆盖全部撤回。
3. 全部签名确认后才增加精确版本映射及拒绝错误版本组合的测试。记录没有精确 ID 时的保守行为，不能按正文、时间或前一条消息猜目标。

### 步骤 4：核对通知解析与通话识别

在授权设备验证后，用真实通知逐项核对第 3.4 节表格：文本前缀与计数、媒体标记、通话文案、id=41 校正、`android.car.EXTENSIONS`、头像路径。额外检查非聊天 ticker 不新增模块回复，聊天通知即使没有原生回复对象也能按已知目标和画像条件进入合成回退。确认无误后在验收矩阵打勾。

### 步骤 5：填画像并跑测试

- 直接把 `profile-proposal.java` 的片段粘进 `WeChatReplyProfile.java`（常量 + `forPackage()` 第一行映射），或先 `git apply --check .debug-artifacts/device/<时间戳>/probe/profile-proposal.patch` 再 `git apply`（补丁按 LF 生成，供仓库根目录直接使用）。
- 在 `WeChatReplyProfileTest` 中补一条该版本的映射断言。涉及独立功能时补对应版本门禁、错误组合及回退验证；撤回相关用例见 `RecallIndexTest`、`RecallHistoryTest` 和 `Plan123Instrumentation`，不要把 instrument APK 编译视为用例执行。
- 运行：

```powershell
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
.\gradlew.bat -PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
```

### 步骤 6：构建与安装

先完成本地构建与签名校验，再进入设备操作。本项目 Debug APK 默认使用现有 release 密钥，必须传入 `-PrequireReleaseSigning=true`；签名配置通过本地 Gradle 属性或进程环境变量提供，不在命令示例中输出密码。签名配置缺失时停止交付，不回退到默认 debug 密钥。对照 release 证书验证 APK 签名，检查 SHA-256 并保留上一版同签名 APK。

只有用户授权安装后才执行同签名原地升级，可保留 LSPosed 的模块激活与作用域：

```powershell
# 已完成步骤 5 且签名校验通过；本命令仅在获得安装授权后执行
adb install -r .\build\outputs\apk\debug\NevolutionXposed-debug.apk
```

Debug 与 release 包采用同一现有 release 证书，不需要因切换构建类型卸载。安装失败先核对设备包与本地包证书和版本，不自动卸载。

装完回拉校验，确认设备运行的就是本次构建：

```powershell
$remote = (adb shell pm path com.oasisfeng.nevo.xposed).Trim() -replace '^package:', ''
adb pull $remote .debug-artifacts/installed-check.apk
Get-FileHash .debug-artifacts/installed-check.apk -Algorithm SHA256
```

### 步骤 7：重启微信与 SystemUI，确认画像生效

以下进程重启仅在用户授权的安装测试范围内执行；构建不代表设备操作授权。不得据此推断允许重启整机。

```powershell
adb shell "su -c 'am force-stop com.tencent.mm; sleep 1; am start -n com.tencent.mm/.ui.LauncherUI; sleep 1; killall com.android.systemui'"
adb logcat -d -v time | Select-String -Pattern 'NX_REPLY|MainHook|WeChatDecorator'
```

期望看到：

```text
NX_REPLY stage=profile_ready version=<版本>/<versionCode> profile=wechat-<版本>
  receiver=com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver
  helper=<helper> gates=<门禁类>[<方法>] usable=true
```

轮次钩子另查 `WeChat.Identity` 的 `source=app_reply_ready profile=<版本>/<versionCode>`；`source=app_reply_unavailable` 表示版本或签名门禁未通过。ready 仅说明钩子装上，不能代替应用内发送后的轮次验证。

撤回钩子另查 `source=recall_ready version=<版本> code=<versionCode>`；`source=recall_unavailable reason=unsupported_version` 表示精确版本未覆盖，`source=recall_unavailable stage=signature` 表示布局校验失败。`recall_event_error` / `recall_identity_error` 表示回调读取失败。ready 同样不能证明目标消息已正确裁剪，必须执行第 6 节对应场景。

若仍是 `profile_rejected ... gates=none usable=false`，回到步骤 2 重新核对门禁类与方法；若一直看不到该行，说明 LSPosed 还没换到新 APK，再重启一次微信。

### 步骤 8：设备回归与记录

模块自身的 framework 仪器测试可以独立执行，无需重启微信或 SystemUI。安装两个已验证同 release 证书的 APK 后，核对 `pm list instrumentation` 的目标为 `com.oasisfeng.nevo.xposed`，再运行：

```powershell
adb -s <设备序列号> shell am instrument --no-hidden-api-checks -w -r com.oasisfeng.nevo.xposed.test/com.oasisfeng.nevo.decorators.wechat.MessageIdentityInstrumentation
```

`--no-hidden-api-checks` 仅关闭本次测试进程的隐藏 API 限制，以覆盖注入环境使用的 `Notification.mSortKey` 等字段；不修改系统全局设置。Android 16 未加该参数时可出现 `NoSuchFieldError`，不能据此把字段改名。runner 只运行模块的 framework 用例，不安装微信混淆 hook、不向真实会话发送消息，也不能代替微信端到端验收。检查完整测试计数、失败状态和最终 `INSTRUMENTATION_CODE: -1`；adb 的退出码 0 本身不表示通过。

- 按第 6 节验收矩阵执行，全部证据（日志行、通知 dump、哈希）留在 `.debug-artifacts/`。
- 在 `docs/wechat-<版本>-findings.md` 记录：设备与版本事实、验证过的映射表、根因、修复与验证结果、未验证项。
- 对应用内回复轮次，分别记录“离线映射已核对”“运行时钩子已触发”“下一条新来信正确切换轮次”的证据；没有设备结果时只填写第一项。
- 对撤回，分别记录离线 ID 来源、hook 安装、实际 B/C 裁剪、无 ID 保留及通知清除后不复活的证据；对合成回复和 SystemUI 清理记录本次实际验证结果，不能沿用历史原生回复的通过结论。
- 同步更新本指南的版本敏感表、日志、回退边界和验收矩阵；相关专题文档与版本 findings 保持一致，遵守项目 `AGENTS.md` 的同步规则。
- 需要公开提交时，只提交源码、`tools/`、`docs/` 中确认要公开的文件；`.debug-artifacts/` 永不入库。

## 5. 证据与日志规范

可以记录：stage 名、类/方法完整签名、结果键、输入长度、异常栈、APK 哈希、设备/版本事实。

禁止记录：回复内容、联系人名、账号 ID、原始会话 key、原始消息 ID、通知正文、序列化 Intent 中的隐私数据。通知 dump 可能包含隐私，只留本地证据，公开文档先脱敏。

必备证据清单：

| 证据 | 来源 |
| --- | --- |
| `profile_ready ... usable=true` | logcat / `.debug-artifacts` 内 LSPosed 日志 |
| 通知 `actions={ [0] "回复" -> ... }` 与 `android.car.EXTENSIONS` | `adb shell dumpsys notification --noredact` |
| 回复链路 `native_receiver` → `native_pending_intent_callback resultCode=0` | logcat（`NX_REPLY` 前缀） |
| 应用内回复轮次 `app_reply_ready`、匿名 `app_reply_sent matched`、下一条新来信 `round advanced=true` | logcat（`WeChat.Identity`；不记录正文、联系人或原始 talker） |
| 撤回 `recall_ready`、B/C 被裁剪、A/D 保留及清除后不复活 | 安装日志 + 脱敏通知快照/设备观察；ready 只证明安装 |
| 本地/设备 APK SHA-256 一致 | `Get-FileHash` + `adb pull` 回拉 |

## 6. 验收矩阵

| 场景 | 操作 | 期望 |
| --- | --- | --- |
| 单聊回复 | 收到单聊消息后在通知栏输入并发送 | 会话内出现该消息，日志出现完整 native 链路 |
| 群聊回复 | 同上（群聊） | 消息进入正确群会话 |
| 多语言/emoji | 中文、英文、emoji 各一条 | 不截断、不乱码、不重复发送 |
| 媒体/文件通知 | 收到图片/视频/文件消息 | 保留微信原布局；符合聊天和投递条件时提供回复入口 |
| 视频/语音通话 | 拨打或接听 | 通知类型正确，不被当成普通消息 |
| 图片事件 | 收到图片消息 | 缩略图正常；版本不匹配时应自动关闭而非崩溃 |
| 通知正文 | 任意消息 | 显示真实文本，不出现 `[消息]` 占位 |
| 应用内回复轮次 | 对方来信 A → 微信内回复 B → 对方来信 C | C 开始新轮次，不出现 A 或 B 的正文 |
| 混合回复轮次 | A → 微信内回复 B → 通知栏回复 D → 对方来信 E | 新轮次保留 D、E；通知栏回复不被误判为应用内回复 |
| 轮次门禁与失败发送 | 未支持版本、签名不符或发送失败 | 不误设轮次边界，通知栏回复链路仍可用 |
| 非聊天通知 | 好友申请、杂项、null/无冒号 ticker | 不新增/代理模块回复，原动作与消息重建保留 |
| 合成回复资格 | 聊天通知无原生回复对象，分别有/无已知目标和有效画像 | 有完整条件时携带正确 `key_username` 并送达；条件不足时没有合成按钮 |
| 撤回精确裁剪 | 私聊、群聊分别 A → B → 撤回 B 的提示 C → D | 仅有精确 ID 时删除 B/C，保留 A/D，不把提示作为新来信 |
| 撤回身份与重放 | 同文同时间、连续撤回、旧规范化快照重放 | 按服务端 ID 区分，不误删相邻消息，不复活已撤回目标 |
| 无 ID 与本人消息 | 旧消息/通知栏本人回复缺少服务端 ID | 保留无法精确关联的消息，仅排除已确认提示 |
| 撤回与通知代次 | 清除通知、替换通知或复用 id 后撤回；撤回最后一条 | 旧事件不重新发布；裁剪为空取消且 `StopPost` 阻止重发 |
| 撤回版本门禁 | 未支持版本、名称/代码交叉组合、字段或方法类型不符 | 不启用精确关联，不按文本猜目标 |
| SystemUI 移除原因 | 点击、手动清除、全部清除及 listener cancel | 前三种重置匹配代次；listener cancel 不清轮次 |
| RemoteInput 与生命周期 | @ 前缀、原生回调失败、混合回复、Local 销毁 | 不重复编码/发送；失败不留成功历史；接收器释放 |

## 7. 常见失败模式

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `profile_rejected ... gates=none` | 门禁类或方法改名 | 重跑探针，按报告更新画像 |
| 通知没有回复按钮 | 画像不可用、非聊天 ticker，或合成路径没有可信会话目标 | 分别核对聊天资格、原生动作、已知 key 与画像，不放宽版本/签名校验 |
| 输入后不发送（无异常） | 目标用户名缺失、门禁不可用或 PendingIntent 失效 | 优先检查原生链路；合成路径核对 key 的来源和转发，不能从正文猜用户名 |
| 通知正文变成 `[消息]` | 车机会话占位文本 | `MessagingBuilder` 的占位回退路径生效即为正常 |
| 模块看起来没生效 | LSPosed 仍持有旧 APK | 再重启一次微信；必要时重启 SystemUI |
| `su: inaccessible or not found` | 临时 root 授权过期 | 重新授权；仅做验证时用 logcat/dumpsys 即可 |
| 微信内回复后历史仍增长 | 轮次版本门禁失败、talker 未关联或发送状态事件未触发 | 核对 `app_reply_ready`、匿名 `app_reply_sent matched` 和第 3.3 节映射；勿读取消息正文定位 |
| `recall_unavailable` | 未支持精确版本或完整签名不符 | 按第 3.5 节重新核对原名、类型和 ID 来源，不套用旧混淆名 |
| ready 后撤回仍残留 | 缺少精确 ID、通知/事件走其他入口或回调读取失败 | 核对调用链及匿名错误阶段；保留未知 ID 消息，不按前一条或同文猜目标 |
| 脚本报 `No device attached` | 未连接或未授权 | `adb devices` 确认状态为 `device` |

## 8. 已知未验证项与回滚

- WeChat 8.0.76 画像存在但**未实机验证**，回归状态为 pending，不要声称通过。
- 图片事件仅覆盖 8.0.72/3085，其他版本自动关闭。
- 应用内回复轮次事件仅映射 8.0.72/3085 与 8.0.78/3180；两版映射都尚未经过真实微信运行验证，其他版本自动保留旧轮次行为。
- 撤回身份/事件仅映射上述两版；两版实际 hook 时序、精确裁剪、清除后不复活及图片回退待真机验证。新合成回退、SystemUI 移除原因和生命周期行为同样不能借用历史原生回复验收。
- 2026-09-30 项目计划 1/2/3 已通过本地 106 项单元测试、Debug/仪器 APK 构建和 lint（0 errors）。同日按用户授权安装后，PJZ110 / Android 16 上 33 项模块 framework 仪器用例全部通过；修复普通进程缺少 Xposed API 的合成回退，测试使用仅作用于自身进程的隐藏 API 参数。两包签名及安装后回拉哈希均核对通过，微信/SystemUI 未重启。验证记录见[项目修改计划](project-modification-plan.md)；真实微信 hook 与通知行为仍未验收。
- 回滚：保留上一版同 release 证书的 APK，取得安装授权后原地回滚；旧包若触发版本降级限制须先检查安装结果和原因，不自动卸载或重启整机。

## 附录 A：画像模板与命名规范

```java
	/** Verified against WeChat <versionName>/<versionCode> on <date>. */
	private static final WeChatReplyProfile RELEASE_<x>_<y>_<z> = new WeChatReplyProfile(
			"wechat-<versionName>",
			new String[] { RECEIVER_CLASS },
			new String[] { "<RemoteInput 辅助类>" },
			new String[] { "<门禁类>" },
			new String[] { "<方法 1>", "<方法 2>", "<方法 3>" });

	// forPackage() 的第一行：
	if ("<versionName>".equals(versionName) || versionCode == <versionCode>L) return RELEASE_<x>_<y>_<z>;
```

规范：

- 标签统一 `wechat-<versionName>`；常量名 `RELEASE_<版本号用下划线>`。
- 此模板描述现有**通知栏回复候选画像**：版本名或版本号任一命中（`||`）选候选，再校验运行时签名；未知版本选择 `wechat-legacy` 候选，不能仅凭候选标签声称支持。应用内回复、图片、撤回的精确门禁采用同时匹配（`&&`），不得复制本模板的 `||`。
- `gateMethods` 顺序与接收器调用顺序一致，便于人工比对。
- 新增画像必须同步在 `WeChatReplyProfileTest` 增加映射断言。

## 附录 B：探针产物说明

| 文件 | 内容 | 判读 |
| --- | --- | --- |
| `probe/dex-anchors.txt` | 每个锚点命中的 dex 文件 | 确认关键字符串是否还在同一个 dex |
| `probe/profile-report.md` | 设备/版本/哈希、锚点、接收器分析、结论 | 主要证据，先看 `Probe result` 段 |
| `probe/profile-proposal.java` | 候选画像常量与映射行 | 候选唯一且校验通过时才生成 |
| `probe/profile-proposal.patch` | 可直接 `git apply` 的补丁（LF 换行） | 先 `git apply --check`；已存在同名画像时改为一致性结论 |
| `probe/test-result.txt` | `gradlew testDebugUnitTest --offline` 输出 | `PASS` 才继续装包 |

回复探针只提供接收器、辅助类和门禁的候选证据，不会自动完成图片、应用内回复、撤回或 SystemUI 交互的适配；这些路径须按正文分别核对、记录和验证。探针测试通过也不能替代步骤 5/6 的构建签名检查和设备操作授权。
