# 微信通知外观

## 2026-11-03 紧凑横幅测试与生产契约对齐

本次仅调整 `PrivateCompactHeadsUpInstrumentation`，保留现有横屏生产实现与用户已确认的视觉基线。真实 Builder 返回模板后，测试按资源 ID 检查契约：生产支持的两个旧模板验证改写、原视图隔离与 reapply；其他模板必须返回同一个原 RemoteViews，且返回视图仍可 apply。Parcel/Builder 重建、图片预览、本人回复、连续来信及合法正文等路径同样检查真实 Builder 返回契约。

两个旧模板另外按各自资源名独立构造，资源可用时继续执行完整改写检查；群聊、bot、未归一化和全屏门禁也使用支持模板，避免被未知模板门禁提前返回而形成无效覆盖。模板或生产所需资源不存在时，测试只记录对应覆盖范围不可用，不把异常或断言失败当作缺资源放行，也不整项跳过真实 Builder 的回退检查。全屏输入使用独立通知副本与公开字段，不新增应用权限，合成 PendingIntent 在 finally 取消。

本机的 2025 模板原样回退与旧模板上的改写是不同覆盖范围；runner 通过不能据此声称本机已验证不存在的旧模板，亦不能代替实际 Xposed hook 和真实横屏视觉验收。原始构建、设备输出及覆盖范围日志仅保存在本地忽略目录 `.debug-artifacts/compact-test-alignment-20261103/`。

最终主机测试 **287/287**，Debug/androidTest 构建、现有 release 证书匹配及 zipalign 通过；142 个输入文件哈希一致的隔离副本 `lintDebug` 为 0 errors、39 warnings。Android 17 / API 37、微信 8.0.79/3183 真机 framework **85/85、0 runner skipped**，微信描述符 **45 项通过**。读取实际 framework 资源表确认本机不存在两个旧模板，因此旧模板的改写、克隆/reapply 与独立门禁分支本机未执行；实际 Builder 及重建/补图/本人回复/连续来信/合法正文的未知模板原样回退检查均执行通过。测试进程的 scope 日志在本机 logcat 未返回内容，覆盖边界另由本地资源表与测试代码核对记录，不将缺失分支计为已验收。

本次 76 个生产源码/资源文件哈希全部保持不变。设备流程在 finally 卸载测试包、恢复测试前非 Debug Release，回拉 SHA-256 相同，独立查询确认测试包和两种临时 runner 不存在；未重启整机、SystemUI 或微信。旧套件 84/85 的历史记录保留在下节，当前测试契约对齐后的结果如上。

## 2026-11-03 每个私聊联系人的首条消息旁头像

用户更正：开机后每个私聊联系人各自的第一条消息，在通知栏展开正文旁出现发送人头像；该联系人的第二条更新后消失。会话缓存按通知 ID 保存，但普通私聊的 UNKNOWN 与已解析 DIRECT 都生成 `isGroupConversation=false`，身份解析先后本身不能证明该现象由分类错误引起。若微信原通知显式标为群聊，首条还可能按原生 GROUP 证据处理、后续由 talker 校正；当前同样没有该路径的运行时证据。暂不修改会话身份、异步回调或缓存生命周期。已发现的候选缺口是：原头像 hook 遇到缺大头像直接退出，若原 style 为 legacy，展开的 MessagingLayout 仍显示发送人头像；后续有头像时才切为一对一 ConversationLayout。是否实际发生了这种头像变化仍缺运行时证据，不能把候选分支写成已确认根因。两种布局的行为见 [AOSP MessagingLayout](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/widget/MessagingLayout.java) 和 [ConversationLayout](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/widget/ConversationLayout.java)。

最小候选修正仅保留在原 `useNativeAvatar()` 中：已归一化（`nevo.messagesNormalized=true`）、具有显式 `android.isGroupConversation=false` 的非 bot 消息，即使缺原头像也选择普通会话布局。有原头像时使用原图标；重要会话类型保留，无头像群聊、缺标记通知、bot 和 hook 失败继续原回退。完整 framework 签名、既有进程/归属/总开关门禁不变。撤去上一轮新增的 `avatar_render` 诊断、大段渲染辅助方法与独立 runner 项目；现有匿名 ready/unavailable/异常日志保留。

