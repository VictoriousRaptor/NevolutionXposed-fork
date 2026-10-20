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
| 8.0.77 适配（2026-09-30） | 基线 `f132a2f`，分支 `codex/wechat-8.0.77`；3141/3160 分别完成通知回复、图片/大图、应用内回复及撤回画像；3141 五条链路 ready，真实消息验收待完成，见 [核对记录](wechat-8.0.77-findings.md) |
| 2026-10-06 媒体回复去重与标记失效 | 修复输入期间新通知到达导致媒体内联回复动作累积的问题；`attachReplyAction` 去除旧可投递自由文本回复；`setActions` 失效 `nevo.actionsSerialized` 标记；源码修复及回归用例已添加，真实微信行为与修复后真机 UI 待验收 |
| 2026-10-12 通知 Markdown | 新增默认关闭的 `WeChatDecorator.markdown` 开关，只在通知输出时解析加粗、斜体、删除线及反引号行内/围栏代码；代码采用等宽字体和灰色底纹，受系统 span 清理限制。历史身份、回复和撤回使用保留的原文。无需新增 hook 或微信混淆画像；真实通知样式待真机验证，见[实现与验证边界](wechat-notification-markdown.md) |
| 2026-10-13 代码样式清理修复 | 针对手机 Android 16 / ColorOS 16.0.10 的消息 span 白名单清理，新增 framework 消息清理 AFTER hook，在微信主进程与 SystemUI 中仅恢复本模块代码范围的等宽字体和灰底；不关闭系统清理，也不恢复其他被移除的 span。签名校验与失败回退见第 3.1 节，设备验证状态见[专题文档](wechat-notification-markdown.md) |
| 8.0.79 适配（2026-10-16） | 3182 独立画像覆盖通知回复、图片/普通大图、应用内回复与撤回；图片状态读取由 d 迁移为画像中的 f，保留旧版 d。模块 3.2.1/code 14，同步升级三项 AndroidX 稳定依赖；完整映射和分层验证见 [8.0.79 记录](wechat-8.0.79-findings.md) |

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

8.0.77/3160 的本地 APK 核对结果：接收器仍为
`com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver.onReceive(Context, Intent): void`，
辅助方法为 `z2.s1.b(Intent): Bundle`，门禁为 `lq1.a.e()/h()/c(): boolean`（均为 static）。
`key_username`、`key_voice_reply_text` 与两个广播 action 保持不变；消息送达与基线合成回退仍待真机验证。

2026-09-30 的实现优先使用 CarExtender 的原生回复 `PendingIntent`，其次使用通知 actions；没有原生动作时，仅在聊天通知、非空已知会话 key、存在 `contentIntent` 且 `MainHook.isSyntheticReplyAvailable()` 校验通过时提供合成回复。合成代理及转发接收器都携带 `key_username`，会话来自已解析的原生会话或第 3.5 节通知身份 hook，不能从标题、ticker 或通知 id 猜用户名。本次合成回退尚未真机验证。

普通模块/仪器测试进程没有 compile-only 的 Xposed API，不能直接加载继承 `XposedModule` 的 `MainHook`。`MessagingBuilder.isSyntheticReplyAvailable()` 对 `NoClassDefFoundError` 返回 false，并记录 `action_synthetic_skipped reason=xposed_api_unavailable`；保持消息重建及原生回复，不添加未经验证的合成入口。该回退不修改微信版本映射或签名门禁，也不向 APK 打包 Xposed API。

2026-10-20 回复门禁安装去重：微信主进程的 `Application.onCreate(): void` AFTER、`MMAutoMessageReplyReceiver.onReceive(Context, Intent): void` BEFORE，以及合成回复派发前，统一复用 `MainHook.hookAutoLogicMethods()`。仅处理当前 `WeChatReplyProfile` 已校验的无参数、static、boolean 返回值门禁（包括上述各版本和 legacy 候选）；保留现有版本名/版本号匹配规则、画像重试及启用条件，不新增目标或作用进程。安装过程串行，按精确 `java.lang.reflect.Method` 记录成功项；同一方法不随回复次数重复添加回调，单项失败不记为成功，后续安装调用仍可重试。`hookAutoLogicMethods: <方法> hook failed` 保留安装失败诊断；`car_mode_bypass_ready` 仅表示安装流程已执行，不证明所有方法均成功或消息已送达。RemoteInput 辅助类和平台结果回退保持原有安装与单次取用语义。

