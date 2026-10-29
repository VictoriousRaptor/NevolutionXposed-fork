# 微信通知外观

## 2026-10-30 图片显示诊断与修复

Android 17 真机上，微信 8.0.79/3182 的一张不同图片已完成事件解析、解码和补发，通知中有 `nevo.wechat.imageReady=true`，SystemUI 的展开视图 AFTER hook 也已进入且归属检查通过，但用户展开后仍看不到图。通知管理器保存的记录为 `android.picture=null`、`android.pictureIcon` 为 URI 类型；原渲染器只读取 `EXTRA_PICTURE`，所以只克隆并清理旧视图，没有添加新图片。框架回归以仅有 `EXTRA_PICTURE_ICON` 的合成通知复现了此缺口（76/77，通过项中不含新增用例）。

现展开渲染优先使用 `EXTRA_PICTURE` 位图；位图被系统转存后，改用 `EXTRA_PICTURE_ICON` 通过 `RemoteViews.setImageViewIcon` 添加图片。重建新补图时清除可能继承的旧图标，撤回或失效时同时清除两个字段。缺少两个字段、模块资源构造失败或系统视图应用失败时，仍保留消息模板的 `[图片]`。该改动没有新增 hook 目标或版本映射；图片请求仍需 8.0.79 与 3182 同时匹配、原开关开启并通过运行时签名校验。Debug 的 `image_render_entry` 只记录进程、归属及两种图片字段是否存在，不记录 URI、路径或正文。

后续私聊截图显示图片贴在卡片左缘，而同卡片消息文字从头像右侧开始。设备 framework 的 `notification_2025_content_margin_start` 为 58 dp，在该设备密度下对应截图中的约 203 px 差距。私聊和群聊 MessagingStyle 都通过 `withImagePreview()` 将同一个 `wechat_group_image_preview` 添加到系统 `notification_main_column`；该列本身未给新增子视图加入消息内容缩进。现在只在 framework 的 2025 展开会话/消息模板上读取其内容缩进并给图片视图设置对应的起始侧 padding，保留系统原布局、头像、文字和回复区。`ImagePreviewLoader` 的非 MessagingStyle 回退使用另一套 `wechat_image_preview`，标题与图片原本同列，不应用这次缩进。无匹配模板或缩进资源时沿用原视图行为。合成 framework 回归已添加私聊、群聊和回退模板检查；设备拔除后无法运行新增回归或确认真实通知最终对齐。

修复包的主机测试 219/219、Android 17 framework 测试 77/77 通过；测试包在 `finally` 阶段卸载并核验不存在。最终 Debug APK 已在手机原地覆盖，手机与本地 APK SHA-256 相同；用户手动重启 SystemUI 后，同一私聊的新图片通知中看到了缩略图。同期实时日志为 `published`、`image_render_entry ... owned=true bitmap=false pictureIcon=true`、`image_render_result ... viewChanged=true`，支持该图片经 URI 图标路径显示的结论。此项视觉验收只覆盖这次私聊图片；群聊、撤回后的视图、其他系统版本仍未验证。日志、APK 和原通知只留本地忽略目录 `.debug-artifacts/live-diagnosis-20261030/`。

## 当前行为

- 私聊和群聊的 MessagingStyle 使用微信原通知大图标，由系统 ConversationLayout 提供通知主头像和微信应用角标；群聊使用原群头像，不写入成员 Person。首次及后续文本通知成功重建为 MessagingStyle 后，都清除微信原通知携带的折叠、展开及横幅自定义视图，避免首条消息仍显示微信的联系人列表式卡片。重要会话类型保留，已确认 bot 或无原头像时保留系统回退。
- 私聊和群聊图片补图后都继续用 MessagingStyle，联系人/群名、群成员、消息文字沿用对应文本消息的系统排版，仅追加高 160 dp、完整等比左上对齐的 fitStart 图片。消息身份、回复动作、静默更新、token 和失效清理保持原规则。
- 折叠通知隐藏“微信”应用名及存在的分隔符；展开通知由系统正常显示应用名，不另外改写联系人/群名与成员标题。链接等媒体卡片成功在微信原生通知上拼接回复时保留微信原模板；未拼接时使用消息会话模板和原头像角标。
- 移除原应用名隐藏设置、文案、运行时开关及同步项。schema 11 在本地初始化和本地/远端同步时清除旧键 `WeChatDecorator.hide_expanded_app_name`。旧键只为迁移清理保留，其余设置及通用远端选择保护不变。