在既有 `nativeAvatarAndGroupScope()` 中复用同一个 Local，按 A1、A2、B1、B2 检查两个独立通知 ID：每个联系人的首条无头像、后续有头像，均选择普通会话类型并保留非群聊标志与各自发送人；B1 在 A 已更新后仍须独立满足条件。已有单联系人 UNKNOWN 到 DIRECT 的分类及异步回调隔离检查继续保留。此合成场景只覆盖候选缺头像路径，不证明真实首条缺头像；设备 framework 结果见下段，真实开机首条私聊展示仍待验收。

本轮主机测试 **287/287**，Debug/androidTest 构建、现有 release 证书匹配及 zipalign 均通过。142 个源码/构建输入文件 SHA-256 一致的隔离副本完成 `lintDebug`，0 errors、38 warnings。最终 APK 和校验记录在本地忽略目录 `.debug-artifacts/private-first-avatar-20261103-minimal/`；替代上一轮候选，未进行实际 hook 安装和真实首条通知验收。

2026-11-03 授权设备测试：Android 17 / API 37、微信 8.0.79/3183，同包 framework instrumentation 为 **84/85 通过、0 skipped**，`notificationNativeAvatar`（包含上述双联系人场景）通过；已安装微信描述符 **45 项通过**。唯一失败为 `privateCompactHeadsUpRenderAndReapply`：设备返回 `android:layout/notification_2025_template_compact_heads_up_messaging`，现有生产门禁按未知模板保留原视图，用例却断言必须发生改写。隔离诊断包只在失败断言中补充模板资源名，复测结果一致；没有修改横幅生产逻辑或放宽门禁。此结果记录为合成测试假设与当前模板不符，不等于真实横屏显示回归；本次套件不能写成全部通过。

两轮均在 finally 清理测试包并恢复测试前 Release；独立查询确认测试包、独立及同包临时 runner 不存在，恢复 APK 的 SHA-256 与测试前备份相同且非 debuggable。原始记录仅在上述忽略目录的 `device-run-1/` 与 `device-diagnostic/`。未重启整机、SystemUI 或微信，未制造真实联系人消息；framework 用例通过不能代替真实首条通知、实际 Xposed hook 或 OEM 视觉验收。

## 2026-11-01 横屏紧凑横幅的重复联系人

2026-11-03 用户补充：本次头像修改之前，真实横屏紧凑横幅的显示效果已通过。保留该视觉基线，不因本轮合成用例对 2025 模板的错误改写预期而判定显示回归。下面保留原实现与当时的验证记录；本轮未重新进行真实横屏视觉验收。

用户反馈 `daefa7b` 后仍出现“联系人名：联系人名 消息”。该提交只调整 MessagingStyle 的会话标题，并检查普通标题/正文 extras；已有外观 hook 的 `createContentView()` 也不覆盖横屏紧凑横幅的独立入口。因此，原检查不能证明横幅的最终控件正确。本轮离线核对 AOSP `Notification.Builder.createCompactHeadsUpContentView(): RemoteViews`、`MessagingStyle.makeCompactHeadsUpContentView()` 及其模板：标题使用 `android:id/title`，正文使用 `android:id/header_text_secondary`，同一行还包含应用名、header 和分隔符。尚无本次异常通知的运行时数据，不能把入口缺口写成已确认的真机根因。

新增 Builder 紧凑横幅 AFTER hook，在既有微信主进程/SystemUI 范围内受增强总开关、微信 Context、模块外观归属和非汇总检查约束。仅处理已归一化的非群聊、非已确认 bot 的 MessagingStyle，且必须匹配 framework 的 `notification_template_material_messaging_compact_heads_up` 或 `notification_template_material_compact_heads_up_base`。克隆最终 RemoteViews，将标题设为模块保存的联系人会话名（缺失时用消息 Person），正文采用最后一条来信；无来信时用最后一条本人回复，但标题仍保留联系人。清除同一行其余 header/应用名及分隔符的可见性。正文原样保留，不按联系人字符串删词，不改消息 Person、归档、回复动作或原始 RemoteViews。

