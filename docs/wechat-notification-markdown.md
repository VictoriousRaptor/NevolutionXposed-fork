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

最终样式受系统通知模板控制。[AOSP `Notification.MessagingStyle.Message`](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/Notification.java) 在 `Flags.cleanUpSpansAndNewLines()` 分支下调用 `stripNonStyleSpans`，会移除字体和背景 span；[颜色清理逻辑](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/com/android/internal/util/ContrastColorUtil.java) 也可能移除背景颜色。支持两种 span 时显示灰底等宽文字；仅背景被过滤时仍可保留等宽字体；两者都被过滤时只能显示去除标记后的普通代码文字。本功能不新增 SystemUI hook，不能保证所有 Android/OEM 版本显示灰底或等宽字体。

单条消息最多解析 4096 个 UTF-16 字符、128 个样式范围、12 层嵌套及 65536 步工作量；任一上限触发时整条保留原文，不输出部分删过标记的消息。运行时解析异常也回退原文。emoji 的样式范围使用 Android 的 UTF-16 下标；删除配对标记及围栏头尾行，保留正文和已有文本 span。

## 原文与展示链路

- `InlineMarkdown` 为无 Android 依赖的有界解析器，不引入新依赖。
- `NotificationMarkdown` 将样式转换为可跨进程序列化的 Android `StyleSpan`、`StrikethroughSpan`、`TypefaceSpan` 与 `BackgroundColorSpan`；在消息合并、轮次裁剪、撤回与图片请求之后处理 `EXTRA_TEXT` 和 `EXTRA_MESSAGES`。Parcel 保留 span 不代表 SystemUI 模板一定显示它们。
- 发生展示转换时，原始文字保存为 `nevo.markdown.rawText`，消息原文保存在消息 bundle 的 `extras` 中。这里的原文是既有前缀剥离、表情转换之后、Markdown 解析之前的内容。
- `NotificationMessages.incoming/read` 优先读取保留的原文；正文去重和图片身份仍使用未去除 Markdown 标记的消息。读取消息时移除展示元数据，避免上一轮样式参与下一次解析。
- `apply` 对需转换的消息 bundle 及其 `extras` 创建副本，不修改传入的消息对象及其身份元数据；通知顶层 `extras` 更新展示文字和原文标记。已处理通知重复解析不会再次丢失原文；关闭开关后的正常重建恢复原文。
- RemoteInput 投递、通知本人回复记录和 `EXTRA_REMOTE_INPUT_HISTORY` 不做 Markdown 转换；通知里的本人回复消息仅在展示时转换。标题、会话身份、ticker 和媒体专用早退路径保持现有处理。
- 图片预览使用现有 builder 和身份复制路径，复制时额外保留原文。切换图片/消息样式和系统 `recoverBuilder` 的完整行为仍需 framework 测试及真机验收。

该功能不新增 hook，不读取微信数据库，不改变混淆类或版本画像。8.0.72、8.0.77、8.0.78 和 legacy 通知重建路径均可使用；回复、图片、应用内回复、撤回各自的版本匹配和运行时签名门禁保持独立，见[适配指南](wechat-version-adaptation-playbook.md)。

## 验证与回退

主机单元测试覆盖解析、样式下标、嵌套、转义、代码、残缺/不支持语法、长度/样式/工作量限制、2000 组随机输入以及设置 schema 4→5 迁移。

新增六项 framework 仪器用例并加入现有 runner：强调 span 的 Parcel 往返和已有样式保留；重复重建与关闭；原文身份/回复/撤回；builder 与图片预览的原文保留；行内/围栏代码的字体、灰底及 emoji 下标在 Parcel 中保留；代码原文与回复在重复解析、重建、Parcel 及关闭开关后保留。编译仪器 APK 不代表这些用例已经运行。

2026-10-12 本地验收：使用现有本地签名配置与 `-PrequireReleaseSigning=true`，离线执行 `testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintRelease --console=plain --offline --no-daemon` 成功。134 项单元测试全部通过（其中解析器 22 项、设置同步 11 项），无失败、错误或跳过；Release lint 为 0 errors、34 warnings，问题编号/级别/描述与既有 2026-10-06 Debug 报告一致。六项新增 framework 用例已编译，尚未运行。

Release APK：包名 `com.oasisfeng.nevo.xposed`，版本保留 `3.1.4` / code `12`，`debuggable=false`，大小 3,103,374 字节，SHA-256 为 `609f30834b3d1b5a8e216e5da6e714c084822129965bf62b3f8a824416fd9a29`。`apksigner verify --verbose --print-certs` 通过；Release、Debug 和仪器 APK 的证书 SHA-256 都与本次从既有 release 密钥导出的公开证书一致：`7a707a90a36c94d231678813514d2ce815ad81fbf79558edc67eab4f288c4df9`。构建与签名报告、校验过的交付副本位于本地忽略目录 `.debug-artifacts/markdown/`，不提交密钥或密码。

未授权安装或设备操作；真实微信 hook、RemoteInput 送达及折叠/展开/锁屏/悬浮通知中的实际字体、灰底与深浅色主题展示尚未验证。系统可能清理或覆盖样式，不能以 APK 构建成功推断视觉通过。

回退方式：关闭“通知内渲染 Markdown”，重启微信进程并收到新通知。不会主动重发既有系统通知；APK 可用原 release 密钥签名版本覆盖安装回退。