## 渲染接口与门禁

仅在既有微信主进程和 SystemUI 作用域，对实际微信 Context、带模块外观标记 `nevo.wechat.appearance=com.tencent.mm` 且非汇总的通知处理。标记不作为消息身份、回复资格或已读证据。受微信增强总开关控制，不新增设置。

头像 BEFORE hook 为 `Notification$MessagingStyle.makeMessagingView(int): RemoteViews`。完整校验 `Style.mBuilder: Notification.Builder`、`Builder.mContext: Context`、`Builder.mN: Notification`、可写实例字段 `mConversationType: int` / `mShortcutIcon: Icon`，以及 legacy=0 / normal=1 的会话类型常量。有原头像的非 bot MessagingStyle 使用原图标；legacy 切换普通会话类型，重要会话类型保留。

折叠 AFTER hook 为 `Notification$Builder.createContentView(): RemoteViews`，要求零参数、实例方法、准确返回类型及 `mContext`/`mN` 实例字段。仅在折叠视图克隆上隐藏系统 `app_name_text` 及可用分隔符；不存在对应资源 ID 时保留原视图。展开视图不改应用头。

展开 AFTER hook 优先 `Notification$Builder.createExpandedContentView(): RemoteViews`，方法缺失才使用 `createBigContentView(): RemoteViews`，要求零参数、实例方法和准确返回类型。对私聊/群聊 MessagingStyle 克隆最终 RemoteViews，按完整实例签名 `RemoteViews.removeFromParent(int): void` 反射登记旧模块图片移除动作；有 READY 且 `EXTRA_PICTURE` 位图或 `EXTRA_PICTURE_ICON` 图标可用时向 `android:id/notification_main_column` 追加 `wechat_group_image_preview`。两者都没有时只清旧图，防止重复 reapply 和撤回残留。不修改展开应用名或内容标题。

模块图片布局使用 libxposed 模块 ApplicationInfo 与完整反射构造 `RemoteViews(ApplicationInfo, int)`，不回退宿主包查询。缺系统容器 ID、移除接口签名不符或资源构造失败时，直接保留原消息模板中的 `[图片]`，不尝试替代接口或其他图片模板。头像、折叠和展开入口独立校验、去优化、安装和回退。日志使用 `WeChat.Appearance` 的 avatar/collapsed/header ready、unavailable 以及 avatar/collapsed_header/message_image 异常类型，不记录通知正文、头像、联系人或设备标识。

framework 以运行时完整签名支持，不另设微信版本白名单。图片仍限定 8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.79/3182，版本名与版本码同时匹配，保留图片开关、等待时间和签名校验。回复、应用内回复与撤回各自门禁独立，未知版本保守回退不变。

## 验证边界

保留群头像与图片相关回归，包括模板/身份/发送者保留、ConversationLayout、四条消息受限高度测量、原模板克隆隔离、重复 reapply 与撤回清理；新增私聊首条文本携带原生内容视图、后续更新保持同一消息模板、原生媒体回复仍保留微信布局的合成检查。私聊补图保持消息模板与头像角标、折叠入口完整签名及媒体模板转换前清理原生内容视图的检查继续保留。旧设置本地/远端清理用例继续保留。

