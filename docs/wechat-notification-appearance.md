# 微信通知头像和展开应用头

## 行为

- 私聊文字等重建后的 `MessagingStyle` 通知使用微信原通知 `getLargeIcon()` 的头像，交给系统 `ConversationLayout` 提供头像和微信应用角标。已有重要会话类型保留；不创建快捷方式，不更改系统排名或通知渠道重要性。联系人名和消息身份、回复和撤回逻辑不变。
- 链接、文件、视频、表情等保留微信原模板的媒体通知继续使用原图标。图片预览仍采用现有自定义展开布局，保留原大图标和外观标记。
- 群聊保持现有群头像/成员身份规则，不套用私聊头像替换。未知会话沿用现有非群聊展示，确认后按实际类型呈现。原通知无头像时使用原系统回退，不合成头像。
- 设置页新增“隐藏展开通知的微信应用头”（`WeChatDecorator.hide_expanded_app_name`），默认开启。仅隐藏展开布局中的应用名及对应分隔符，保留联系人/群名、头像和应用角标、时间、展开按钮和操作按钮。关闭后在后续通知模板重建时恢复系统应用名；不会主动重发当前通知。
- 设置通过现有 libxposed `settings` 组同步，渲染时读取；首次装入新 hook 仍需要目标进程加载新版模块，本轮不操作设备或重启进程。开关关闭不影响私聊原生头像规则。

2026-10-27 审查修复：本地初始化不再提前补齐缺失布尔设置的默认值，避免首次同步时覆盖远端已保存的关闭选择。读取时仍默认隐藏应用头，本地显式选择优先，历史 schema 的必要迁移保持原规则；不新增设置来源状态或迁移版本。

## 渲染接口和作用域

`NotificationAppearanceHooks` 仅装入既有的微信主进程与 `com.android.systemui`，并检查 builder 实际 Context 的包名为 `com.tencent.mm`、通知包含模块外观标记 `nevo.wechat.appearance=com.tencent.mm`，排除汇总通知。原始消息 ticker 或模块存储的消息历史用于登记外观标记，标记不作为会话身份、回复资格或已读证据。

头像 BEFORE hook：`android.app.Notification$MessagingStyle.makeMessagingView(int): android.widget.RemoteViews`。完整检查 `Style.mBuilder: Notification.Builder`、`Builder.mContext: Context`、`Builder.mN: Notification`、可写实例字段 `mConversationType: int` / `mShortcutIcon: Icon`，以及 `CONVERSATION_TYPE_LEGACY=0` / `CONVERSATION_TYPE_NORMAL=1`。仅对非群聊、非已确认 bot 且原通知有头像的 MessagingStyle 生效；legacy 类型改为普通会话布局，重要会话类型保留，图标取原通知头像。

展开 AFTER hook：优先解析 `android.app.Notification$Builder.createExpandedContentView(): RemoteViews`（本地 AOSP Android 17 参考入口）；方法不存在时解析 `createBigContentView(): RemoteViews`。方法必须为实例方法、零参数且返回 RemoteViews。现代方法存在但签名不符时不混用旧方法。对最终结果克隆后追加 `app_name_text` 和可用的 `app_name_divider` / `app_name_text_divider` 隐藏动作，使动作排在会话数据绑定之后，并避免修改缓存模板造成开关无法还原。不删除整个通知 header 容器。

已知创建/渲染 ContentView 的 framework 方法在安装时去优化，防止目标被内联绕过。两个 hook 独立安装、独立回退；缺签名、字段类型不符、去优化或安装失败时保留对应系统显示，日志为 `WeChat.Appearance source=avatar_hook_unavailable` / `header_hook_unavailable`；安装成功为 `avatar_hook_ready` / `header_hook_ready`，回调异常为 `avatar` / `header`，仅含异常类型，不记录通知正文、头像、联系人或实际设备标识。

本次不改变微信版本画像；回复、图片、应用内回复和撤回仍各自遵守既有版本名/版本码匹配与完整签名门禁。framework 支持以运行时完整签名为准，不把同名方法或某一微信版本适配当作所有 Android/OEM 支持证据。

## 验证边界

主机回归覆盖新旧展开入口、错误返回类型/静态方法拒绝、字段类型和可写性、会话类型常量、所有者双重限制、开关默认开启和显式关闭选择保留。framework 回归入口覆盖原头像/重要会话/群聊/bot/无头像、汇总与其他包隔离、图片预览元数据、展开模板克隆及原模板恢复。

本轮禁止安装 APK、运行设备测试或重启进程。framework 回归仅编译，不能当作已在设备执行；构建和签名结果单独记录，不证明 LSPosed hook 已安装或真实微信/ColorOS 视觉通过。需要后续另行授权验收：私聊文字/链接/图片、群聊文字/媒体、折叠/展开、头像角标、开关开启/关闭、通知回复及撤回后的重建。

系统布局依据：[AOSP ConversationLayout](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/com/android/internal/widget/ConversationLayout.java)。它提供会话头像和应用角标，并在展开时控制应用名；具体尺寸、角标图形和位置由设备 framework/SystemUI 决定，本地源码和反射检查不能保证与某一 OEM 微信原模板像素一致。

图片预览额外核对了本地参考对应的 AOSP commit `94b4c163b7dfe5ce3607f7bb8456f9573f7de57d`：`DecoratedCustomViewStyle` 的 `DECORATION_PARTIAL` 保留大图标，经过公共 `applyStandardTemplate()` / `bindLargeIcon()` 使用原 `mLargeIcon`，与普通微信卡片共享系统图标绑定路径。因此本次保留现有 fitStart 图片布局，不把“必须改成同一种模板”当作原生头像的必要条件。[对应源码](https://github.com/aosp-mirror/platform_frameworks_base/blob/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/app/Notification.java#L6950)

本轮本地验证：207 项主机测试通过，失败、错误、跳过均为 0；使用 `-PrequireReleaseSigning=true` 离线完成 `testDebugUnitTest assembleDebug assembleDebugAndroidTest`，Debug 与 androidTest 两包签名证书均与现有 release 密钥证书一致，签名及 zipalign 检查通过。新增三项 framework 回归已编译，未执行；未运行完整 Lint。没有安装 APK、读取或操作设备、重启进程、提交或推送。原始日志、证书核验结果与两包副本仅保存在本地忽略目录 `.debug-artifacts/notification-presentation-20261026/`。