入口完整校验零参数、非静态、准确 `RemoteViews` 返回类型，以及 `Builder.mContext: Context`、`Builder.mN: Notification` 非静态字段。方法不可用时仅关闭此入口；未知模板、缺资源、未归一化、群聊、bot、全屏通知或缺联系人/正文时保留系统视图。没有新增微信混淆映射或版本名/版本号门禁；功能按 framework 能力启用，各微信功能继续遵守自己的版本规则。

Debug 的 `source=compact_hook_ready` 仅证明安装；`compact_render` 记录进程、是否更改、layout ID、模板/归一化/群聊布尔值、分类数字和联系人字段是否存在；`compact_payload` 记录消息数量、是否来信、正文是否以联系人名开头和是否有会话标题，均不记录姓名、正文、账号或 URI。`compact_hook_unavailable` / `compact_render` 的异常日志只记异常类型。若实际横幅仍异常，可用这些事件区分入口未触发、模板回退和消息正文已带前缀；`changed=true` 不代表视觉验收。

新增合成 framework 检查涵盖带重复文字的最终横幅、原视图不变、reapply、Parcel/Builder 重建、补图、连续来信后回复、仅剩本人回复、含联系人字样的合法正文、两种已知模板及群聊/bot/未归一化/未知模板回退。检查直接调用渲染辅助函数，不能替代 Xposed hook 安装与系统实际横幅路径。主机测试、构建签名结果见交付记录；本轮新增 framework 检查仅编译，运行时 hook 与真实微信横屏行为未执行。

离线来源：[AOSP Notification.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/Notification.java)、[紧凑消息横幅模板](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/layout/notification_template_material_messaging_compact_heads_up.xml)，2026-11-01 获取；原始副本留在本地忽略目录 `.debug-artifacts/private-compact-title-20261101/`。尚未核对本次目标设备的 framework/OEM 实现。

## 2026-10-30 图片显示诊断与修复

Android 17 真机上，微信 8.0.79/3182 的一张不同图片已完成事件解析、解码和补发，通知中有 `nevo.wechat.imageReady=true`，SystemUI 的展开视图 AFTER hook 也已进入且归属检查通过，但用户展开后仍看不到图。通知管理器保存的记录为 `android.picture=null`、`android.pictureIcon` 为 URI 类型；原渲染器只读取 `EXTRA_PICTURE`，所以只克隆并清理旧视图，没有添加新图片。框架回归以仅有 `EXTRA_PICTURE_ICON` 的合成通知复现了此缺口（76/77，通过项中不含新增用例）。

现展开渲染优先使用 `EXTRA_PICTURE` 位图；位图被系统转存后，改用 `EXTRA_PICTURE_ICON` 通过 `RemoteViews.setImageViewIcon` 添加图片。重建新补图时清除可能继承的旧图标，撤回或失效时同时清除两个字段。缺少两个字段、模块资源构造失败或系统视图应用失败时，仍保留消息模板的 `[图片]`。该改动没有新增 hook 目标或版本映射；图片请求仍需 8.0.79 与 3182 同时匹配、原开关开启并通过运行时签名校验。Debug 的 `image_render_entry` 只记录进程、归属及两种图片字段是否存在，不记录 URI、路径或正文。

后续私聊截图显示图片贴在卡片左缘，而同卡片消息文字从头像右侧开始。设备 framework 的 `notification_2025_content_margin_start` 为 58 dp，在该设备密度下对应截图中的约 203 px 差距。私聊和群聊 MessagingStyle 都通过 `withImagePreview()` 将同一个 `wechat_group_image_preview` 添加到系统 `notification_main_column`；该列本身未给新增子视图加入消息内容缩进。现在只在 framework 的 2025 展开会话/消息模板上读取其内容缩进并给图片视图设置对应的起始侧 padding，保留系统原布局、头像、文字和回复区。`ImagePreviewLoader` 的非 MessagingStyle 回退使用另一套 `wechat_image_preview`，标题与图片原本同列，不应用这次缩进。无匹配模板或缩进资源时沿用原视图行为。合成 framework 回归已添加私聊、群聊和回退模板检查；设备拔除后无法运行新增回归或确认真实通知最终对齐。