同轮简化将三处原生回复动作构建集中在 `MessagingBuilder`，保留原始 PendingIntent、resultKey/extras、动作顺序及无 RemoteInput 时的默认结果键；合成派发仍独立。`Notification.actions` 直接写入公开字段，写入前仍清除 `nevo.actionsSerialized` 标记。图片首次发布与重新挂接共用通知构建步骤，继续复制原有身份、轮次、撤回和 Markdown 原文元数据；未改变下载目标、缓存上限或各图片画像。

回归需分别覆盖启动后多次回复不叠加门禁回调、单项安装失败后的重试、原生/缺 RemoteInput/媒体回复、同 ID 更新后的动作去重、图片首次发布及重新挂接的身份与 token 保留。2026-10-20 本地 138 项单元测试及 Debug/仪器 APK 构建通过，两包签名与现有 release 证书一致；PJZ110 / Android 16 上 46/46 项模块 framework 仪器测试通过，已安装微信 8.0.79/3182 的 40 项描述符检查通过。测试仅在模块测试进程放开隐藏 API 检查；微信与 SystemUI 进程均未变化，临时测试包已卸载，保留本轮 Debug 模块。上述测试不证明新门禁 hook 已在微信进程加载，也不替代真实消息收发、合成回复安装失败重试或厂商通知 UI 验收；这些运行时行为仍待验证。

本轮应用源码 Lint 通过，结果为 0 errors / 33 warnings。测试源码的 Lint 分析在常规和单工作线程尝试中均长时间未产出，已中止；生成报告时明确排除了 `lintAnalyzeDebugAndroidTest` 与 `lintAnalyzeDebugUnitTest`，测试源码静态检查状态仍为未完成。

2026-10-20 后续结构清理及调试能力复核：删除 `Conversation` 中仅由 `setType()` 自行维护、从未交给消息的旧 sender Builder/占位对象及无调用 helper；实际 Person 仍由 `NotificationMessages` 构造，群成员原名 URI、类型切换时的参与者清理和异步快照代次保留。仅删除 `SystemUIDecorator` 从未读写的字符串归档；恢复 NLS 弱引用、`setNLS()`、`getNLS()`、`cancelNotification(String)` 及 `MainHook` 构造回调中的保存操作，保留同进程调试访问和手动取消能力。listener 原来有实际写入，不能将没有源码读取调用等同于没有调试价值。`NotificationListenerService` 所有已声明构造函数的 AFTER hook、实际监听器类的 posted/removed BEFORE hook、媒体装饰分发和微信轮次重置广播均保留。`LocalDecorator` 的整数 ID 归档、120 总条数/20 单会话上限及匹配代次的清理均不变。

同轮图片状态简化删除 `Preview` 对 `Request.token` 的重复保存，并将只供同步成功日志读取的下载提交结果从 `Task` 改为局部值；token 比较、反射调用完整签名、返回值非负成功条件、轮询/取消时序、TTL 和失败关闭行为不变。适用于当前全部既有画像，不改变任何功能的版本名/版本号匹配规则、开关、诊断日志或启用条件。上述为源码等价性核对；本轮自动化结果另行记录，前述历史设备结果不作为本轮真机验收。仍需在授权设备上分别回归私聊/群聊身份、撤回、图片首次发布/重新挂接、媒体通知及移除后的轮次广播。

本次结构清理的本地验证：138 项单元测试通过，Debug 与仪器 APK 构建通过，两包证书与现有 release 密钥一致；应用生产源码 Lint 为 0 errors / 33 warnings，继续明确排除上述两个测试源码分析任务。本轮没有安装 APK、运行仪器测试或操作微信/SystemUI；构建与源码引用核对不代表运行时 hook 或真实通知行为已验收。

各功能的版本匹配不能混用：

