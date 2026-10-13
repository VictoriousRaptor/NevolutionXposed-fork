# 微信通知 Markdown 展示

2026-10-12：新增“通知内渲染 Markdown”设置，默认关闭，依赖微信通知增强。修改后需重启微信进程。该功能只改变通知消息的显示，不发送转换后的文字。

## 支持范围

| 输入 | 显示 |
| --- | --- |
| `**加粗**` | 原生加粗样式 |
| `*斜体*` | 原生斜体样式 |
| `~~删除线~~` | 原生删除线样式 |
| `***加粗斜体***` | 同时应用加粗与斜体 |
| `**加粗和 *斜体* 一起**` | 有界嵌套样式 |
| 单反引号包围的代码 | 去除反引号，代码文字使用等宽字体和灰色底纹 |
| 两个或更多等长反引号包围的行内代码 | 允许代码内容包含较短或不同长度的反引号 |
| 独立行的三个或更多反引号围栏 | 去除围栏和语言标签，保留正文缩进和换行，应用代码样式 |

这是有界 Markdown 子集，不是完整 CommonMark 渲染器。不实现下划线语法、标题、表格、链接点击、图片、HTML 或语法高亮。代码内容不再解析加粗、斜体、删除线或反斜杠；行内代码保留空白和换行，不执行 CommonMark 的空白归一化。反斜杠转义保留原样，转义的反引号串不会被再次解析。未闭合的反引号保护其后全文；空围栏保留原样。

围栏起止行允许至多三个前导空格；开头语言标签不可包含反引号，结束围栏必须独占一行，长度不短于开始围栏，尾部只允许空格或制表符。代码正文及最后一行的换行保留，结束围栏行连同其换行移除。加粗、斜体和删除线不跨行配对；残缺标记、空白相邻的起止标记和四个以上连续星号保留原样。其它语法的结构标记保留，但普通文本中的受支持行内语法仍可渲染。