修复包的主机测试 219/219、Android 17 framework 测试 77/77 通过；测试包在 `finally` 阶段卸载并核验不存在。最终 Debug APK 已在手机原地覆盖，手机与本地 APK SHA-256 相同；用户手动重启 SystemUI 后，同一私聊的新图片通知中看到了缩略图。同期实时日志为 `published`、`image_render_entry ... owned=true bitmap=false pictureIcon=true`、`image_render_result ... viewChanged=true`，支持该图片经 URI 图标路径显示的结论。此项视觉验收只覆盖这次私聊图片；群聊、撤回后的视图、其他系统版本仍未验证。日志、APK 和原通知只留本地忽略目录 `.debug-artifacts/live-diagnosis-20261030/`。

## 当前行为

- 私聊的 MessagingStyle 在消息列表有来信时不另设会话标题，由消息中的联系人 Person 提供名称，避免普通标题 extras 重复联系人。群聊继续设置群名作为会话标题，保留成员发送者；对方撤回后仅剩自己回复的私聊仍保留联系人会话标题，避免系统回退显示自己的名称。私聊的原会话名仍存于模块身份元数据，供归档与后续重建使用。2026-10-31 的标题检查未覆盖实际紧凑横幅控件；2026-11-01 新增独立入口修正，范围与待验收项见上文。
- 私聊和群聊的 MessagingStyle 使用微信原通知大图标，由系统 ConversationLayout 提供通知主头像和微信应用角标；群聊使用原群头像，不写入成员 Person。首次及后续文本通知成功重建为 MessagingStyle 后，都清除微信原通知携带的折叠、展开及横幅自定义视图，避免首条消息仍显示微信的联系人列表式卡片。重要会话类型保留；归一化非群聊缺原头像时仍选择会话布局，已确认 bot、无原头像的群聊及未归一化通知保留原回退。
- 私聊和群聊图片补图后都继续用 MessagingStyle，联系人/群名、群成员、消息文字沿用对应文本消息的系统排版，仅追加高 160 dp、完整等比左上对齐的 fitStart 图片。消息身份、回复动作、静默更新、token 和失效清理保持原规则。
- 折叠通知隐藏“微信”应用名及存在的分隔符；展开通知由系统正常显示应用名，不另外改写联系人/群名与成员标题。链接等媒体卡片成功在微信原生通知上拼接回复时保留微信原模板；未拼接时使用消息会话模板和原头像角标。
- 移除原应用名隐藏设置、文案、运行时开关及同步项。schema 11 在本地初始化和本地/远端同步时清除旧键 `WeChatDecorator.hide_expanded_app_name`。旧键只为迁移清理保留，其余设置及通用远端选择保护不变。

## 渲染接口与门禁

仅在既有微信主进程和 SystemUI 作用域，对实际微信 Context、带模块外观标记 `nevo.wechat.appearance=com.tencent.mm` 且非汇总的通知处理。标记不作为消息身份、回复资格或已读证据。受微信增强总开关控制，不新增设置。

头像 BEFORE hook 为 `Notification$MessagingStyle.makeMessagingView(int): RemoteViews`。完整校验 `Style.mBuilder: Notification.Builder`、`Builder.mContext: Context`、`Builder.mN: Notification`、可写实例字段 `mConversationType: int` / `mShortcutIcon: Icon`，以及 legacy=0 / normal=1 的会话类型常量。有原头像的非 bot MessagingStyle 使用原图标；归一化非群聊缺头像时保留系统头像回退，但同样把 legacy 切换为普通会话类型，避免消息旁头像。重要会话类型保留。

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
