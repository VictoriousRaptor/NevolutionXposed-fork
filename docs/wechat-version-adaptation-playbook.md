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
| 通话通知类型/文案异常 | 通知文本解析规则变化 |

三条铁律：

1. **未知版本不得暴露无法投递的回复入口**：画像必须通过完整签名校验后才提供回复动作，宁可暂时不显示，也不能出现"能输入、发不出去"的假按钮。
2. **禁止凭短方法名猜混淆类名**：所有混淆类/方法都要在运行时按完整签名校验，并在文档中记录证据（APK 哈希 + 反编译位置）。
3. **先证据、后改动**：先只读采集（设备信息、APK、反编译、日志），确认布局后再改代码；结论必须能落到日志、哈希或反编译行。

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

补充事实（8.0.78 实测）：接收器 `onReceive` 的第一个语句是 `key_username` 判空；只有走微信自己的车机回复 `PendingIntent` 才带该字段，所以必须让门禁校验通过、由微信自己创建车机会话，模块合成路径无法补出 `key_username`。

### 3.2 图片事件（版本绑定最深，当前仅 8.0.72/3085）

| 依赖点 | 8.0.72 | 失效表现 |
| --- | --- | --- |
| 版本门禁 | `versionName=8.0.72` 且 `versionCode=3085` | 其他版本直接 `events_unavailable` |
| 状态类 / 简单消息类 | `v65.z`、`yh3.f` | 类找不到或字段类型不符 |
| 消息类 | `com.tencent.mm.storage.f9`（`O0`/`getMsgId`/`getCreateTime`/`getType`/`C0`） | 事件索引拿不到消息 |
| 查询 / 路径类 | `b80.m.oi(...)`、`m90.b`、`com.tencent.mm.vfs.w6.i(...)` | 缩略图路径解析失败 |

### 3.3 通知解析与通话识别

| 依赖点 | 现状 | 失效表现 |
| --- | --- | --- |
| 通知文本前缀 | `微信xx: 文本`、`[N条]` 计数 | 正文解析错位、计数丢失 |
| 媒体标记 | `[表情]`/`[视频]`/`[文件]`/`[链接]`/`[音乐]`/`[位置]`/`[红包]`/`[转账]`/`[小程序]` | 被误当成纯文本处理 |
| 通话文案 | `[语音通话]`、`[视频通话]`、`语音通话中` | 通话通知类型或计时显示异常 |
| 通话校正来源 | 通话通知 id=41 | 视频通话被标成语音通话 |
| 车机会话 | `android.car.EXTENSIONS`、占位文本 `[消息]` | 通知正文退化为占位符 |
| 头像路径 | `/data/data/com.tencent.mm/MicroMsg/{hash}/avatar/...` | 通知头像缺失 |

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

### 步骤 4：核对通知解析与通话识别

用真实通知逐项核对第 3.3 节表格：文本前缀与计数、媒体标记、通话文案、id=41 校正、`android.car.EXTENSIONS`、头像路径。确认无误后在验收矩阵打勾。

### 步骤 5：填画像并跑测试

- 直接把 `profile-proposal.java` 的片段粘进 `WeChatReplyProfile.java`（常量 + `forPackage()` 第一行映射），或先 `git apply --check .debug-artifacts/device/<时间戳>/probe/profile-proposal.patch` 再 `git apply`（补丁按 LF 生成，供仓库根目录直接使用）。
- 在 `WeChatReplyProfileTest` 中补一条该版本的映射断言。
- 运行：

```powershell
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
.\gradlew.bat testDebugUnitTest --offline --console=plain
```

### 步骤 6：构建与安装

优先"同签名原地升级"，可保留 LSPosed 的模块激活与作用域：

```powershell
# 发布密钥参数从 .debug-artifacts/signing/github-secrets.txt 读取，勿写入仓库
.\gradlew.bat assembleRelease --offline --console=plain `
  "-PRELEASE_STORE_FILE=<release.jks>" "-PRELEASE_STORE_PASSWORD=<store pwd>" `
  "-PRELEASE_KEY_ALIAS=<alias>" "-PRELEASE_KEY_PASSWORD=<key pwd>"
adb install -r .\build\outputs\apk\release\NevolutionXposed-release.apk
```

需要更详细日志时再改用 debug 包（签名不同，必须先卸载：`adb uninstall com.oasisfeng.nevo.xposed`，装好后要在 LSPosed 管理器重新启用并确认作用域）。