| 功能 | 当前匹配语义 | 未匹配时行为 |
| --- | --- | --- |
| 通知栏回复 `WeChatReplyProfile.forPackage()` | 8.0.79 **且** 3182，8.0.77 **且** 3141/3160；8.0.72/8.0.78 保留名称或版本号任一匹配；再校验运行时签名。8.0.79 名称或 3182 代码的错误组合先退回 legacy | 选 legacy 候选并校验；不可用时不开放合成投递 |
| 图片事件 / 独立大图服务 | 8.0.72 **且** 3085、8.0.77 **且** 3141/3160、8.0.79 **且** 3182；完整签名校验 | 分别不启用对应组件 |
| 应用内回复事件 | 8.0.72 **且** 3085、8.0.77 **且** 3141/3160、8.0.78 **且** 3180、8.0.79 **且** 3182；完整签名校验 | 保留原轮次行为 |
| 撤回身份和事件 | 同上五组精确版本；完整字段/方法及事件父类校验 | 不启用精确撤回关联 |
| 通知 Markdown 展示 | 无新增版本名/版本号匹配或运行时签名校验；适用于既有微信通知重建路径，含上述版本及 legacy 回退；须开启微信通知增强和 `WeChatDecorator.markdown` | 默认关闭；语法不支持、解析达到限制或出现运行时异常时保留原文，不改变其他功能的版本门禁 |

Markdown 在微信进程的 `WeChatDecorator.Local.apply(NotificationManager, String, int, Notification): Decorating` 末段、`MessagingBuilder.flatIntoExtras(MessagingStyle, Bundle): void` 之后执行。`onCreate(SharedPreferences): void` 读取开关，设置存储 schema 为 5，升级默认关闭。`EXTRA_TEXT` 和 `EXTRA_MESSAGES[*].text` 使用可序列化的 Android `StyleSpan`/`StrikethroughSpan`，代码使用 `TypefaceSpan("monospace")`/`BackgroundColorSpan(0x30808080)`。代码内标记保持字面量，正文空白和换行保留，未闭合或空围栏保留原样。原文保存于 `nevo.markdown.rawText`（消息内置于 `extras`），转换的消息还保存 `nevo.markdown.owner=com.tencent.mm`；读取规范消息时清除两项展示元数据，关闭功能后的正常重建也清除它们。重复重建先读取原文，图片预览复制身份时也保留原文；发送 RemoteInput 和输入历史仍保留用户原始文字。回复、图片、应用内回复和撤回仍按上表分别匹配，不因展示可用而视为完成适配。

代码样式恢复 hook：`android.app.Notification$MessagingStyle$Message.stripNonStyleSpans(CharSequence): CharSequence` 的 AFTER 回调，读取 `Message.getExtras(): Bundle`，在清理后的返回值上仅补回代码样式，随后由 framework 原有调用者赋回消息正文；不反射写入 `mText`。作用进程限定 `com.tencent.mm` 主进程和 `com.android.systemui`；沿用这两个 LSPosed 作用域，不加入 `system_server`。安装前精确校验参数、CharSequence 返回值及方法非 static；缺失、签名不符或安装失败记录 unavailable，保留原生输出。实际回调还须同时满足微信通知增强与 Markdown 开关开启、消息 owner 标记匹配、存在原文、解析未超限、清理后的全部文字与原文解析结果一致。原方法异常时不修复；仅补回代码范围中的字体/灰底，保留系统已清理后的其他样式，重复回调不累积 span。签名已离线核对 PJZ110 Android 16 / API 36 / `PJZ110_16.0.10.501(CN01)` 的 framework；未添加微信版本名/版本号门禁，其他 Android/OEM 仅按完整签名校验并保守回退，尚不宣称运行时兼容。旧系统若未经过此清理方法则不触发恢复，不覆盖其他颜色处理或 OEM 的额外清理路径。修改 Markdown 设置需重启微信进程；安装包含新 hook 的 APK 后，微信及 SystemUI 两个进程均需重新加载模块才能验证完整链路，相关设备操作须按授权范围执行。

诊断标签 `WeChat.Markdown`：启动记录 `source=markdown_settings enabled=<true|false>`；Debug 在字符数、样式数、嵌套或工作量超限时记录 `source=markdown_skipped reason=limit`；运行时解析失败记录 `source=markdown_fallback type=<异常类名>`，不记录消息正文。回归须分别检查三种强调样式、行内代码/不同长度反引号/带语言标签围栏/缩进与 CRLF、转义和残缺标记、中文与 emoji 范围、重复通知/回复/撤回及图片切换、关闭开关恢复原文，以及折叠、展开、锁屏和悬浮通知在明暗主题下的字体与底纹。自动化和真实微信/SystemUI 展示的验证层级见[专题文档](wechat-notification-markdown.md)。