代码样式使用原生 [`TypefaceSpan("monospace")`](https://developer.android.com/reference/android/text/style/TypefaceSpan) 和 [`BackgroundColorSpan`](https://developer.android.com/reference/android/text/style/BackgroundColorSpan)。背景为半透明中性灰色 `0x30808080`，保留系统文字颜色，适应明暗背景。它是文字范围的底纹，不是铺满通知宽度、带圆角和内边距的网页代码面板。

最终样式受系统通知模板控制。[AOSP `Notification.MessagingStyle.Message`](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/Notification.java) 在 `Flags.cleanUpSpansAndNewLines()` 分支下调用 `stripNonStyleSpans`，会移除字体和背景 span；[颜色清理逻辑](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/com/android/internal/util/ContrastColorUtil.java) 也可能移除背景颜色。2026-10-13 已从 PJZ110 Android 16 / ColorOS 16.0.10 的实际 framework 确认清理开关默认返回 true，白名单不包含这两种代码 span。字体服务中 monospace 指向 `DroidSansMono.ttf`，sans-serif 指向 `Roboto-Regular.ttf`，均存在。原 APK 的反引号已解析，但代码字体和底纹在通知重建时会丢失。

现新增 `NotificationMarkdownHooks`，在微信主进程及 SystemUI 的消息清理方法完成后，仅恢复本模块代码范围中的字体和灰底。原方法继续执行；其他被清理的 span 不恢复。需完整签名校验、两个功能开关开启、消息带 `nevo.markdown.owner=com.tencent.mm` 和原文标记，且清理后的文字与解析结果完全一致；不一致、超限、缺少标记、没有代码或 hook 不可用时保留原生输出。已存在的代码 span 不重复添加，文字和身份元数据不改变。Android/OEM 其他清理路径仍需独立验证，完整 hook、日志与失败语义见[适配指南](wechat-version-adaptation-playbook.md)。

单条消息最多解析 4096 个 UTF-16 字符、128 个样式范围、12 层嵌套及 65536 步工作量；任一上限触发时整条保留原文，不输出部分删过标记的消息。运行时解析异常也回退原文。emoji 的样式范围使用 Android 的 UTF-16 下标；删除配对标记及围栏头尾行，保留正文和已有文本 span。

## 原文与展示链路

- `InlineMarkdown` 为无 Android 依赖的有界解析器，不引入新依赖。
- `NotificationMarkdown` 将样式转换为可跨进程序列化的 Android `StyleSpan`、`StrikethroughSpan`、`TypefaceSpan` 与 `BackgroundColorSpan`；在消息合并、轮次裁剪、撤回与图片请求之后处理 `EXTRA_TEXT` 和 `EXTRA_MESSAGES`。Parcel 保留 span 不代表 SystemUI 模板一定显示它们。
- 发生展示转换时，原始文字保存为 `nevo.markdown.rawText`，消息原文保存在消息 bundle 的 `extras` 中，另保存 `nevo.markdown.owner=com.tencent.mm`。这里的原文是既有前缀剥离、表情转换之后、Markdown 解析之前的内容。
- `NotificationMessages.incoming/read` 优先读取保留的原文；正文去重和图片身份仍使用未去除 Markdown 标记的消息。读取消息时移除展示元数据，避免上一轮样式参与下一次解析。
- `apply` 对需转换的消息 bundle 及其 `extras` 创建副本，不修改传入的消息对象及其身份元数据；通知顶层 `extras` 更新展示文字和原文标记。已处理通知重复解析不会再次丢失原文；关闭开关后的正常重建恢复原文。
- RemoteInput 投递、通知本人回复记录和 `EXTRA_REMOTE_INPUT_HISTORY` 不做 Markdown 转换；通知里的本人回复消息仅在展示时转换。标题、会话身份、ticker 和媒体专用早退路径保持现有处理。
- 图片预览使用现有 builder 和身份复制路径，复制时额外保留原文。切换图片/消息样式和系统 `recoverBuilder` 的完整行为仍需 framework 测试及真机验收。

该功能新增一处 framework AFTER hook，不读取微信数据库，不改变混淆类或微信版本画像。8.0.72、8.0.77、8.0.78 和 legacy 通知重建路径均沿用原有门禁；回复、图片、应用内回复、撤回各自的版本匹配和运行时签名门禁保持独立，见[适配指南](wechat-version-adaptation-playbook.md)。

## 验证与回退

2026-10-13 修复新增三项 framework 用例：调用实际消息清理方法，再恢复代码范围并做三轮 native bundle/Parcel 往返；缺少标记、owner 不符、正文已改变、没有代码或解析超限时保持原生结果；只补缺失的背景且不重复添加已有等宽字体。既有关闭开关用例额外检查 owner 标记也被移除。现有 runner 共注册 46 项 framework 用例，已在连接的 PJZ110 Android 16 手机上实际运行，46/46 通过。

本次修复已使用既有 release 密钥及 `-PrequireReleaseSigning=true` 完成 `testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintRelease --console=plain --offline --no-daemon`：134 项主机测试通过，无失败、错误或跳过；Release lint 为 0 errors、34 项原有 warnings，问题编号/级别/描述与基线一致。Release 包名 `com.oasisfeng.nevo.xposed`，版本仍为 3.1.4 / code 12，`debuggable=false`，3,106,150 字节，SHA-256 `52aee3a34e62da71936a197bf132dd71966878c51f989493c59fbefd24ce8f32`。Release、Debug 及测试 APK 的签名验证通过，证书均与现有 release 证书一致。交付及报告位于忽略目录 `.debug-artifacts/markdown-diagnosis-20261013/`，旧版本已留作本地回退副本。

经用户授权已覆盖安装修复版 Release 与测试 APK，并执行 `am instrument --no-hidden-api-checks -r -w com.oasisfeng.nevo.xposed.test/com.oasisfeng.nevo.decorators.wechat.MessageIdentityInstrumentation`。仅本次测试进程放行隐藏 API；结束记录为 `Identity instrumentation: 46/46 passed` 和 Android RESULT_OK。随后用户手动重启系统界面，并反馈“我看到界面生效了”。该项属于用户实操的界面确认；当前 logcat 缓冲区未保留到 `code_styles_hook_ready`/`code_styles_restored` 专用日志，不能额外声称已捕获两个进程的 hook 调用现场或完成所有明暗主题/通知形态的视觉回归。助手未执行原定进程重启命令，遵循用户后续只读日志指令。测试结束按用户要求卸载 `com.oasisfeng.nevo.xposed.test`，包列表确认测试包消失，修复版主模块保留。2026-10-12 的以下验收对应初版，不作为本次修复证据。

主机单元测试覆盖解析、样式下标、嵌套、转义、代码、残缺/不支持语法、长度/样式/工作量限制、2000 组随机输入以及设置 schema 4→5 迁移。

新增六项 framework 仪器用例并加入现有 runner：强调 span 的 Parcel 往返和已有样式保留；重复重建与关闭；原文身份/回复/撤回；builder 与图片预览的原文保留；行内/围栏代码的字体、灰底及 emoji 下标在 Parcel 中保留；代码原文与回复在重复解析、重建、Parcel 及关闭开关后保留。编译仪器 APK 不代表这些用例已经运行。

2026-10-12 本地验收：使用现有本地签名配置与 `-PrequireReleaseSigning=true`，离线执行 `testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintRelease --console=plain --offline --no-daemon` 成功。134 项单元测试全部通过（其中解析器 22 项、设置同步 11 项），无失败、错误或跳过；Release lint 为 0 errors、34 warnings，问题编号/级别/描述与既有 2026-10-06 Debug 报告一致。六项新增 framework 用例已编译，尚未运行。

Release APK：包名 `com.oasisfeng.nevo.xposed`，版本保留 `3.1.4` / code `12`，`debuggable=false`，大小 3,103,374 字节，SHA-256 为 `609f30834b3d1b5a8e216e5da6e714c084822129965bf62b3f8a824416fd9a29`。`apksigner verify --verbose --print-certs` 通过；Release、Debug 和仪器 APK 的证书 SHA-256 都与本次从既有 release 密钥导出的公开证书一致：`7a707a90a36c94d231678813514d2ce815ad81fbf79558edc67eab4f288c4df9`。构建与签名报告、校验过的交付副本位于本地忽略目录 `.debug-artifacts/markdown/`，不提交密钥或密码。

初版仅完成构建与签名验收。修复版的设备运行及用户界面反馈见上述 2026-10-13 记录；真实 RemoteInput 送达、全部折叠/展开/锁屏/悬浮通知形态和深浅色主题仍未逐项回归，其他 Android/OEM 的额外清理路径也未验证。

回退方式：关闭“通知内渲染 Markdown”，重启微信进程并收到新通知。不会主动重发既有系统通知；APK 可用原 release 密钥签名版本覆盖安装回退。