下节是真机框架回归的历史记录，早于本次折叠应用头及私聊图片改动；本次新代码的设备框架验证另记于下方。真实微信/OEM 的群图标、连续图文、通知栏交互及真实回复/撤回展示仍未验收。框架通过不代表实际 LSPosed hook 安装或视觉通过。

AOSP commit `94b4c163b7dfe5ce3607f7bb8456f9573f7de57d` 的 [群会话展开布局](https://github.com/aosp-mirror/platform_frameworks_base/blob/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/res/res/layout/notification_2025_template_expanded_conversation.xml) 与 [RemoteViews](https://github.com/aosp-mirror/platform_frameworks_base/blob/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/widget/RemoteViews.java) 支持静态结构核对，不能替代设备验收。原始日志、签名材料和 APK 仅存本地忽略目录 `.debug-artifacts/group-notification-20261027/`。

## 2026-10-27 应用头与消息头像设备框架回归

- Android 17 / API 37 设备上，69/69 项 framework instrumentation 通过；设备已安装微信 8.0.79/3182 的描述符检查 40 项通过。新增检查覆盖折叠应用名布局、私聊图片继续使用消息模板与原头像角标、原生媒体视图清理；既有群头像、群图标题/发送者、图片 reapply 与撤回检查继续通过。
- 测试 APK 在 `finally` 阶段卸载，独立查询确认测试包与 instrumentation runner 均不存在。测试前备份和测试后回拉的模块 APK 哈希均与本地 Release 相同，最终安装版本非 debuggable。原始设备输出、备份和结果只存放在忽略目录 `.debug-artifacts/notification-style-device-20261027/`。
- 本轮使用 `--no-hidden-api-checks` 执行合成 framework 测试；未制造真实微信私聊/群聊消息，未检查 LSPosed 在微信/SystemUI 中的实际 hook 安装或 ColorOS 通知栏视觉。没有重启整机。

## 2026-10-27 本次真机验证与提交

- 使用现有 release 密钥及 `-PrequireReleaseSigning=true` 离线完成 `testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease`：213 项主机测试全部通过，失败、错误、跳过均为 0；Release 的 lintVitalRelease 通过，三包签名及 zipalign 检查通过，证书匹配既有 release 密钥。
- Release Lint 首次指出 `RemoteViews.removeFromParent(int)` 为受限隐藏接口。仅在既有可选 Xposed 渲染方法局部声明 BlockedPrivateApi，保留完整签名校验及异常回退，不放宽设备隐藏 API 策略；所需接口或图片布局失败直接保留原消息模板的 `[图片]`，不尝试替代接口/其他图片模板。
- Android 17 / API 37 真机最终通过 68/68 项框架 instrumentation；微信安装包描述符检查通过 40 项。群头像选择、群聊图片模板/标题/发送者保留、受限高度、reapply 不重复、撤回清理及旧设置双端清理均包含在框架用例中。
- 首轮为 67/68，唯一失败是旧测试将系统生成的普通标题固定断言为纯会话名。核对 framework 的 MessagingStyle 标题合成后，测试改为验证补图前后的实际原始标题保持一致；未因此改变通知标题业务逻辑。最终完整重跑全部通过。
- 两轮均在 finally 清理阶段卸载精确测试包并核验 package/runner 均不存在，再用最终 Release 覆盖临时 Debug。最终安装的 Release 为 3.4.0 / 17，回拉 APK SHA-256 与本地最终 Release 一致，包不含 DEBUGGABLE。
- instrumentation 使用 `--no-hidden-api-checks`，结果证明该测试上下文中的 framework 行为；不证明微信/SystemUI 进程的 LSPosed hook 安装、真实消息收发、图片下载或实际 OEM 通知视觉。未发送真实消息，未主动重启整机、微信或 SystemUI，未执行完整 Lint。
- 原始日志、设备标识、回滚 APK、三包副本、证书和哈希核验仅保存在忽略目录 `.debug-artifacts/group-notification-20261027/`；不加入提交或公开附件。