恢复 hook 日志：`source=code_styles_hook_ready process=<进程>` 表示安装成功；`code_styles_hook_unavailable ... type=<异常类>` 表示保守回退。首次实际补回样式记录 `source=code_styles_restored process=<进程>`，之后仅 Debug 记录；回调失败记录 `source=code_styles_fallback type=<异常类>`。均不记录正文。新增回归须覆盖原生清理→恢复→Parcel 的重复往返、灰底被清理但字体保留的情况、未标记/owner 不符/正文已改变/解析超限的消息保持原生结果、关闭设置后移除展示元数据，以及两个进程的实际 hook 与通知字体/底纹验证；ready 或 restored 日志仍不能替代 UI 验收。

ART 内联处理：安装 hook 前通过 libxposed API 102 的 `deoptimize(Executable): boolean` 对已核对的调用链取消优化，避免 boot image/JIT 的内联清理代码绕过私有方法 hook。目标依次为 `Message.ensureColorContrastOrStripStyling(int): void`、`MessagingStyle.getBundleArrayForMessages(List, boolean, int): Bundle[]`（static）、`MessagingStyle.addExtras(Bundle, boolean, int): void`、`MessagingStyle.makeMessagingView(int): RemoteViews`；其余均为实例方法。每一项也需完整参数/返回值/static 属性校验，取消优化失败时不安装恢复 hook并记录 unavailable。范围仍限定上述两个进程，取消优化影响共享消息布局方法的执行方式，但不改变无标记消息的清理或输出结果；其它 OEM 调用链不推断已覆盖。

`makeMessagingView` 是受限非 SDK 方法，查找处局部标注 `BlockedPrivateApi`：此代码仅由已注入的 LSPosed 模块执行，不在普通设置 App 中调用。该标注只说明 Xposed 用途，不证明运行时访问成功；访问限制、签名或取消优化失败仍保守回退，须以两个进程的 hook 日志和实际通知验证。未全局关闭 lint 或修改 targetSdk。

2026-10-13 验收分层：134 项主机测试、签名构建及 Release lint 通过；PJZ110 Android 16 上 46/46 framework 仪器用例实际通过，包含新增三项清理恢复回归。用户手动重启系统界面后确认界面生效，测试 APK 已卸载，保留修复版。当前日志缓冲区没有保留专用 ready/restored 记录，未额外确认两个进程的 hook 调用现场；所有通知形态、明暗主题、真实回复投递和其他 OEM 尚未逐项验收。源码、设备测试、用户界面确认与缺失的日志证据分别记录于[专题文档](wechat-notification-markdown.md)。

### 3.2 图片事件（8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.79/3182）

| 依赖点 | 8.0.72 | 失效表现 |
| --- | --- | --- |
| 版本门禁 | 四组精确版本分别选画像；本列为 8.0.72/3085 | 未映射组合直接 `events_unavailable` |
| 状态类 / 简单消息类 | `v65.z`、`yh3.f` | 类找不到或字段类型不符 |
| 消息类 | `com.tencent.mm.storage.f9`（`O0`/`getMsgId`/`getCreateTime`/`getType`/`C0`） | 事件索引拿不到消息 |
| 查询 / 路径类 | `b80.m.oi(...)`、`m90.b`、`com.tencent.mm.vfs.w6.i(...)` | 缩略图路径解析失败 |

8.0.77 的旧图片类不兼容，已改为独立画像并开放精确支持组合。完整新签名、证据及回退见第 3.7 节和 8.0.77 findings。

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

`NotificationItem.i` 的 `toString()` 标签虽为 msgId，`notification.x` → `m0.a(...)` 的调用链证明它取自上述服务端 ID accessor，不能与本地 `getMsgId()` 混用。记录的是运行时原名 `f/h/i/g/c`，不是 JADX 的生成别名。版本名与版本号必须**同时**匹配；8.0.77/3141、8.0.77/3160 与 8.0.79/3182 已新增完整画像（见第 3.7/3.8 节）；8.0.76 及其他未列组合不启用映射。

