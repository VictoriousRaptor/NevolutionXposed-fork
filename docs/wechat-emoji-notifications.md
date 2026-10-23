# 通知内置表情映射与 Unicode 占位符调查

记录日期：2026-10-23。

## 本次改动

先补齐 `EmojiMap.MAP` 的 32 个缺失名称，随后新增默认关闭的“保留通知原生 Emoji”开关。映射参考 [WeKit 固定提交 `d5dc76e5` 的映射表](https://github.com/Ujhhgtg/WeKit/blob/d5dc76e5d1fa23ccb6f8220911399a6b93d4f771/app/src/main/java/dev/ujhhgtg/wekit/utils/strings/MessageTextUtils.kt)，现在覆盖该版本 `EMOJI_MAP` 的全部 108 个名称；已有同名项继续使用本项目原来的输出。

新增名称：勾引、嘿哈、机智、抠鼻、可怜、右哼哼、笑脸、生病、恐惧、失望、无语、吃瓜、加油、汗、天啊、Emm、社会社会、旺柴、好的、打脸、哇、翻白眼、666、让我看看、叹气、苦涩、合十、庆祝、礼物、福、烟花、爆竹。`Emm` 放在英文列，以符合现有翻译器按首字母选择字典的规则。

映射输出为近似 Unicode emoji，不保证与微信原图一致。为兼容最低 API 26，可怜、让我看看、叹气分别采用 😟、👀、😮；福和爆竹在 API 26/27 分别回退为 💰、🎉，API 28 起使用 🧧、🧨。字体及 OEM 展示仍需设备验证。`[裂开]` 不在该参考表内，本次不猜测新增。

`[表情]` / `[动画表情]` 的媒体提前返回保留；`EmojiMap` 原有的通用 `[表情]` 映射不等于真实表情资源恢复，也不改变该媒体分支。读取已有结构化消息时未统一转换的路径本次未调整。没有增加表情下载或图片展示。

## 保留通知原生 Emoji 开关

设置键为 `WeChatDecorator.preserve_emoji`，中英文设置界面均提供入口，依赖微信通知增强总开关。设置存储升级为 schema 7；新安装和升级默认关闭，不覆盖已保存的显式选择。远程同步保留本地明确选择的优先级。

首个适配组合仅为版本名 **8.0.79 且版本码 3182**。不按名称或代码单独命中，不复用回复、图片、撤回功能的其他版本画像。作用进程仅为 `com.tencent.mm` 主进程；在既有 `Application.onCreate(): void` AFTER 中取得 Context 后安装，与微信通知增强及新开关是否开启无关，以便之后读取最新设置；不在 SystemUI 或微信其他进程安装。

完整目标与回调：

| 目标 | 完整签名及属性 | 回调行为 |
| --- | --- | --- |
| 表情转换 | `com.tencent.mm.smiley.g.d(java.lang.String): java.lang.String`，实例、非 abstract | BEFORE 在通知构建作用域内、微信通知增强和新开关均开启时，直接返回第一个参数；原始 emoji、肤色、ZWJ、混合文字及 null 原样保留 |
| 通知构建作用域 | `com.tencent.mm.booter.notification.m0.a(com.tencent.mm.storage.e9, long, boolean, boolean, java.lang.String, int, java.lang.String, java.lang.String, android.content.Intent, boolean): void`，实例、非 abstract | BEFORE 增加当前线程的嵌套深度；AFTER 减少并在最外层退出时移除 ThreadLocal，即使原方法异常也清理 |

安装前使用 `getDeclaredMethod` 校验精确参数列表、返回值与实例/非 abstract 属性，不进行模糊候选搜索；对已核对的直接调用者 `m0.a(...)` 调用 libxposed `deoptimize(Executable): boolean`，避免其保留内联的转换代码。取消优化失败时不安装。两个 hook 均成功后才激活回调，部分安装失败时先置为无效，再移除已安装项；移除失败仍不会启用文本替换。

原生转换函数的其他调用者与其他线程继续执行微信原方法。回调每次读取远程设置，关闭任一开关后不再替换原方法结果；已经生成的通知及历史不作逆向恢复。首次安装包含该 hook 的 APK 后须让微信进程重新加载模块，设备操作仍需明确授权。实际设置同步、hook 安装和通知显示仍待真机验证。

诊断标签 `WeChat.Emoji`：`source=emoji_hook_ready version=8.0.79 code=3182` 只表示安装成功；`emoji_hook_unavailable reason=unsupported_version` 表示版本不匹配；其他不可用情况只记录异常类；部分清理失败记录 `emoji_unhook_failed`。不记录原始文本、联系人或消息身份数据。版本未知、签名不符、取消优化/安装失败或保护性回调失败时，保留微信原始转换。

回归须覆盖 `👌`、`😀`、`😊`、肤色和 ZWJ、中文混合文本、重复重建、单聊/群聊及联系人标题、同线程嵌套与异常退出、其他线程/其他调用者、总开关与子开关切换、未知版本和签名错误，以及真实贴纸占位不被猜测成字符；分别验证折叠、展开、锁屏、悬浮通知和回复/撤回链路。

## 微信会不会把 Unicode emoji 转成 `[表情]`

结论：在已核对的微信 8.0.79 / 3182 中存在明确路径。模块翻译器不会反向把原生 emoji 改成 `[表情]`，但这不能排除微信在模块收到通知之前已完成该转换。

来源为 ADB 从此前已安装微信取得的 APK；安装来源及完整 split 清单见 [8.0.79 适配记录](wechat-8.0.79-findings.md)。本次直接重新核对 base APK SHA-256：`4382254fbb256f37b7b430e6f3d984c512c603e46d39f9c323cd8a6fa75d8364`。base APK、split-4.apk 及原始数据保存在 `.debug-artifacts/device/20261016-005759/apks/`，未上传。

离线源码与资源证据：

1. 通知构造类 `com.tencent.mm.booter.notification.m0` 的既有处理链调用 `com.tencent.mm.smiley.g.b(): g` 与 `g.d(String): String`，再交给 `com.tencent.mm.pluginsdk.ui.span.c0.e(String): String`；转换后的内容用于通知正文及 ticker 构造。这一调用发生在模块的通知装饰前。
2. `g.d(String): String` 初始化本地化名称表，并用 `com.tencent.mm.smiley.p.a(CharSequence, int, int, b1): void` 扫描原文；名称表来自 `R.array.a1` 的旧 SoftBank 编码与 `R.array.a2/a3/a4` 的中、英、繁体名称。
3. 回调 `com.tencent.mm.smiley.p0.a(a1, int, int): boolean` 对 `b2` emoji 项，先以 `b2.a: g1` 的 `g1.d: int` 查询 SoftBank 关联，再查本地化名称；若名称为空，就附加 `R.string.a11`。base 资源实际确认 `R.string.a11 = 0x7f100411 = "[表情]"`。
4. 使用 JADX `--rename-flags none --no-res --comments-level debug` 补查 `com.tencent.mm.smiley.q0`。`q0.i(DataInputStream, k0, boolean): boolean` 展示自带旧格式资源的索引构造：将 `k.d/e` 码点转为 `b2`，将 `k.i` 作为图片标识，将非零 `k.f` 加入 SoftBank 对应表。`q0.j(): boolean` 优先加载运行时更新的 `color_emoji_new`，否则使用自带 `color_emoji`。

反编译位置：通知类位于 `.debug-artifacts/wechat-8.0.79/mapping-sources/sources/`；`smiley`、`R` 等依赖位于 `.debug-artifacts/wechat-8.0.79/sources/sources/`；定向补查、资源 dump 和解析脚本位于 `.debug-artifacts/emoji-source-investigation-20261023/`。新 hook 仅使用上文已列出的转换及通知构建入口；其余内部方法用于解释原始逻辑，不安装 hook。源码中增加了运行时签名校验，尚未在微信进程执行该校验。

## 自带资源的离线查表结果

使用 split-4.apk 的 `assets/color_emoji`（旧格式 version 1）和 base 的中文名称数组，按 `q0.i()` 与 `p0.a()` 的索引规则解析。原始 emoji 先作为码点项进入索引，再查名称；缺少项名时得到通用占位符。以下是离线结果，不是设备通知实测：

| 原始 emoji | 自带资源的转换结果 |
| --- | --- |
| 👌 `U+1F44C` | `[表情]`，虽有关联旧编码，但中文名称表无对应项 |
| 😀 `U+1F600` | `[表情]` |
| 🙂 `U+1F642` | `[表情]` |
| 🤔 `U+1F914` | `[表情]` |
| 😎 `U+1F60E` | `[表情]` |
| 😊 `U+1F60A` | `[开心]` |
| 😂 `U+1F602` | `[破涕为笑]` |
| 👍 `U+1F44D` | `[强]` |

这解释了两个不同问题：具体名称可以通过扩展映射改善；统一 `[表情]` 已丢失原始码点，单靠最终通知无法逆向恢复。在本项目中，正文只要包含 `[表情]` 即提前进入保留媒体通知的分支，可能同时阻止同一条混合文本中的其他表情名称转换。不能把这个占位符一律当成图片贴纸的证据。

运行时可以加载更新的表情数据，语言设置也会影响名称表。不能用自带索引结果断言当前设备、所有微信版本或所有 emoji 都按上表输出。尤其不能把本地模拟写成真实微信收发或 SystemUI 视觉验证。

当前 hook 在通知转换前保留输入，避免丢失原始字符，不需要另读消息正文或按内容关联消息，也不会将已经存在的 `[表情]` 猜成固定 emoji。WeKit 在消息入库时保存原文是另一种参考方案，本次未移植该链路。真实贴纸资源展示另见[全局计划第 7 项](project-modification-plan.md#7-通知中的真实表情包图片待办)。

## 验证边界

映射离线验证使用生产 `EmojiMap` / `EmojiTranslator` 和最小 Android 文本桩：API 26、27、28、35 各 43 项，共 172 项通过，覆盖新增名称、恢复英文别名、连续及混合文本、未知名称、原生肤色和 ZWJ、旧映射与版本回退。材料位于 `.debug-artifacts/emoji-mapping-20261023/`。

另将反编译的 `p0` 回调源码在最小桩环境编译执行，按真实资源建立名称关系，以合成的图片标识输入上表八个样例，8 项回调输出检查全部通过；这些桩替代 Android Context、索引匹配及其依赖，不代表完整微信函数或通知链路已运行。材料位于 `.debug-artifacts/emoji-source-investigation-20261023/callback-probe/`。

微信转换调查为离线源码、APK 资源、查表模拟及桩环境回调验证；未在微信进程中执行内部函数，未采集新消息或设备通知。本次未安装 APK、执行仪器测试或操作设备。

映射阶段的项目验证：148 项单元测试通过、两包构建与 release 证书核对通过；应用源码 Lint 成功，测试源码分析因长时间无进展排除，报告为 0 errors / 34 warnings，记录在 `.debug-artifacts/emoji-build-20261023/`。

新增 hook 后，使用 `-PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`，沿用排除 `lintAnalyzeDebugAndroidTest` / `lintAnalyzeDebugUnitTest` 的既有边界，离线构建成功。160 项单元测试通过；新增 11 项覆盖精确版本、完整实例签名、通知内原样返回、非通知调用不变、动态开关、部分安装未激活、嵌套/异常退出和跨线程隔离，另 1 项覆盖 schema 7 默认值和同步选择。应用源码 Lint 为 0 errors / 34 warnings；这不代表完整测试源码 Lint 通过。仪器 APK 仅构建，未执行。

Debug 与仪器 APK 均通过 APK v2 签名验证，证书与既有 release 证书一致。Debug APK 为 3.3.1 / 16、5,041,139 字节，SHA-256 `748a12d639bf02e37b5dd94ec162d35b118a9f8ebe89aa301cb35777993ce633`；仪器 APK 为 83,522 字节，SHA-256 `beba7cdd35b0189420ba3edad9c9d17b968b6f21c8002dc264e1bc677fcd1692`。新一轮构建和签名记录位于 `.debug-artifacts/emoji-hook-20261023/`。没有在设备安装或验证 hook、设置同步及视觉显示。