装完回拉校验，确认设备运行的就是本次构建：

```powershell
$remote = (adb shell pm path com.oasisfeng.nevo.xposed).Trim() -replace '^package:', ''
adb pull $remote .debug-artifacts/installed-check.apk
Get-FileHash .debug-artifacts/installed-check.apk -Algorithm SHA256
```

### 步骤 7：重启微信与 SystemUI，确认画像生效

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

若仍是 `profile_rejected ... gates=none usable=false`，回到步骤 2 重新核对门禁类与方法；若一直看不到该行，说明 LSPosed 还没换到新 APK，再重启一次微信。

### 步骤 8：设备回归与记录

- 按第 6 节验收矩阵执行，全部证据（日志行、通知 dump、哈希）留在 `.debug-artifacts/`。
- 在 `docs/wechat-<版本>-findings.md` 记录：设备与版本事实、验证过的映射表、根因、修复与验证结果、未验证项。
- 需要公开提交时，只提交源码、`tools/`、`docs/` 中确认要公开的文件；`.debug-artifacts/` 永不入库。

## 5. 证据与日志规范

可以记录：stage 名、类/方法完整签名、结果键、输入长度、异常栈、APK 哈希、设备/版本事实。

禁止记录：回复内容、联系人名、账号 ID、通知正文、序列化 Intent 中的隐私数据。

必备证据清单：

| 证据 | 来源 |
| --- | --- |
| `profile_ready ... usable=true` | logcat / `.debug-artifacts` 内 LSPosed 日志 |
| 通知 `actions={ [0] "回复" -> ... }` 与 `android.car.EXTENSIONS` | `adb shell dumpsys notification --noredact` |
| 回复链路 `native_receiver` → `native_pending_intent_callback resultCode=0` | logcat（`NX_REPLY` 前缀） |
| 本地/设备 APK SHA-256 一致 | `Get-FileHash` + `adb pull` 回拉 |

## 6. 验收矩阵

| 场景 | 操作 | 期望 |
| --- | --- | --- |
| 单聊回复 | 收到单聊消息后在通知栏输入并发送 | 会话内出现该消息，日志出现完整 native 链路 |
| 群聊回复 | 同上（群聊） | 消息进入正确群会话 |
| 多语言/emoji | 中文、英文、emoji 各一条 | 不截断、不乱码、不重复发送 |
| 媒体/文件通知 | 收到图片/视频/文件消息 | 保留微信原布局，同时有回复入口 |
| 视频/语音通话 | 拨打或接听 | 通知类型正确，不被当成普通消息 |
| 图片事件 | 收到图片消息 | 缩略图正常；版本不匹配时应自动关闭而非崩溃 |
| 通知正文 | 任意消息 | 显示真实文本，不出现 `[消息]` 占位 |

## 7. 常见失败模式

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `profile_rejected ... gates=none` | 门禁类或方法改名 | 重跑探针，按报告更新画像 |
| 通知没有回复按钮 | 画像不可用，模块主动隐藏 | 先让 `profile_ready usable=true` |
| 输入后不发送（无异常） | 走了合成路径、缺少 `key_username` | 确认门禁旁路在 `Application.onCreate` 阶段装上，走微信原生 PendingIntent |
| 通知正文变成 `[消息]` | 车机会话占位文本 | `MessagingBuilder` 的占位回退路径生效即为正常 |
| 模块看起来没生效 | LSPosed 仍持有旧 APK | 再重启一次微信；必要时重启 SystemUI |
| `su: inaccessible or not found` | 临时 root 授权过期 | 重新授权；仅做验证时用 logcat/dumpsys 即可 |
| 脚本报 `No device attached` | 未连接或未授权 | `adb devices` 确认状态为 `device` |

## 8. 已知未验证项与回滚

- WeChat 8.0.76 画像存在但**未实机验证**，回归状态为 pending，不要声称通过。
- 图片事件仅覆盖 8.0.72/3085，其他版本自动关闭。
- 回滚：保留上一版 release APK，`adb install -r` 同签名旧包即可；若必须换签名版本，先卸载，再在 LSPosed 管理器重新启用模块并确认作用域（本机可通过 ReSukiSU 的模块操作按钮打开管理器）。

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
- 映射同时匹配 `versionName` 与 `versionCode`，两者任一命中即用该画像；未知版本落到 `wechat-legacy` 并在运行时校验失败后隐藏回复入口。
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