非空会话与正数服务端 ID 用于通知身份标记、撤回索引和归档裁剪。重建先过滤精确目标及已确认的撤回提示，再推进轮次；正文回退、MessagingStyle、RemoteInput 历史和可关联的回复 ID 一起清理。当前归档实例及活动通知 token 均匹配时才重新发布，已移除或替换的通知不能被旧事件复活；裁剪为空时取消通知并返回 `StopPost`。

无精确 ID 的旧消息保留；通知栏本人回复只有模块 UUID，不能凭发送顺序绑定服务端 ID。未经过该通知入口或改用异步事件入口的路径不承诺精确裁剪。进程索引最多 64 个会话、每会话 128 个撤回 ID；撤回重建取消旧图片预览并回退为剩余文字，剩余图片的视觉回归属于计划第 4 项。实现、证据路径及详细边界见[撤回通知历史裁剪](wechat-recall-history.md)。两版均未完成本次真机验收。

### 3.6 Android/SystemUI 与本地通知 hook 合约

- SystemUI listener 调试入口不依赖微信版本画像，适用于现有 SystemUI hook 范围；在 `android.service.notification.NotificationListenerService` 所有已声明构造函数的 AFTER 回调中刷新弱引用，不改 posted/removed 目标的完整签名、查找校验和兼容回退。`getNLS()` 与 `cancelNotification(String): void` 供同一 SystemUI 进程、模块类加载器内的手工调试使用，不新增自动调用或功能开关。弱引用为空时取消无操作，否则沿用 listener 的取消行为及异常传播；Debug 保留 `cancelNotification ` 日志，Release 不输出该日志。运行时回归应覆盖实例刷新、空引用和按 key 取消，并单独核对下述 listener cancel 不清空微信轮次的规则；源码与构建验证不能代替这些真机检查。
- 微信主进程的 `android.app.NotificationManager.notify(String, int, Notification): void` BEFORE hook 必须遵守本地装饰返回的 `Decorating.StopPost`，阻止空撤回提示和已抑制快照再次发布；这属于 framework 合约，不新增微信混淆版本映射。
- 标准 SystemUI 移除通知仅在 `NotificationListenerService.REASON_CLICK`、`REASON_CANCEL`、`REASON_CANCEL_ALL` 时发出定向轮次重置；listener cancel 不清空回复轮次。沿用活动通知 token 和接收端代次校验，避免通知 id 复用时误清新通知。
- 原生回复转发只执行一次 `RemoteInput.addResultsToIntent()`；@ 前缀调整在 results Bundle 内完成，不再拷贝旧 ClipData。原生 PendingIntent 回调 `resultCode != 0` 时中止预留，不记成功回复历史。
- `WeChatDecorator.Local.onDestroy()` 关闭 `MessagingBuilder` 的接收器并停止应用内回复追踪、清理来源索引和轮次重置接收器。验证时覆盖生命周期与原生/合成回复的混合场景；本次仅有构建和自动化证据。
- **媒体通知内联回复动作合并与去重**：用户在媒体通知输入期间到达同 ID 新通知并发送成功后，控制器回调对 latest 缓存清除 `pre-applied`/`applied` 标记并调用 `attachReplyAction` 重新发布。旧实现整体复制原 actions 数组再追加模块回复，导致累积多个重复回复动作。现改为在合并时过滤旧的可投递自由文本回复动作（`actionIntent != null` 且 `RemoteInput` 中包含 `allowFreeFormInput == true`），再加入当前 CarExtender 产生的新模块回复动作；非回复动作（大图缩放、标记已读、仅选择项/仅数据输入、无 Intent 动作及原有 null 元素）保持原对象、原顺序与相对位置；无 CarExtender/native reply 或非聊天 ticker 仍不修改原动作。诊断日志保持仅记录 `notificationId`、`resultKey` 与 `existingActions` 元数据。
- **已重建通知动作修改与序列化标记失效**：`LocalDecorator.setActions(Notification, Action...)` 修改动作时显式清除 `nevo.actionsSerialized` 附加实例字段（置为 null）。避免已由 `recoverBuilder()` 重建的通知后续修改动作后，因残留序列化标记导致 MainHook 跳过必要的 actions 序列化/RemoteViews 重建链路。动作去重仅在既有聊天 ticker、CarExtender 会话和原生回复 PendingIntent 资格满足时生效；版本相关旁路沿用原画像选择和完整运行时签名校验：8.0.72/3085、8.0.78/3180 的通知回复候选仍以版本名或版本号任一匹配（`||`）选择，8.0.77/3141、3160 仍须两者同时匹配（`&&`）。其他版本保留原候选、签名校验和保守回退，不因本次修复宣称新增支持；图片、应用内回复及撤回门禁分别保持不变。
- **输入期间的结果键竞态（仅记录）**：同 ID 新通知改变原生结果键、代理已更新为新键，而 SystemUI 仍按旧键立即发送时，接收器可能以 `native_drop reason=missing_input` 退出。尚无已适配微信版本触发该条件的实机证据；本次发布依用户要求只记录风险，不修改键映射或发送规则。条件、AOSP 依据和 UI 验证边界见[输入生命周期与竞态记录](notification-reply-input-lifecycle.md)。

