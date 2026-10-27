# 微信通知外观

## 当前行为

- 私聊和群聊的 MessagingStyle 使用微信原通知大图标，由系统 ConversationLayout 提供通知主头像和微信应用角标；群聊使用原群头像，不写入成员 Person。重要会话类型保留，已确认 bot 或无原头像时保留系统回退。
- 私聊和群聊图片补图后都继续用 MessagingStyle，联系人/群名、群成员、消息文字沿用对应文本消息的系统排版，仅追加高 160 dp、完整等比左上对齐的 fitStart 图片。消息身份、回复动作、静默更新、token 和失效清理保持原规则。
- 折叠通知隐藏“微信”应用名及存在的分隔符；展开通知由系统正常显示应用名，不另外改写联系人/群名与成员标题。链接等媒体卡片成功在微信原生通知上拼接回复时保留微信原模板；未拼接时使用消息会话模板和原头像角标。
- 移除原应用名隐藏设置、文案、运行时开关及同步项。schema 11 在本地初始化和本地/远端同步时清除旧键 `WeChatDecorator.hide_expanded_app_name`。旧键只为迁移清理保留，其余设置及通用远端选择保护不变。

## 渲染接口与门禁

仅在既有微信主进程和 SystemUI 作用域，对实际微信 Context、带模块外观标记 `nevo.wechat.appearance=com.tencent.mm` 且非汇总的通知处理。标记不作为消息身份、回复资格或已读证据。受微信增强总开关控制，不新增设置。

头像 BEFORE hook 为 `Notification$MessagingStyle.makeMessagingView(int): RemoteViews`。完整校验 `Style.mBuilder: Notification.Builder`、`Builder.mContext: Context`、`Builder.mN: Notification`、可写实例字段 `mConversationType: int` / `mShortcutIcon: Icon`，以及 legacy=0 / normal=1 的会话类型常量。有原头像的非 bot MessagingStyle 使用原图标；legacy 切换普通会话类型，重要会话类型保留。

折叠 AFTER hook 为 `Notification$Builder.createContentView(): RemoteViews`，要求零参数、实例方法、准确返回类型及 `mContext`/`mN` 实例字段。仅在折叠视图克隆上隐藏系统 `app_name_text` 及可用分隔符；不存在对应资源 ID 时保留原视图。展开视图不改应用头。

展开 AFTER hook 优先 `Notification$Builder.createExpandedContentView(): RemoteViews`，方法缺失才使用 `createBigContentView(): RemoteViews`，要求零参数、实例方法和准确返回类型。对私聊/群聊 MessagingStyle 克隆最终 RemoteViews，按完整实例签名 `RemoteViews.removeFromParent(int): void` 反射登记旧模块图片移除动作；有 READY 与 EXTRA_PICTURE 时向 `android:id/notification_main_column` 追加 `wechat_group_image_preview`。无位图时只清旧图，防止重复 reapply 和撤回残留。不修改展开应用名或内容标题。

模块图片布局使用 libxposed 模块 ApplicationInfo 与完整反射构造 `RemoteViews(ApplicationInfo, int)`，不回退宿主包查询。缺系统容器 ID、移除接口签名不符或资源构造失败时，直接保留原消息模板中的 `[图片]`，不尝试替代接口或其他图片模板。头像、折叠和展开入口独立校验、去优化、安装和回退。日志使用 `WeChat.Appearance` 的 avatar/collapsed/header ready、unavailable 以及 avatar/collapsed_header/message_image 异常类型，不记录通知正文、头像、联系人或设备标识。

framework 以运行时完整签名支持，不另设微信版本白名单。图片仍限定 8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.79/3182，版本名与版本码同时匹配，保留图片开关、等待时间和签名校验。回复、应用内回复与撤回各自门禁独立，未知版本保守回退不变。

## 验证边界

保留群头像与图片相关回归，包括模板/身份/发送者保留、ConversationLayout、四条消息受限高度测量、原模板克隆隔离、重复 reapply 与撤回清理；新增私聊补图保持消息模板与头像角标、折叠入口完整签名及媒体模板转换前清理原生内容视图的检查。旧设置本地/远端清理用例继续保留。

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