### 3.7 8.0.77 同名不同构建的完整画像与验证

当前真机是 3141，用户提供 APK 是 3160；两种构建独立匹配 `versionName=8.0.77 && versionCode=<code>`。
通知回复、图片、应用内发送与撤回都按自己的完整签名解析，不能用“一个功能 ready”代表其他功能已启用。

| 目标 | 3141 | 3160 |
| --- | --- | --- |
| 通知回复 gates（static boolean） | `bs1.a.e()/g()/c()` | `lq1.a.e()/h()/c()` |
| 状态/简单消息 | `ld5.z.d(String): Object`、`gp3.f`（继承 `gp3.g.d:int`） | `pb5.z.d(String): Object`、`mn3.f`（继承 `mn3.g.d:int`） |
| 流程 AFTER hook | `db0.m.l(ld5.z): od5.b`、`handleDataFromRemote(ld5.z,j51.e): od5.b`、`handleDataFromFile(ld5.z,ra0.d): od5.b` | `n90.m.l(pb5.z): sb5.b`、`handleDataFromRemote(pb5.z,q31.e): sb5.b`、`handleDataFromFile(pb5.z,b90.d): sb5.b` |
| 消息类/会话/发送方向/服务端 ID | `storage.e9`；`N0(): String`、`C0(): int`、`J0(): long` | `storage.e9`；`Q0(): String`、`A0(): int`、`K0(): long` |
| 路径 AFTER hook | `x51.l0.d3(storage.e9,String,boolean): String` | `e41.l0.N2(storage.e9,String,boolean): String` |
| 大图 core/query | `yp0.k1.v(): yp0.c0` static；`qd5.k0.f(String,String[],int): Cursor` | `ho0.j1.v(): ho0.b0` static；`ub5.k0.f(String,String[],int): Cursor` |
| 下载服务完整方法 | `x51.j.b(long,MsgIdTalker,int,Object,int,pa0.w,int,boolean): int` | `e41.j.b(long,MsgIdTalker,int,Object,int,z80.w,int,boolean): int` |
| 本人状态 AFTER hook | `storage.e9.r1(int): void`，本类 override；状态 `M0(): int`；排除 `a3/M2/F2(): boolean` | `storage.e9.u1(int): void`，本类 override；状态 `P0(): int`；排除 `Z2/L2/E2(): boolean` |
| 撤回 payload | `RevokeMsgEvent.g: en.gs`、`en.gs.c: storage.e9` | `RevokeMsgEvent.g: fm.fs`、`fm.fs.c: storage.e9` |

两版均保留 `getString/getLong/getInteger(int)`、消息 ID/时间/类型与 `field_msgSvrId:long` 完整校验，
simplemsginfo 字段偏移一致；VFS 为 `vfs.a7.i(String,boolean): String` static，
WXGF 为 `MMWXGFJNI.wxam2PicBuf(byte[],int,int): byte[]` static。
撤回沿用第 3.5 节的 `NotificationItem.a(Context):void` 与 `IEvent.e():boolean` BEFORE、
字段 `f/h/i/g/c` 类型及事件父类校验。下载服务 getter、数据库字段/schema、两份来源哈希及反编译位置
完整记录在 [8.0.77 findings](wechat-8.0.77-findings.md)。

作用进程与回调时机沿用基线；预览开关关闭时跳过图片 hook，大图开关额外控制下载提交。
应用内回复/撤回不依赖预览开关。未知构建或签名失败分别关闭对应组件，保留现有回退。
图片服务获取始终使用已选画像，后续调用不再硬编码另一版本类名。

3141 设备检查结果：通知回复 `profile_ready usable=true`、`app_reply_ready`、`recall_ready`、
`events_ready revision=image-events-10`、`large_ready revision=image-large-2`。
新增 opt-in 仪器参数 `verify_wechat_profiles=true` 校验已安装微信的 40 项描述符，
不初始化业务、不安装 hook、不查数据库、不发送消息；Manifest 仅查询 `com.tencent.mm`。
110 项单元测试、33 项模块框架仪器测试与 lint（0 errors）通过；同 release 证书安装与回拉哈希一致。
自动检查阶段按用户要求只做不发消息检查；同日用户随后反馈“测试基本通过”，并授权合并推送。
本轮整体验收记为基本通过（用户实机反馈）；实际送达、连续收图、下载开关/取消/超时、发送失败/轮次推进、
撤回精确裁剪及通知不复活的逐项覆盖仍待记录。3160 未做同构建真机检查。

### 3.8 8.0.79 / 3182 完整画像

唯一启用组合为名称 `8.0.79` **且**代码 `3182`，所有功能分别执行完整签名校验。
通知回复为 `lt1.a.c()/d()/b(): boolean`（static），接收器与 `z2.s1.b(Intent): Bundle` 不变；
错误组合不进入旧版名称或版本码任选候选。
图片 state 为 `gh5.z.f(String): Object`，simple message 为 `lr3.f`，flow 为 `sb0.l`，
三项 AFTER hook 仍为 `l(state)` / `handleDataFromRemote(state, h61.e)` / `handleDataFromFile(state, gb0.d)`，
返回 `jh5.b`；路径 hook 为 `v61.l0.I3(storage.e9, String, boolean): String`，VFS 为 `z6.i(String, boolean)`。
`WeChatImageProfile.stateGet` 记录 f，旧三组图片画像保留 d；生产与描述符检查共用画像字段。
普通大图为 `pq0.i1.x(): pq0.a0`、唯一 `lh5.k0` 数据库字段、`kj5.n0.c(Class): kj5.m`、
`db0.e.Bj(): eb0.x` 和 `v61.j.b(long, MsgIdTalker, int, Object, int, eb0.w, int, boolean): int`。
仍按图片总开关安装、按大图开关请求；未知版本/签名不符分别关闭对应组件。

应用内回复为 `storage.e9.r1(int): void` AFTER、`C0/M0/N0` 和 `a3/M2/F2` 排除条件。
撤回为 `RevokeMsgEvent.g: en.js`、`en.js.c: storage.e9`、`I0(): long` / `N0(): String`；
沿用 `IEvent.e(): boolean` 与 `NotificationItem.a(Context): void` BEFORE，字段 f/h/i 类型不变。
作用进程仍为微信主进程；这两项不受图片开关影响。通知 ID 与撤回 ID 都源于同一 I0 服务端 ID，
缺少精确 ID 时仍保留未知消息。没有新增 SystemUI hook 或放宽既有 framework 校验。

APK 哈希、原始完整描述符、反编译文件与行号、各阶段验证、日志及待验收场景统一记录于
[8.0.79 findings](wechat-8.0.79-findings.md)。日志标签沿用 `profile_ready`、`events_ready`、
`large_ready`、`app_reply_ready`、`recall_ready`；ready 只证明安装，不代表实际消息通过。

2026-10-16：138 项主机测试、40 项已安装微信描述符检查和 46/46 framework 用例通过；
Release lint 0 errors / 34 warnings，签名/对齐/安装后回拉哈希核对通过。
LSPosed 现场发现微信作用域未勾选，补选并重启微信后五条链路 ready；最终安装 Release 3.2.1/code 14。
未重启 SystemUI 或整机，未发送真实消息；实际回复、收图/下载、轮次、撤回与展示仍待消息验收。

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

以下为最初 8.0.72/3085 的锚点；当前各版本使用独立 `WeChatImageProfile`，验证状态见第 3.2/3.7/3.8 节。
新版本按以下顺序核对（`WeChatImageEvents.install`），不能直接复制旧混淆名：

1. `v65.z.d(String): Object`（8.0.79 对应 `gh5.z.f(String): Object`）、`yh3.f` 的 `d` 字段与 `getString/getLong/getInteger`。
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
| 媒体输入期间更新与回复 | 重复 attach、同 ID 第二条消息后发送、已有多个回复及其他动作、标记清除后 recoverBuilder/Parcel | 第二条实际正文保留，原生转发一次，仅一个有效回复；其他动作保持，重建及序列化后仍为一个回复 |
| 动画表情媒体分支判定 | `[动画表情]` / `[2条][动画表情]` 媒体通知，多次 apply 与序列化标记失效 | 稳定返回 `Unprocessed`；正文与模板保持不变，序列化标记被清除，保持单一有效回复动作且非回复动作保留 |
| 动画表情更新后回复投递 | 第一条普通消息到达并挂载回复，第二条动画表情同 ID 更新后通过原代理发送回复 | 原代理保持有效并成功派发至最新 native target，first count=0、latest count=1，输入历史仅记入最新通知，正文与单一回复保持 |
| 输入期间的 SystemUI 状态（真机待验收） | 在回复框输入中文/emoji，收到同 ID 动画表情消息后继续编辑并发送 | 草稿保留、输入法可用、投递一次且会话正确；旧代理派发测试不代替此 UI 验收，另检查结果键或回复入口变化时的行为 |

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
- 图片事件及普通大图覆盖 8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.79/3182；未映射组合自动关闭。3141 hook 与描述符检查通过，实际收图/下载和 3160 真机待验收；3182 分层结果见 8.0.79 findings。
- 应用内回复轮次映射 8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.78/3180、8.0.79/3182；3141 hook 就绪，实际发送与轮次推进待验收；其他组合保留原轮次行为。
- 撤回身份/事件映射上述五组精确版本；3141 hook 就绪，3182 分层验证见对应 findings。实际撤回裁剪、清除后不复活及图片回退待真实消息验收。新合成回退、SystemUI 移除原因和生命周期行为同样不能借用历史原生回复验收。
- 2026-09-30 项目计划 1/2/3 已通过本地 106 项单元测试、Debug/仪器 APK 构建和 lint（0 errors）。同日按用户授权安装后，PJZ110 / Android 16 上 33 项模块 framework 仪器用例全部通过；修复普通进程缺少 Xposed API 的合成回退，测试使用仅作用于自身进程的隐藏 API 参数。两包签名及安装后回拉哈希均核对通过，微信/SystemUI 未重启。验证记录见[项目修改计划](project-modification-plan.md)；真实微信 hook 与通知行为仍未验收。
- 2026-10-06 媒体通知重复回复去重与 `nevo.actionsSerialized` 失效：用户已在真机复现原输入期间新消息导致重复回复按钮的现象。本次完成源码修复并通过 110 项本地单元测试、Debug/仪器 APK 构建及 lint（0 errors / 34 warnings）；两个 APK 证书与现有 release 密钥一致。新增仪器用例仅通过编译，本轮未在设备运行；修复后的真机 UI 流程及微信运行时行为尚未验收。计划及独立验收证据保存在本地 `.debug-artifacts/reply-action-dedup/`，未纳入 Git。
- 2026-10-06 动画表情测试计划新增用例（`animatedStickerMediaBranch`、`animatedStickerReplyAfterUpdate`）：2026-10-07 完成 Debug/仪器 APK 构建、新增用例编译及 lint（0 errors / 34 warnings），两包 release 证书核对一致；仪器 runner 共注册 37 项，本轮没有在设备运行。生产及单测源码未变，110 项单元测试通过结果由 Gradle 复用。旧代理派发测试覆盖稳定结果键下的模块控制器与广播转发链路，不代表真实 SystemUI 的草稿文本或输入焦点已被保留；结果键变化、回复入口变化及厂商布局重建仍须分别检查，真机交互与微信实际行为待验收。本地计划、执行及输入链路分析保存在 `.debug-artifacts/animated-sticker-tests/`。
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
- 新增画像应使用版本名与版本码 `&&` 精确门禁。8.0.77 的 3141/3160 和 8.0.79/3182 已按此规则实现；不得直接复制上面的历史 `||` 模板或探针中的候选映射行。
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
