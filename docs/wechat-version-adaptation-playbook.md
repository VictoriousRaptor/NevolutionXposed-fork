# 微信版本适配手册

## 2026-11-03 紧凑横幅测试契约对齐

- 仅调整测试 helper，不改变现有生产 hook、签名、进程、功能开关、微信版本匹配或未知模板回退。真实 Builder 的已支持模板要求改写，未知模板要求返回原 RemoteViews 并可 apply；重建、补图、本人回复、连续来信和合法正文沿用该判断。
- 对生产支持的两个旧模板分别构造 fixture，资源可用时执行改写、克隆隔离和 reapply；群聊/bot/未归一化/全屏门禁必须放在可改写模板上独立检查。缺模板/必需资源仅记录相应分支不可用，不吞异常或把未知模板当作这些门禁的有效覆盖。用户确认的真实横屏显示基线保留，原先 84/85 是旧合成断言不适用当前 2025 模板的记录。
- 测试通过、旧模板分支实际可用范围与真实微信/OEM 视觉分别记录；本次测试对齐不代表头像真实首条通知或 Xposed 渲染链路已通过。详情见[通知外观说明](wechat-notification-appearance.md)，原始材料只在本地忽略目录 `.debug-artifacts/compact-test-alignment-20261103/`。
- 最终主机 287/287，签名构建/证书一致/zipalign 通过；142 个输入文件哈希一致的隔离 `lintDebug` 为 0 errors、39 warnings。Android 17 / API 37、微信 8.0.79/3183 framework **85/85、0 runner skipped**，微信描述符 **45 项通过**。实际资源表中两个旧模板不存在，旧模板改写及独立门禁场景本机未执行，不能据总数声称它们已验证；实际 Builder 及各类重建的未知模板回退已执行。本次 76 个生产文件哈希不变。测试包与临时 runner 已移除，测试前非 Debug Release 已恢复且 SHA-256 一致，未重启整机、SystemUI 或微信。

## 2026-11-03 每个私聊联系人的首条消息布局

- 用户更正为开机后每个联系人的首条展开消息显示发送人头像、该联系人后续更新消失。源码确认缓存按通知 ID 隔离，但 UNKNOWN 与 DIRECT 均生成非群聊标志，不能仅据身份首次解析修改缓存或回调。缺原大头像导致 legacy 布局未切换仍是候选原因，尚无异常通知运行时数据确认。
- 最小候选修正仅调整既有 `Notification$MessagingStyle.makeMessagingView(int): android.widget.RemoteViews` BEFORE hook 内的 `useNativeAvatar()`：`nevo.messagesNormalized=true` 且存在显式 `android.isGroupConversation=false` 的非 bot 消息，在无原头像时也把 legacy=0 切为 normal=1。有头像仍使用原图标，重要会话类型不降级；无头像群聊、未归一化/缺标记通知、bot 继续原回退。既有可写实例字段 `mConversationType:int` / `mShortcutIcon:Icon`、`Style.mBuilder` 和 `Builder.mContext/mN` 的完整签名校验、微信主进程/SystemUI 范围、微信 Context/归属/非汇总/增强总开关及失败回退不变。
- 不新增微信版本映射或外观白名单；通知回复的历史 OR/精确 AND/同名唯一签名选择，图片、应用内回复与撤回的独立 AND/实验性/失败关闭规则分别保持原状。未修改微信混淆布局或画像。撤去上一轮新增诊断、独立回归入口和辅助方法，复用原匿名日志及 `nativeAvatarAndGroupScope()`，按 A1、A2、B1、B2 检查两个通知 ID 的头像缺失/补齐、会话类型、群聊标志和发送人归属。framework 编译、设备执行、hook 安装与真实开机/OEM 视觉仍须分层记录，详见[通知外观说明](wechat-notification-appearance.md)。

- 本轮主机测试 287/287、Debug/androidTest 构建、release 证书一致及 zipalign 通过；142 个源码/构建输入哈希一致的隔离副本 `lintDebug` 为 0 errors、38 warnings。候选 APK 与记录在本地忽略目录 `.debug-artifacts/private-first-avatar-20261103-minimal/`，后续授权设备检查结果见下条，不能写成真实开机首条通知通过。
- Android 17 / API 37、微信 8.0.79/3183：同包 framework **84/85、0 skipped**，双联系人头像场景所在 `notificationNativeAvatar` 通过，微信描述符 **45 项通过**。唯一失败是紧凑横幅合成用例要求改写 `notification_2025_template_compact_heads_up_messaging`，而既有门禁对该模板保留原视图；隔离包只增加断言资源名后复测一致，没有修改生产代码。用户确认头像修改前的真实横屏显示已通过，本轮测试假设不符不作为显示回归证据，套件仍如实记录一项失败。两轮均清理测试包及临时 runner，恢复测试前非 Debug Release 并回拉核对 SHA-256 一致。未重启整机、SystemUI 或微信，未验证实际 Xposed hook 与真实联系人首条消息；详见[通知外观说明](wechat-notification-appearance.md)。

## 2026-11-02 微信 8.0.79/3183 自动真机验证

适配分支先 rebase 到本地主检出区 `master` 的 `daefa7b`，再执行以下验证；主检出区未提交的其他修改不包含在本轮基线中。设备为 Android API 37，已安装微信 `versionName=8.0.79 && versionCode=3183`，回拉 base APK SHA-256 为 `90096f785f1227f5456f3d099ea9855822d5601754565de5dc45ab5c1df7e3f0`，与 2026-10-29 APKMirror 样本相同。APK 来源、反编译位置及完整原始描述符见 [8.0.79 findings](wechat-8.0.79-findings.md)。

首轮发现普通大图 `large_unavailable reason=no_match`：`pq0.a0` 的数据库字段类型 `lh5.k0` 是接口，`f(String,String[],int): android.database.Cursor` 为抽象实例契约，旧解析器误将其当作必须有方法体的下载入口。现在仅数据库 query 允许 abstract，仍精确校验参数、返回类型、非 static 及唯一数据库字段；反射调用由实际数据库接收对象派发。下载实现的 getter 与完整 `b(...) : int` 继续要求非 abstract 实例方法，其他服务继承、callback interface 和消息对构造校验保留。3183/3200 的合成接口派发测试及抽象下载入口拒绝测试通过；本轮只有 3183 完成设备复测，不能据此补写 3200 真机结果。

版本匹配仍要求版本名和代码同时满足精确画像；同名未知构建继续按各组件完整签名唯一通过才实验性启用。初始化仍在微信主进程的 `Application.onCreate(): void` AFTER；图片预览开关控制事件安装，普通大图还要求大图开关，签名/安装/查询失败继续只关闭或回退相应组件，保留本地缩略图与文字占位。没有新增 hook 目标、回调或跨功能授权。

最终结果：主机 **284/284**；同包 framework instrumentation **84/84、0 skipped、0 failures**；已安装微信描述符及生产解析器检查 **45 项通过**，两类 instrumentation 均以 `INSTRUMENTATION_CODE: -1` 结束。runner 补齐 master 缺失的 3 个名称，并在执行前检查名称/用例数量；图片占位用例在生成预览前复制待图通知，避免 framework Builder 复用对象导致阶段混淆，并在主线程测量布局后检查待图、完成态和移除图片后的回退。生产解析器校验覆盖图片事件/下载、应用内回复、撤回和有精确支持画像时的 Emoji；旧版没有 Emoji 画像时不误报失败。

完整 `lintDebug`、`lintRelease` 均为 0 errors（分别 35、14 warnings）。Debug 同包 framework 用例显式要求 API 28+，低于此版本返回取消及原因；微信描述符模式独立于该限制。所需 API 标注仅作用于测试 helper，Markdown 测试隐藏 API 检查仅在注明 `--no-hidden-api-checks` 的测试类中定向抑制。最后一次入口调整后的 framework 复测证据另存 `.debug-artifacts/framework-final-3183/`。

微信主进程连续两次启动均得到 Emoji `emoji_hook_ready match=exact`、应用内回复 `app_reply_ready match=exact`、撤回 `recall_ready match=exact`、图片事件 `events_ready match=exact` 及普通大图 `large_ready match=exact`；通知合成回复为 `profile_ready match=exact usable=true`，仅证明完整画像可解析，未派发回复或证明延迟安装的三道门禁已在实际发送中生效。大图 ready 只证明当前进程解析就绪，不代表已查询真实数据库、提交下载或展示了真实图片。

Debug、androidTest、Release 均通过现有 release 证书及 zipalign 核验，Release 无 instrumentation 入口。各轮测试均在 `finally` 中恢复测试前模块，回拉 SHA-256 一致，并核验测试包及临时 runner 不存在。原始 APK、日志及回滚材料仅在 `.debug-artifacts/verified-3183/` 等本地忽略目录。用户选择保留人工验收：真实收发、通知回复、应用内回复后的轮次、Emoji 展示、普通大图下载/超时/取消、撤回裁剪及 OEM/SystemUI 外观尚未验收；没有重启设备或 SystemUI，不宣称本次 SystemUI hook 已重新加载。

## 2026-10-29 Android 16 framework 仪器测试修复

Android API 36 的独立 androidTest APK 与模块 APK 使用不同类加载器，测试源码访问模块包私有类/方法时产生 65 项 `IllegalAccessError`。Debug APK 现在同时编入 framework 用例并声明同包 instrumentation，直接执行 `com.oasisfeng.nevo.xposed/com.oasisfeng.nevo.decorators.wechat.MessageIdentityInstrumentation`；Release APK 不含该入口。此改动只影响测试承载方式，不改变微信或 SystemUI hook。当前手机复测为 **68/69 通过、1 项明确跳过、0 失败**，另有已安装微信描述符 **40 项通过**，两次运行最终均为 `INSTRUMENTATION_CODE: -1`。跳过的 `markdownFrameworkCodeCleanup` 在该设备 framework 上缺少所需 `stripNonStyleSpans` 签名；生产 hook 在此布局同样记录 `code_styles_hook_unavailable`，不宣称代码样式恢复通过。签名 Debug 和 Release 均成功构建、证书与既有 release 证书相同；测试结束后恢复原模块 APK，回拉哈希与测试前一致，独立测试包及 runner 均不存在。原始结果仅存于 `.debug-artifacts/wechat-same-name-profile-probing/self-runner-green-*/`。

## 2026-10-29 微信 8.0.79/3200 当前分支初次真机复测

在 Android API 36 手机上安装 `codex/wechat-same-name-profile-probing` 的签名 Debug APK（`912a574`），设备已装微信确认为 8.0.79/3200，base APK SHA-256 为 `f2a725bc04302ad218a563533ae79356e66b64aa711b275bc919a8d432f7e15d`，与本地离线样本一致。主机单元测试 252 项通过，Debug 与 androidTest APK 均用现有 release 证书签名；设备回拉的模块 APK 与构建产物 SHA-256 一致。微信主进程连续两次启动均记录 Emoji `emoji_hook_ready match=exact`、回复 `profile_ready match=exact usable=true`、应用内回复 `app_reply_ready match=exact`、撤回 `recall_ready match=exact`、图片事件 `events_ready match=exact`，头像与折叠头 hook 也 ready。`profile_ready` 仅证明回复画像及描述符就绪，未触发合成回复派发或确认三道门禁实际生效。普通大图下载两次均为 `large_unavailable reason=no_match`，该组件没有启用；源码核对提示 `og5.k0` 为接口，其抽象 `f(String,String[],int): Cursor` 与下载探测的非抽象方法要求冲突，具体失败点仍需运行时定向诊断确认。

设备 framework instrumentation 为 4/69：其余 65 项均因测试 APK 与目标 APK 跨类加载器访问包私有成员产生 `IllegalAccessError`（其中一项由断言包裹），不作为功能回归通过。测试 APK 在 `finally` 中卸载并核验测试包及 runner 均不存在；结束时恢复测试前的模块 APK，回拉 SHA-256 与备份一致，并重新启动微信目标进程。未发送真实消息，未测试通知回复、Emoji 显示、收图/下载、应用内回复、撤回或 OEM 视觉效果；未重启 SystemUI，因此它在本次模块安装后的 hook 未核验。本轮原始证据及 APK 仅在忽略目录 `.debug-artifacts/wechat-same-name-profile-probing/device-3200-branch-20261029-225900/`。以下较早写的“3183/3200 未安装本次模块”是本轮前的历史状态；3183 仍无本轮真机结果。

## 2026-10-29 微信 8.0.79/3183 与 8.0.79/3200 精确画像

已从 [APKMirror 3183 APKM](https://www.apkmirror.com/apk/wechat/wechat/wechat-8-0-79-release/wechat-8-0-79-3-android-apk-download/) 抽取 base APK；3200 使用与 [APKMirror 3200 APK](https://www.apkmirror.com/apk/wechat/wechat/wechat-8-0-79-release/wechat-8-0-79-2-android-apk-download/) 所列大小、MD5、SHA-256 完全一致的腾讯官方 CDN 文件。`apkanalyzer` 与 `apksigner` 已核对版本和 Tencent 证书。3183 APKM SHA-256 为 `11a4fe01140090ae14874db2cd6a40a34680504bd0394fb547c10126c0ecab0c`，base 为 `90096f785f1227f5456f3d099ea9855822d5601754565de5dc45ab5c1df7e3f0`；3200 APK 为 `f2a725bc04302ad218a563533ae79356e66b64aa711b275bc919a8d432f7e15d`。APK、反编译源码与日志只存于忽略的 `.debug-artifacts/wechat-same-name-profile-probing/apkmirror-8079/`。原始类、字段、方法名和完整类型表见 [8.0.79 findings](wechat-8.0.79-findings.md) 本日小节；JADX 的 `f数字` 别名不是 hook 名称。

3183 与 3200 均使用 **`versionName == 8.0.79 && versionCode == 对应值`** 精确门禁，画像修订版为 2。版本名或版本码单独相同不能选中精确画像；未知同名代码仍各功能独立探测，完整签名唯一成功才为 `experimental`，零命中/歧义关闭该功能，缓存只存候选 ID 且新进程重新校验。3183 保留 3182 的图片、回复、Emoji、应用内回复及撤回描述符；3200 合成回复门禁移至 `os1.a`，应用内回复回调改为 `e9.s1(int): void`、发送方 `z0(): int`，撤回负载改为 `fm.js`、服务器 ID 为 `H0(): long`，图片事件流改为 `jg5.z`/`oq3.f`/`sa0.l`，图片路径为 `v51.l0.s4(...)`，下载服务改为 `pp0`/`og5`/`ni5`/`ea0`/`da0`/`v51`。每项都在安装前校验返回值、参数、static/实例、字段类型及必要继承关系；图片事件和下载分开探测，不能从一个 ready 推断另一个。3200 的 `sa0.k` 是协程远端阶段，事件 hook 使用同步签名的 `sa0.l`。

3182/3183 在同一功能内已核对为相同完整描述符；未知同名代码只探测一次这组描述符。3200 的不同描述符仍独立探测，两个不同描述符同时成功才判歧义。

回退与诊断：合成回复需接收器和三门禁全装，失败仅保留原生 `PendingIntent`；Emoji 两方法与直接调用者取消优化全通过才激活；图片事件、下载、应用内回复、撤回各自失败时保持该组件关闭，撤回双 hook 部分失败时清理并使旧回调惰性。分别看 `profile_ready`、`emoji_hook_ready`、`image_events`/`image_download` ready、`app_reply_ready`、`recall_ready` 及对应 `unavailable` 的阶段/异常类型，不记录消息或身份。回归至少分别覆盖通知栏原生/合成回复、Emoji 原字形、接收图缩略/普通大图及文件回退、应用内发送轮次、撤回裁剪/不复活、跨版本名/版本号组合和签名缺失回退。当前仅能将 APK 离线核对、主机单元测试、构建与签名核对列为本轮证据；未在 3183/3200 安装本次模块、执行运行时 hook 或真实消息/OEM UI 回归。下面 2026-10-28 的“3183 待验/无 APK”及旧版 3200 `no_match` 记录是历史状态，由本节更新。

## 2026-10-28 同版本名不同 versionCode 的实验性签名探测（3183 待验）

微信主进程 `Application.onCreate(): void` AFTER 首次初始化时，通知栏合成回复及通知原生 Emoji 保留各自按已知 `versionName` 取候选；已知精确 `versionName && versionCode` 组合优先。未知代码只探测**同版本名**候选，完整签名唯一命中后才标记 `experimental` 并安装该功能的 hook；零命中或 8.0.77 多候选歧义均不启用实验性画像。回复原有 8.0.72/8.0.78 的名称**或**代码匹配和 legacy 回退继续保留，原生 `PendingIntent` 回复与不依赖微信混淆布局的 framework 外观仍独立工作。图片事件、大图下载、应用内回复、撤回均已分别接入独立的同版本名完整签名探测，不能从回复或 Emoji 通过推断其他组件可用。

回复完整预检包括 `MMAutoMessageReplyReceiver` 继承 `BroadcastReceiver` 及实例 `onReceive(Context, Intent): void`、`z2.s1.b(Intent): Bundle` 静态方法、对应画像中三道无参数静态 `boolean` 车载门禁。门禁批量安装期间回调保持惰性，任一 hook 失败就撤销已装句柄且关闭合成入口；`profile_ready` 仅表示签名解析，须门禁安装完成才允许合成动作。Emoji 仅有 8.0.79/3182 画像；探测 `com.tencent.mm.smiley.g.d(String): String` 和 `com.tencent.mm.booter.notification.m0.a(com.tencent.mm.storage.e9,long,boolean,boolean,String,int,String,String,Intent,boolean): void` 两个实例非 abstract 方法。直接调用者取消优化成功后才激活，微信通知增强及 `WeChatDecorator.preserve_emoji` 仍在回调时实时读取，默认关闭。诊断只记录版本、画像、`exact`/`experimental`、`no_match`/`ambiguous`、阶段及异常类型，不记录消息或身份。

候选正缓存按功能、版本名、长版本码、微信更新时间及画像修订版存入**微信应用自身私有**的 `wechat_profile_candidates` 偏好组，只保存候选画像 ID，不保存消息或身份。注入代码可在微信主进程同步提交；下一次微信进程启动命中缓存时，仍对该组件重新校验完整运行时签名，不把缓存当作 hook 成功。打开偏好组失败时记录 `stage=open type=...` 并使用不可写的空缓存；读写失败只影响跨进程复用，当前进程仍可完成现场探测。精确版本优先、歧义关闭和各功能独立门禁不变。libxposed API 102 的 `getRemotePreferences()` 在被 hook 应用中只读，无法用于此处写入。

2026-10-28 至 29 日在 Android API 36、微信 8.0.79/3200 的已授权测试中，模块 provider 在微信进程报 `Unknown authority`；显式广播引导 Binder 已入队却长期未分发，前台优先广播仍超时；显式服务绑定立即返回 `false`。这些试验版本均未确认持久化，相关 IPC 入口已从最终代码移除。改用微信私有偏好后，首次启动日志出现 `stage=candidate_persisted feature=emoji`，下一次进程启动出现 `stage=cache_revalidated feature=emoji`。这证明 3200 上该候选的写入、读取和再次完整签名复核；不证明 Emoji 已实际显示，也不能推广至 3183 或其他功能。

`candidate_persisted` 只在同步提交成功后记录，`cache_revalidated` 只在缓存候选再次完整签名通过后记录；日志仅带功能名，不输出缓存键、版本更新时间、账号或消息。主机测试覆盖独立选择会话复核、构建键隔离和提交失败；设备回归仍须分别检查回复、Emoji、图片事件/下载、应用内回复、撤回的 hook 安装及真实功能行为。离线源码、测试构建、运行时 hook 安装与真实设备效果分别记录。

2026-10-29 最终签名 Debug 构建通过 249 项主机测试；模块 APK 与测试 APK 均匹配既有 release 证书，手机安装包回拉 SHA-256 与本地最终 APK 一致，独立测试包已确认不存在。API 36、微信 8.0.79/3200 的微信主进程日志显示：Emoji `emoji_hook_ready match=experimental`，缓存 `cache_revalidated`；framework 头像、折叠头、展开头分别 `*_hook_ready`。合成回复 `profile_rejected match=no_match`，应用内回复、撤回、图片事件和大图下载分别 `no_match` 并关闭；Markdown 代码样式因 `NoSuchMethodException` 关闭。SystemUI 尚需在最终 APK 安装后重新启动才能核对该进程的本次 hook；上述 ready 均未执行真实消息、回复、图片下载、撤回或通知视觉验收。

8.0.79/3183 没有提供 APK，来源、SHA-256 和反编译位置均未知；本阶段不具备该目标的离线字节码、真实 hook 安装、回复送达、Emoji 展示或 OEM 行为证据。回归须分别覆盖精确优先、3183 唯一签名、8.0.77 歧义、错误 helper/门禁、部分安装失败、原生回复和 Emoji 运行中开关，再在取得 APK 与授权设备后逐层验证。

应用内回复与撤回现在各自使用同名候选和完整描述符预检：已列出的名称**且**代码组合仍精确优先；同名未知代码只有该组件唯一签名通过才标为 `experimental`，零命中或多候选通过分别记录 `no_match` / `ambiguous` 并关闭该组件。应用内回复核对消息类、本类状态更新覆盖、发送方向/状态/会话、消息 ID/时间和三个排除方法的精确实例签名；撤回核对 `NotificationItem`、事件直接父类、payload 字段类型、服务端 ID/会话 accessor，并在两个 BEFORE hook 全部安装前保持回调惰性。任一安装失败只关闭相应组件，不影响原生回复、另一事件组件或图片开关。日志为 `app_reply_ready` / `recall_ready`，包含画像和 `exact` / `experimental`；失败记录原因、阶段和异常类型。主机合成类型测试与构建不能证明未知代码的字段语义、事件时序或真实撤回裁剪；3183 APK 和运行时证据仍缺失。

本文档是一次性完成"微信新版本适配"的运行手册：从采集证据、定位混淆类/方法、生成版本画像，到装包、验证与记录。适用于本仓库的维护者与代行实施的 agent。

配合脚本：`tools/collect-device-info.ps1`（只读取设备与 APK，不在手机上做任何修改）。

## 2026-10-30 重复图片通知的消息身份关联

- 图片索引的候选是消息事件，键为会话和消息本地 ID；同一文件或相同内容可属于多条消息。微信重读旧消息服务器 ID 时会刷新身份事件的观察时间，旧身份因此可能与新图片同时进入窗口。本轮不按图片哈希、路径相同或通知时间排除这些身份。
- 复用既有 `NotificationItem.a(android.content.Context): void` BEFORE hook 写入的 `nevo.wechat.verifiedNotificationKey`、`nevo.conversationKey` 和 `nevo.wechat.messageServerId`。图片请求在通知归一化前固定核验标记、会话和正数服务器 ID，使用“会话与服务器 ID 同时匹配”的事件；通知时钟滞后不削弱这个精确关联。目标尚未进入索引或同一服务器 ID 对应多个本地消息时保留文字并重试/到期回退，不改选其他消息；目标身份先到且路径未到时也只等待该消息。30 秒索引 TTL、请求等待预算、事件已分配检查、原图限制和发布 token 均保留。
- 缺少核验标记、有效会话或正数服务器 ID 时保留原时间/观察窗口算法，多候选仍拒选。相同正文、时间或图片不能使不同核验身份复用待处理请求；后续文字的图片连续性检查使用原图片请求的身份，仍须 live token、消息前缀和单图片条件。
- 没有新增或修改 hook、完整签名、回调时机、进程、设置及版本画像。图片继续只在微信主进程、通知增强和图片预览开启且图片完整签名核验成功时运行；图片画像为版本名与版本码同时匹配的 `8.0.79/3182`、`8.0.72/3085`、`8.0.77/3160`、`8.0.77/3141`。精确身份另外依赖既有通知身份 hook；该链路不可用时只走旧的保守算法，不借用其他版本映射。回复、普通大图请求、应用内回复、撤回及 Android/SystemUI 渲染各自门禁保持独立。
- Debug 在已提供核验身份但尚未取得唯一事件时使用 `verified_message_pending` 原因；无核验身份仍为 `no_unique_message_event`，`selection_counts` 保留计数诊断，不记录会话、消息 ID、图片路径、哈希或正文。Release 不新增诊断日志。
- 展开图片只改 RemoteViews 的呈现副本：有正数服务器 ID 时按该 ID 唯一定位 incoming `[图片]`/`图片` 行；没有有效 ID 时，仅在 incoming 图片占位唯一时选中该行。清空所选行文本并追加图片；源通知、回复历史和消息归档不变。图片预览开关开启且存在占位时，从待图 RemoteViews 起反射设置并验证 `FLAG_REAPPLY_DISALLOWED`，因为本次核对的 AOSP `NotificationRowContentBinderImpl.canReapplyRemoteView()` 只看旧 RemoteViews 的 flag。当 SystemUI 缓存中的旧展开视图已带该 flag，图片到达时它会重新 inflate 展开树，避免对已显示的 MessagingLayout 重绑该行并留下淡出占位。flag 接口/资源不可用、目标不唯一或身份不符时保留原生 `[图片]` 并不显示补图；AOSP 依据为[固定源码中的重绑判定](https://github.com/aosp-mirror/platform_frameworks_base/blob/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/packages/SystemUI/src/com/android/systemui/statusbar/notification/row/NotificationRowContentBinderImpl.kt#L1709-L1717)及[展开树替换](https://github.com/aosp-mirror/platform_frameworks_base/blob/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/packages/SystemUI/src/com/android/systemui/statusbar/notification/row/NotificationContentView.java#L466-L510)。仍缓存旧版无 flag 视图或 pending inflate 被更新取消时，新 READY 视图的 flag 不能保证首次转换也走 inflate；该边界及 OEM 视觉都待实机。
- 图片解析在线程池执行，发布在主线程并检查 active token 与 pending 请求实例。后续文字通知即使在图片刚发布前已进入模块装饰，也会刷新同一请求唯一的一组有界重检：取消旧排队回调，重新安排最多 4 次、每 150ms 的尝试；已取出的旧回调失效且不再延续旧链，以覆盖原微信 notify 在 `Local.apply()` 返回后才提交的窗口；重检仍要求同一请求/preview 和活动通知 token。没有在 loader monitor 内跨 `NotificationManager.notify()`，避免与 `Local.apply()` 的锁序形成死锁。窗口以外的跨线程系统提交顺序仍需设备验证，不声称获得全局原子性。
- 主机回归覆盖同路径不同消息、多个旧身份、精确目标缺失/路径迟到、同 ID 冲突、跨会话、时钟滞后、TTL、未核验身份回退和新身份不复用旧请求的值语义；framework 用例验证呈现副本仅清空精确目标、原占位保留、RemoteViews flag 和后续文本中的图片身份。重挂调度的主机回归覆盖队列合并、过时回调失效、部分执行后的完整四次窗口、执行中刷新、请求结束停止和请求隔离。运行时 hook 安装与真实重复图片/SystemUI 验收本轮未执行，framework 用例仅编译；离线证据与真实设备效果分开记录。
- 本轮结果：完整 Debug 主机测试 232/232，在实际 Release 编译类上直接执行的图片选择/身份纯 Java 回归 28/28。规定的签名 Debug、androidTest、Release 构建及 Release lint 成功；三类 APK 均与既有 Release 证书一致。完整 Debug lint 在测试源码分析阶段超过 6 分钟无新输出后主动停止，未记为通过；没有 `testReleaseUnitTest` Gradle 任务，不把 28 项补充回归写成完整 Release 测试。

## 2026-10-31 图片与占位互斥及通知刷新

- 既有展开 AFTER hook 不变。图片预览开关开启且存在 incoming 图片占位时，待图 RemoteViews 即反射设置并验证 `FLAG_REAPPLY_DISALLOWED`；此阶段设置是必要条件，因为 AOSP SystemUI 比较 `newView` 与 `oldView` 时仅检查旧 RemoteViews。只有旧缓存已带 flag 时，补图才确定走新树 inflate；仍缓存无 flag 的旧视图或 pending inflate 被取消时首次转换仍有竞态。补图后只在 render-only `Bundle` 中清空按服务器 ID 精确定位的 incoming 图片占位；没有有效 ID 时要求 incoming 图片占位唯一，再追加图片；系统树/接口或目标核验失败时保持文字。原始 `EXTRA_MESSAGES`、图片身份和回复数据不修改。
- 图片获取 worker 与通知刷新可交错。旧图片发布前后仍由 request instance、active token、talker、消息前缀和精确图片身份约束。每次安全的后续文字通知都会刷新该图片请求唯一的一组重检，取消旧排队回调并使过时回调失效，为最新通知重新保留最多 4 次、间隔 150ms 的完整窗口；若图片在其原通知提交前发布，重检可从已提交的新通知重新挂图。未在 loader 锁内调用 `notify()`；这些重检只覆盖模块可观测的短窗口，无法替代真机的 Android/OEM 提交时序验证。
- 本轮未增改 hook 目标、完整签名、回调时机或微信版本画像。framework 合成用例覆盖 fresh-inflate flag、只隐藏精确消息和源占位保留；host 用例覆盖身份连续性。重挂调度另有主机回归验证队列合并、旧回调失效、窗口重置、执行中刷新、请求结束停止和请求隔离；无手机时不把源码与构建结果写成 hook 安装或 OEM 视觉通过。
- 最终主机测试 237/237；Debug、androidTest 与 Release APK 使用既有 Release 证书构建并核对一致，Release lint 为 0 error / 4 warnings。androidTest APK 仅编译，未安装或执行；SystemUI hook 安装、已附着视图动画、首次旧 flag 缓存过渡及 OEM 显示均未实测。
- 2026-10-31 周审查清理：重挂任务按图片请求合并，新增 7 项主机调度回归先复现旧链叠加，再验证窗口刷新与旧回调失效；完整主机测试 244/244。Debug、androidTest、Release 构建及 Release lint 通过（0 error / 4 warnings），三包签名均与现有 Release 密钥一致且 zipalign 通过。删除仅供测试使用的群聊模板 helper，framework 用例改为直接验证消息模板和群聊标记；测试 APK 仅编译，未安装或执行，真实微信/SystemUI/OEM 验证边界不变。

## 2026-10-30 图片图标与展开布局缩进

- 既有 `Notification$Builder.createExpandedContentView(): RemoteViews` AFTER hook（缺失时回退 `createBigContentView(): RemoteViews`）在微信主进程及 SystemUI 中处理带模块外观标记的私聊、群聊 `MessagingStyle`。从 `EXTRA_PICTURE` 读取位图；系统转存位图后从 `EXTRA_PICTURE_ICON` 读取图标并调用 `RemoteViews.setImageViewIcon(int, Icon)`。两个字段都缺失时只登记旧图片移除动作，保留原消息模板的 `[图片]`。图片失效清理同时删除两个字段，新补图时清除继承的旧图标。
- 私聊和群聊的展开视图均向系统 `notification_main_column` 追加同一个 `wechat_group_image_preview`。Android 17 设备的 2025 展开会话/消息模板把原消息内容缩进 58 dp，新增子视图没有继承该缩进，因此此前图片贴在卡片左缘。仅当展开视图布局 ID 匹配这两种 2025 模板时，从 framework 的 `notification_2025_content_margin_start` 读取设备实际像素值，给图片起始侧设置同等 padding（含 RTL）。无布局或尺寸资源时沿用原视图。非 `MessagingStyle` 回退使用独立的 `wechat_image_preview`，标题与图片同列，不施加该缩进。原头像、标题、消息文字和回复区继续由系统模板管理。
- 沿用原完整 hook 签名、运行时字段/接口校验、微信 Context、模块归属、非汇总、通知增强与图片开关，以及独立图片画像的版本名和版本号同时匹配语义。缺容器、隐藏移除接口或模块资源时回退原生消息视图。Debug 的 `image_render_entry/result` 和 `selection_counts` 只记状态与计数，不含图标 URI、图片路径、联系人或正文；Release 不执行这些诊断扫描。当时真实私聊已验证图标缩略图及重复同图显示，图片缩进和标题修正因手机断开尚未做新包真机视觉验收；合成图片缩进框架用例已编译但尚未执行。没有核验消息身份时，事件索引继续在同会话多个候选时保守拒选，避免通知时间滞后时把旧图误配给新消息；本日后续的精确关联见上节。

## 2026-10-30 通知 Markdown 一级与二级标题

- 既有 `WeChatDecorator.markdown` 展示路径识别独占行首（允许零至三个前导空格）的 `# ` 和 `## `，正文须非空。移除前缀后，一级标题使用原字号粗体，二级标题使用原字号 `sans-serif-medium`；行内强调、删除线和代码仍照常解析，代码字体优先。三级及更深标题、四格缩进、转义或行中标记、围栏代码中的井号保持字面量。不改变 `MessagingStyle` 系统模板或通知字号。
- 原始正文继续保存在 `nevo.markdown.rawText`，重复重建、回复、归档和撤回读原文，关闭开关后恢复标记。已有微信主进程和 SystemUI 的 `Message.stripNonStyleSpans` AFTER hook 同时补回模块所有的代码字体/底纹与二级标题字体；保留完整签名、调用者取消优化、进程与双开关门禁、owner 和清理后全文一致校验。缺任一条件或 hook 不可用时保留原生清理结果，不扩大微信版本画像。诊断沿用 `source=code_styles_*`，不记录消息内容。
- 新增解析边界和标题与删除线共存的主机用例，及 native Parcel、实际 framework 清理、三轮重建与关闭开关的框架用例。2026-10-30 当前手机已断开，框架用例仅能编译；真实 Android 17 / 微信 8.0.79/3182 的标题字重、系统模板显示和 hook 回调仍待真机验收。详见[通知 Markdown](wechat-notification-markdown.md)。

## 2026-10-29 私聊首条文本的通知视图一致性

- 微信原通知即使是普通私聊文本，也可能携带自定义折叠、展开或横幅视图。成功重建为 `MessagingStyle` 后统一清除这三种原生视图，确保首条与后续更新都由系统会话模板显示微信原头像及应用角标；私聊保持 `isGroupConversation=false`，有来信 Person 时由发送者身份提供名称，不另设会话标题，避免横屏短通知重复联系人名；群聊及仅剩自己回复的私聊保留会话标题，消息历史规则不变。标题修复不改变 hook 或版本门禁，回归与验证边界见[通知外观说明](wechat-notification-appearance.md)。仅原生媒体卡片成功拼接回复动作时继续保留微信布局；构建失败仍保持原通知。
- 适用于当前微信通知增强的通用路径，不改变 hook 目标、签名、回调时机、微信/SystemUI 作用进程、开关或版本名与版本号的同时匹配语义。头像 hook 仍需完整运行时签名、实际微信 Context、模块外观标记、非汇总通知和非已确认 bot；有原大图标时使用原图标，归一化非群聊缺头像时按 2026-11-03 规则继续选会话布局，其他缺条件或 hook 失败时保留系统回退。回复、图片、应用内回复和撤回的独立门禁不变。诊断继续使用 `WeChat.Appearance` 的 avatar/collapsed/header ready、unavailable 与异常类型，不记录通知内容。
- 合成回归覆盖私聊首条带自定义视图与下一条更新、媒体原生回复保留布局；真实微信首次收信、SystemUI hook 安装和 OEM 折叠/展开视觉仍需分别核验。仅编译或主机测试通过不代表真机行为通过。

## 2026-10-28 跨媒体通知历史连续性

- 通用微信通知重建路径在最近一份已规范化消息快照之后，按通知顺序补入同会话的原始聊天通知正文；动画表情、图片、视频、文件、链接等保留原生布局的媒体通知在后续消息样式构建中不再形成历史空洞。原生媒体通知当前发布时仍保留微信模板与内联回复；历史中的媒体为微信通知提供的文字/占位符，不推断真实贴纸图片。原始通知必须具备可识别的聊天 ticker 和非空正文；其他会话、撤回提示、同代次副本及已超出归档窗口的内容不作为新历史。
- 此项只改变通知装饰器的历史合并及媒体回复快照，不新增或调整 hook 目标、完整签名、回调时机、作用进程、开关、运行时校验、版本名/版本号匹配或失败回退。适用于现有微信通知增强启用时的各版本路径；回复、图片、应用内回复和撤回仍分别受各自画像和门禁约束。诊断继续使用不含正文的 `WeChat.Identity source=merge` 与媒体回复动作状态；没有新增记录消息内容的日志。
- 回归需覆盖私聊/群聊的文本与连续媒体交错、同名会话隔离、同代次重建、原生结构化历史去重、已读/撤回、媒体通知回复、归档上限，以及微信和 OEM 的折叠/展开显示。本轮主机测试与 framework 用例编译不能证明真实微信收发、hook 安装或设备通知视觉通过；详见[通知历史策略](notification-history-policy.md)。

## 2026-10-27 通知应用头与消息头像统一

- 当前外观策略：带模块外观标记的私聊、群聊消息统一保留微信原通知 `getLargeIcon()`；系统 `ConversationLayout` 以该图标作为会话头像并提供微信应用角标。群聊成员 `Person` 仍只表示消息发送者，不以群头像覆盖成员头像；归一化非群聊缺原头像时仍用会话布局；已确认 bot、无原头像的群聊及无支持的 framework 布局保持系统回退。媒体卡片若成功在微信原生通知上拼接原生回复动作，保留原生模板、内容视图与回复实现；未拼接时才进入现有 `MessagingStyle` 会话构建，并在构建成功后清除原生自定义折叠/展开/横幅视图，避免它们遮住会话头像。构建失败仍保留原通知。
- 私聊与群聊图片补图都保留 `MessagingStyle`、原大图标、会话标题、发送者身份和回复动作；`Builder.createExpandedContentView(): RemoteViews` AFTER hook（该方法缺失才回退 `createBigContentView(): RemoteViews`）克隆系统展开视图，按已校验的 `RemoteViews.removeFromParent(int): void` 清除旧图片，再向 `android:id/notification_main_column` 追加模块 160 dp `fitStart` 图片。先用 `EXTRA_PICTURE` 位图，若被系统转存则用 `EXTRA_PICTURE_ICON` 及 `RemoteViews.setImageViewIcon(int, Icon)`；两者都没有时只清旧图。群图标题和成员排版继续与群文本共用系统模板；私聊图片不再切换 `DecoratedCustomViewStyle`。原生非消息模板的图片构建仍保守使用原有自定义展开回退。
- 新增 `Notification$Builder.createContentView(): RemoteViews` AFTER hook，仅对实际微信 Context、`nevo.wechat.appearance=com.tencent.mm`、非汇总通知且微信增强开启时，克隆折叠 `RemoteViews` 并隐藏系统 `app_name_text` 及存在的分隔符；展开 hook 不修改应用头，因此展开继续显示“微信”。不恢复已删除的设置键。折叠入口要求零参数、实例方法、准确 `RemoteViews` 返回类型及 `mContext: Context`、`mN: Notification` 实例字段；与头像和展开入口各自独立去优化、安装及失败回退。诊断使用 `WeChat.Appearance source=collapsed_hook_ready/collapsed_hook_unavailable/collapsed_header`、`message_image` 与原 avatar/header 来源，只记录异常类型，不记录内容或身份。
- framework 外观 hook 仍按完整运行时签名判定，不新增微信版本白名单。图片获取仍只适配 8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.79/3182，版本名与版本码必须同时匹配；图片开关、等待值和微信主进程签名校验不变。回复、应用内回复、撤回各有独立门禁，不能据外观成功推断这些功能适配。缺头像、容器、`removeFromParent` 或模块资源时保留原消息文字/原生样式。回归需分别检查私聊链接原生回复与无回复、私聊文本/图片、群聊文本/图片、折叠/展开应用头、补图前后、连续更新与撤回；本节是源码及构建范围说明，真实微信/SystemUI hook 安装和 OEM 视觉结果尚未验证。
- 2026-10-27 Android 17 / API 37 设备框架回归：69/69 instrumentation 通过，设备已安装微信 8.0.79/3182 的描述符检查 40 项通过。测试使用现有 release 证书签名的 Debug 与 androidTest APK；`finally` 清理并独立核验测试包和 runner 均不存在，测试前与测试后模块 APK 的回拉哈希均与本地 Release 相同，最终模块非 debuggable。使用了 instrumentation 的 `--no-hidden-api-checks`，结果只证明该测试上下文的 framework 布局与描述符；未发送真实消息，未验证微信/SystemUI 内的 hook 安装或 OEM 实际通知视觉，也未重启整机。原始日志、APK 备份和结果只保存在本地忽略目录 `.debug-artifacts/notification-style-device-20261027/`。

## 2026-10-27 取消应用名修改并撤销额外标题改写

- 撤销上一轮新增的应用名行替换群名、内容标题改为轮次首位发送者的实现和测试。保留更早的群头像角标、群聊图片复用文本模板及图片 reapply/撤回清理修正，应用名、分隔符与内容标题交回系统模板。
- 取消原应用名隐藏功能，移除设置项、中英文文案、运行时开关和同步项；schema 11 在本地初始化及本地/远端同步时删除旧键。其余设置、历史迁移与通用远端选择保护保留。
- `Notification$Builder.createExpandedContentView(): RemoteViews` AFTER hook（方法缺失才回退 `createBigContentView(): RemoteViews`）为群聊图片继续保留，完整实例签名、字段、去优化、实际微信 Context、模块所有者及非汇总门禁不变，不再读写任何应用名/标题视图。缺签名或安装失败保留系统模板，匿名 ready/unavailable/异常类型日志不变。图片所需接口/布局失败直接保留 `[图片]`，不尝试替代接口；可选 Xposed 渲染方法局部声明 BlockedPrivateApi，不放宽设备策略。
- 图片的既有版本名与版本码仍同时匹配，回复、图片、应用内回复和撤回独立门禁不变。本次已授权执行框架旧设置双端清理和群头像/图片用例，最终 68/68 通过；微信描述符检查 40 项通过，测试包/runner 均清理，最终 Release 回拉哈希与本地一致且非 debuggable。真实微信/OEM 视觉结果仍待验收，详见通知外观说明的本次真机记录。

## 2026-10-27 群聊通知主图标和图片消息样式

- 既有 `Notification$MessagingStyle.makeMessagingView(int): RemoteViews` BEFORE hook 不再排除群聊：在微信主进程与 SystemUI 的实际微信 Context、模块外观标记、非汇总、非已确认 bot 且有原大图标的条件下，使用原群头像作为 `mShortcutIcon`，legacy 类型改为普通会话类型，保留重要会话类型。折叠/展开共用此规则，由系统提供微信角标；不把群头像写入成员 `Person`。完整字段类型、可写性、常量和方法签名检查保持原规则。
- 群聊图片补图继续保留 `MessagingStyle`、群会话标志和原会话标题/消息身份。既有 `Builder.createExpandedContentView(): RemoteViews`（缺失时使用 `createBigContentView(): RemoteViews`）AFTER hook 在系统展开模板的克隆上向 `android:id/notification_main_column` 追加模块 `wechat_group_image_preview`，图片高 160 dp、`fitStart`；每次群聊展开先反射校验实例签名 `RemoteViews.removeFromParent(int): void` 并登记旧图移除动作，避免 reapply 重复和撤回残留；群名、发送者和消息文字沿用群聊文本排版，不再从可能混合群名和发送者的 `android.title` 另造标题。收起/横幅保持消息模板，私聊图片模板不变，回复动作、静默更新、token 和图片清理规则不变。
- 模块布局继续使用 `RemoteViews(ApplicationInfo, int)` 的完整反射签名及 libxposed 模块信息，不回退包名查询。图片追加须有 READY、位图和群聊 MessagingStyle；无位图时只清理旧图；无容器 ID、移除接口签名不符、hook 缺签名/安装失败时保留群聊文字或图片标记，群图片移除接口/资源构造异常记录 `WeChat.Appearance source=group_image` 和异常类型，直接保留系统原消息模板。头像失败独立保留系统显示；未新增通知正文或身份日志。OEM 实际布局与 RemoteViews 应用行为仍需真机验证。
- 微信图片画像仍为 8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.79/3182，版本名与版本码**同时匹配**；原有图片开关、等待时间、运行时签名校验不变。framework 外观按运行时完整签名适配，不另设微信版本白名单；回复、应用内回复、撤回画像独立，未知版本保守回退不变。
- 框架回归新增群头像/角标布局选择、群图片 Parcel 后的模板/群名/发送者保留、展开图片布局和原模板克隆隔离、reapply 不重复及撤回重用视图时清除旧图。本次框架用例已在 Android 17 / API 37 执行，记录见通知外观说明；实际 hook 安装、群聊文字/图片补图前后、折叠/展开/横幅、系统标题、连续图文、回复和撤回后的真实微信/OEM 展示待真机验收。验证结果见[通知外观说明](wechat-notification-appearance.md)。

## 2026-10-27 3.4.0 审查精简

本地初始化只迁移已存设置及历史 schema 明确要求的值，不提前写入其他布尔设置的默认值；首次同步仍按本地显式选择、远端选择、默认值的顺序取值，避免覆盖远端显式选择。其他设置默认值和历史迁移规则不变，不增加 schema、来源标记或 hook。删除图片等待策略中仅供旧测试使用的重载、外观安装的未用 ClassLoader 参数和重复模板常量；实际 hook 目标、完整签名、时机、进程范围、独立失败回退及各功能版本匹配语义均不变。回归新增“本地初始化后再合并远端关闭选择”的主机用例，沿用图片等待、签名与作用域测试；不以本地主机结果代替设备验证。

## 2026-10-27 图片展开布局的模块包查询修复

- 现场 Android 17 / 微信 8.0.79 (3182) 已确认：图片事件和缩略图解码成功，但 `RemoteViews(String, int)` 查询模块包时抛出 `IllegalArgumentException: No such package com.oasisfeng.nevo.xposed`，尚未到通知发布。模块安装存在不代表微信进程能够查询模块；此前在模块 UID 下切换微信 Context 的测试没有覆盖这个条件。具体过滤来源未确认，不改动系统可见性或其他模块设置。
- 首次发布和后续图片重新挂接均通过已 attach 的 libxposed API 102 `XposedInterface.getModuleApplicationInfo(): ApplicationInfo` 获取模块信息，显式传给布局构建。`ImagePreviewRemoteViews` 只按 `RemoteViews(ApplicationInfo, int)` 完整构造签名反射查找、设为可访问后创建；该构造在旧系统中属于隐藏接口，不能直接链接成一个较新 API 的公开调用。minSdk 继续为 26；不以同名或其他参数签名替代，不回退到会重现故障的包名构造，也不添加 RemoteViews hook 或调整隐藏 API 策略。构造缺失、访问被拒或其他异常沿用发布/重新挂接失败路径，保留现有文字通知；Debug 的 `publish_failed` / `reattach_failed` 仅记录异常类型，Release 不新增图片日志。
- 模块资源所有者、160 dp `fitStart`、`DecoratedCustomViewStyle`、通知身份与代次、RemoteInput/回复动作、静默更新、图片清理和缓存保持原逻辑。图片/普通大图开关、等待值、微信主进程范围及四组版本名与版本码同时匹配的图片画像不变；回复、应用内回复、撤回和头像/展开应用名各自保持独立门禁。
- 主机回归覆盖包名构造必然失败时的 ApplicationInfo 构造、protected/public 完整签名、错误签名拒绝、构造异常不重试包名及模块信息缺失。新增 framework 场景使用不存在的合成包名，先确认包查询与旧构造失败，再核验显式 ApplicationInfo 的图片通知构建、Parcel、资源包名与布局 ID；原有 fitStart、回复动作和撤回清理回归继续保留。framework 用例仅编译，本轮不安装、不运行设备测试，不把主机通过当作 Android 26 或实际微信/SystemUI 展示验收。
- 合并后的离线验证为 212 项主机测试全部通过，Debug instrumentation APK 与 Release 构建成功，`lintVitalRelease` 通过；Release 与既有 release 证书一致，zipalign 通过，实际 minSdk 26 且非 debuggable。没有执行完整 Lint、framework 用例或真实收图/重新挂接验收。

## 2026-10-26 私聊原生头像与展开应用头对比

- 新增 framework 渲染 BEFORE hook：`Notification$MessagingStyle.makeMessagingView(int): RemoteViews`，在微信主进程与 SystemUI 中，仅对实际微信包、带模块消息标记的非群聊且非已确认 bot 通知，将原 `getLargeIcon()` 传入系统会话图标布局；重要会话类型保留，无原头像回退。链接/文件等原模板继续沿用微信图标；图片预览保留标记。完整字段/常量校验与作用域见[通知外观说明](wechat-notification-appearance.md)。
- 新增展开 AFTER hook：`Notification$Builder.createExpandedContentView(): RemoteViews`，方法不存在才回退到 `createBigContentView(): RemoteViews`；要求零参数、实例方法和准确返回类型。此展开入口现仅用于群聊图片追加和清理，不修改应用名、分隔符或内容标题。只修改模块消息，排除汇总；签名不符、去优化或安装失败独立保留系统显示，匿名诊断使用 `WeChat.Appearance source=avatar_hook_ready/header_hook_ready`、`avatar_hook_unavailable/header_hook_unavailable` 和回调异常类型。
- 原应用名隐藏设置及行为已移除，schema 11 清理旧键，应用名和标题沿用系统模板。
- 不新增或修改微信混淆 hook、版本名/版本码映射；回复、图片、应用内回复、撤回各自门禁和未知版本回退保持独立。主机测试、构建、运行时 hook 与真实微信/OEM 展示分别记录；本轮按用户要求不安装 APK、不运行设备测试、不操作设备或重启进程。framework 回归仅编译，真实头像角标、折叠/展开和群聊标题待另行授权验收。

## 2026-10-26 图片等待设置与下载 ID 恢复

- 新增“图片文件等待时间（毫秒）”：`WeChatDecorator.image_preview_wait_ms`，按数字字符串保存，默认 `1500`，有效范围 `100–30000`。设置页拒绝空值、非整数和越界输入；读取到无效字符串或错误类型时保守使用默认值。设置存储 schema 升为 9；数值独立于既有布尔开关同步到 libxposed `settings` 组，本地显式值优先，本地缺失时保留远端有效值。微信主进程初始化读取设置，修改后需重新初始化微信进程；本轮不自动执行进程重启。
- 每个图片请求固定启动时的等待值，默认截止点为 `request.started + waitMillis`。选定唯一图片事件后，在该事件的 30 秒身份期限前预留 500 毫秒完成最终候选扫描，必要时提前收紧截止点，后续索引刷新不延长它；文件/事件重试、候选画质等待及普通大图下载观察共用同一截止点，避免等待 30 秒时先丢失事件而无法降级到缩略图。可提前发布，超时使用最佳可用图片或保留原通知；不保证微信网络下载按期完成，提交后的原生任务可能继续。不延长索引、预览缓存或调试探针生命周期，不放宽消息归属、取消、发布 token 和原图访问限制。
- 恢复 `fbcf426` 之前的 ID 分工：SQL 单独读取 `ImgInfo2.id`、`msglocalid`、`msgSvrId`；下载首参使用基础图片行 `id`，`MsgIdTalker(long, String)` 使用该行 `msglocalid` 与 talker。缺列回退，非正消息本地 ID 不主动下载，仍保留基础行已有图片的预览。近期有界文件读取与 WXGF 缓存改进继续保留。
- 适用当前四组图片画像：8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.79/3182，均按版本名与版本码同时匹配；原有反射完整签名、AFTER 回调、微信主进程及图片/普通大图各自开关不变。回复、应用内回复、撤回门禁各自独立。8.0.79 的 ID 语义已再次核对原 APK 反编译且设备 base 哈希匹配既有来源；其余版本不据此追加真实下载通过结论。
- Debug 的 `NX_IMAGE stage=decorator_ready` 增加 `image_wait_ms`，`stage=queued` 增加 `waitMs`，用于确认进程配置和请求快照；既有 `large_submitted`、`large_submit_failed`、`retry`、`fallback` 和 token 诊断保留，不输出真实 ID、路径或消息内容。Release 仍不输出图片诊断。
- 回归覆盖默认/最小/最大/无效等待值、缩短/延长画质截止点、远端设置同步及既有选择保留、图片/消息/服务端/原图 ID 互异、无效消息本地 ID 保留本地预览。framework 设置回归使用独立 SharedPreferences，不改用户真实设置。主机、签名构建、framework、真实图片收发及 SystemUI 展示分别记录，构建或描述符检查不等同实际图片下载和显示通过。
- 本轮最终验证：200 项主机测试通过；同源干净副本完整 `lintDebug` 为 0 errors / 1 项原有警告；Debug、androidTest、Release 均通过既有 release 证书与对齐核验。Android 17 / API 37、微信 8.0.79 (3182) 的最终构建完成 65/65 framework 和 40 项 descriptor 检查；测试包及 runner 已移除并独立核验，最终签名 Release 已安装且拉回哈希一致。微信/SystemUI 进程未主动重启，真实图片事件、下载、渲染及新配置在既有微信进程内的加载仍未验收。原始日志及产物仅保存在本地忽略目录。

## 2026-10-24 全项目审查修复（3.4.0 / code 17）

- SystemUI 继续通过 `NotificationListenerService` 已声明构造函数 AFTER 捕获实例和更新 NLS 弱引用。实际移除方法优先匹配 `onNotificationRemoved(StatusBarNotification, RankingMap, NotificationStats, int): void`，类型或方法不存在时独立尝试 `onNotificationRemoved(StatusBarNotification, RankingMap, int): void`；目标必须返回 void 且非 static。选定方法仍安装 BEFORE 回调；同一进程按 Method 仅安装一次，失败不记入成功集合，后续构造可重试。两种签名均不可用时记录 `NL hook failed`，不安装移除追踪；NLS 诊断与其他功能继续沿用既有路径。微信主进程和 SystemUI 的作用域、三种移除原因、代次广播及 Markdown 门禁不变。
- 微信通知栏回复的原生已读动作与当前归档的会话实例、代次和待回应组一起进入 `ReplyTarget`，来源为当前通知的原生 `deleteIntent`。删除旧按 ID 保存动作的表；没有当前动作时不继承旧动作，本地已读与回复派发仍分别处理。会话元数据最多保留 256 个最近使用的 ID；归档仍为全局 120 条、每 ID 20 条，归档中的会话不参与身份淘汰。普通点击/划除不立即丢失身份，淘汰实例的迟到解析仍被拒绝。
- WXGF 输入只分配一份 expectedSize 缓冲，限定读取长度并检查 EOF；空、截断或多余字节均回退。20 MiB 输入限制、前后大小/修改时间核验、单槽转换缓存、像素预算、1.5 秒等待及发布 token 校验保留。
- 120 秒路径探针须同时为 Debug 和 `-PimageDiagnostics=true` 才启动，默认关闭；取消、替换、连续性拒绝和已读清理停止对应旧观察，已取消请求不能迟到启动探针。Release 始终关闭。常规匿名诊断日志继续保留，不新增消息正文、账号或文件路径日志。
- 删除没有当前图片路径生产者的旧 Zoom 动作、接收器和资源；现代 fitStart 预览、动作身份及撤回清理继续使用 ImagePreviewLoader。旧撤回文本正则和两项无消费者 extras 删除，提示判定统一使用 RecallHistory，精确 ID 裁剪不变。展开 minSdk 26 以下的恒定分支；微信 targetSdk 检查及 API 28/31/35 差异保留。
- 新生成画像使用版本名与版本码同时匹配，常量和 label 均含版本码；碰撞核对限定到该精确组合返回的画像声明。已有 8.0.72/8.0.78 的历史任一匹配规则和各功能现有门禁不变，不借本次修复扩展版本支持。

回归：旧 ID 从有原生已读动作更新为无动作、连续回复与失败派发；同名不同构建和错误版本组合；WXGF 长度边界；四/三参数选择、静态/返回类型拒绝、重复安装及失败重试；会话容量、活跃归档保留和迟到解析拒绝。主机测试及生成代码验证可以离线执行，framework 用例本轮仅编译；没有安装、运行时 hook 日志、真实微信收发或 OEM UI 的新验收。

## 2026-10-24 上游遗留实现清理（3.4.0 / code 17）

本轮变更汇入模块 3.4.0，Android `versionCode` 从 16 递增为 17，设置 schema 为 8。[版本说明](release-3.4.0.md)汇总变更与验证边界；以下旧版本构建记录保留原版本与产物身份。

- 移除独立媒体通知样式增强及其开关、布局、取色和 Palette 依赖。SystemUI 不再 hook `Method.isAnnotationPresent(Class)`、`Method.getAnnotation(Class)`，也不再为媒体装饰安装监听器的 `onNotificationPosted(StatusBarNotification, RankingMap): void` BEFORE 回调。设置 schema 升为 8，本地和远端均清除旧媒体键，不再同步或恢复该选择。微信图片/视频/文件/表情消息的原生布局、回复动作与相关测试继续保留。
- SystemUI 作用域保留。`NotificationListenerService` 所有已声明构造函数 AFTER、NLS 弱引用及 `setNLS/getNLS/cancelNotification` 诊断入口继续保留；实际监听器的 removed BEFORE hook（既有四参数优先及三参数回退）、移除原因提取和显式轮次广播不变。`ContextImpl.createAppContext` AFTER 仍仅捕获首个上下文供移除广播使用。Markdown hook 的签名、开关和失败回退不变；旧记录中的 posted/媒体分发说明仅代表当时状态。
- 读取 CarExtender 原生 `on_read` 前先检查扩展及会话 bundle；没有会话时保留原 `deleteIntent`，不再因仅含颜色/图标的扩展抛空指针。即使 participants/messages 不完整，也继续接受会话中已有的原生删除动作，避免把完整会话解析的门槛扩大到划除已读。回复目标继续使用平台 `getUnreadConversation()` 的完整解析，原生回复、合成回复和移除后的本地历史清理仍各自沿用既有条件。
- 仅在旧渠道向新渠道迁移时，原样复制声音（包括静音 `null`）、灯光开关和振动开关。API 35+ 的非空振动 effect 原样复制，其余沿用 pattern；复制后恢复显式振动开关，避免存留 pattern 意外重新启用振动。不创建新版本门禁或修改现有渠道 ID。
- WXGF 扫描仅缓存最终最佳转换候选，整个加载器最多保留一份、20 MiB。命中须同时匹配请求身份及当前候选的绝对路径、大小和修改时间；候选仍须在本轮路径列表中，质量取本轮来源值。文件变化、普通图片胜出或候选消失会失效。JNI 转换在锁外；取消、替换、失败、完成解码及发布结束均关闭对应缓存写入，迟到结果不能复活；旧请求清理不能误清新请求缓存。1.5 秒等待、输入文件/像素预算、最终文件变化检查、通知 token 及发布校验不变。Debug 继续使用匿名 candidate/retry 日志，不增加文件路径或消息内容日志。
- 微信回复、图片、应用内回复和撤回画像的版本名/版本号匹配语义、完整签名校验与未知版本关闭行为均不变。已有本地下载器 ID 改动按用户要求保留，并同步主机测试；本轮没有重新判断该改动的微信运行时语义。自己的头像仅登记[未确定待办](project-modification-plan.md#8-自己的头像来源核实待办未确定)，读取和缓存代码不改。

回归覆盖：设置升级移除旧键且保留微信选项；无车载会话仍保留原删除动作，不完整会话仍使用已有原生 `on_read`；静音、默认振动、关闭但保留 pattern、灯光和 API 35+ effect 的渠道复制；稳定 WXGF 跨轮复用、元数据变化失效、单槽替换、旧请求清理隔离及取消后的迟到转换。Android framework 回归仅编译不代表已执行；本轮未授权安装、重启或真实微信验收，不复用历史设备结果作为本轮证据。

版本升级前（3.3.1 / code 16）的本地验证：167 项主机单元测试全部通过，失败/错误/跳过均为 0；使用 `-PrequireReleaseSigning=true` 离线构建 Debug 和 androidTest APK，两包签名均与当前 release 密钥证书一致，签名及 zipalign 校验通过。应用源码 Lint 为 0 errors / 31 warnings，明确排除 `lintAnalyzeDebugAndroidTest`、`lintAnalyzeDebugUnitTest`，不声称测试源码 Lint 已完成。实际 Debug APK 的 DEX/资源中未检出独立媒体实现、Palette 类和专用布局。新增两个 framework 回归入口仅编译，没有安装、运行仪器测试或验证微信运行时；WXGF 主机测试验证缓存契约，不是实际 JNI 次数或耗时测量。本地证据及 APK 保存在忽略目录 `.debug-artifacts/remove-media-review-20261024/`。3.4.0 的重新构建结果单独记录于版本说明。

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

2026-10-20 通知栏回复后新来信提醒修复：针对 `v3.2.1-build.33` / 微信 8.0.76 / 澎湃 OS 4 的报告，修正通用 `NotificationMessages.rebuild()` 的输入历史重建。`EXTRA_REMOTE_INPUT_HISTORY` 仅收集当前 `ConversationRound.pendingReplies` 中的本人回复；严格更新的对方来信提交轮次后，已回应的回复保留在 `EXTRA_MESSAGES`，不再补回输入历史，避免 `Local.apply()` 因旧回复追加 `FLAG_ONLY_ALERT_ONCE`。连续回复及相同/旧时间通知保留待回应输入，撤回仍按精确 ID 裁剪。原通知已有的静默标记与图片补图的 `setOnlyAlertOnce(true)` 保留。

该修复作用于既有微信主进程通知重建路径，含 8.0.76 legacy 及其他既有画像；启用条件仍为微信通知增强开启。未新增 hook、回调、开关、版本名/版本号匹配或运行时签名校验，回复投递、图片、应用内回复与撤回各自的版本门禁及失败回退不变。沿用 `WeChat.Identity` 的匿名 `source=round advanced=...` / `source=merge` 日志，不记录输入正文。新增最终 `Local.apply()` 提醒标记回归，并调整私聊、群聊、重放和混合回复的输入历史断言。

本次本地验证：138 项单元测试通过，Debug 与仪器 APK 构建通过，两包签名与现有 release 证书一致；应用源码 Lint 为 0 errors / 33 warnings，仍排除 `lintAnalyzeDebugAndroidTest` 与 `lintAnalyzeDebugUnitTest`。仪器 runner 共注册 47 项，本轮只编译、未执行；没有安装 APK 或操作设备。澎湃 OS 4 / 微信 8.0.76 的声音、震动、浮动及真实消息收发仍待授权设备验收。

各功能的版本匹配不能混用：

| 功能 | 当前匹配语义 | 未匹配时行为 |
| --- | --- | --- |
| 通知栏回复 `WeChatReplyProfile.forPackage()` | 8.0.79 **且** 3182，8.0.77 **且** 3141/3160；8.0.72/8.0.78 保留名称或版本号任一匹配；再校验运行时签名。8.0.79 名称或 3182 代码的错误组合先退回 legacy | 选 legacy 候选并校验；不可用时不开放合成投递 |
| 图片事件 / 独立大图服务 | 8.0.72 **且** 3085、8.0.77 **且** 3141/3160、8.0.79 **且** 3182；完整签名校验 | 分别不启用对应组件 |
| 应用内回复事件 | 8.0.72 **且** 3085、8.0.77 **且** 3141/3160、8.0.78 **且** 3180、8.0.79 **且** 3182；完整签名校验 | 保留原轮次行为 |
| 撤回身份和事件 | 同上五组精确版本；完整字段/方法及事件父类校验 | 不启用精确撤回关联 |
| 通知 Markdown 展示 | 无新增版本名/版本号匹配或运行时签名校验；适用于既有微信通知重建路径，含上述版本及 legacy 回退；须开启微信通知增强和 `WeChatDecorator.markdown` | 默认关闭；语法不支持、解析达到限制或出现运行时异常时保留原文，不改变其他功能的版本门禁 |
| 通知原生 Emoji 保留 | 仅版本名 8.0.79 **且**版本码 3182；转换和通知构建方法分别精确校验参数、返回值、非 static / 非 abstract，构建方法取消优化须成功；还须微信通知增强和 `WeChatDecorator.preserve_emoji` 均开启 | 默认关闭；未知组合、签名不符、取消优化/安装失败时保留微信原始转换；不借用回复、图片、应用内回复或撤回画像 |

2026-10-23 内置表情名称映射补齐 32 项，已有映射保持不变。在微信 8.0.79 / 3182 的离线源码与资源中确认 `com.tencent.mm.smiley.g.d(String): String` 会在名称缺失时转为 `[表情]`，新增默认关闭的“保留通知原生 Emoji”以避免该上游损失。作用进程仅 `com.tencent.mm` 主进程，安装入口为既有 `Application.onCreate(): void` AFTER；转换函数 BEFORE 只在 `com.tencent.mm.booter.notification.m0.a(com.tencent.mm.storage.e9, long, boolean, boolean, String, int, String, String, Intent, boolean): void` 的 BEFORE/AFTER 线程作用域内返回原始参数，嵌套与异常退出均清理深度。原生方法其他调用者/线程保持不变，开关每次从远程设置读取；schema 7 默认关闭并保留显式选择。安装前取消已核对直接调用者 `m0.a(...)` 的优化，防止内联绕过；部分安装未激活或失败时回退，诊断标签 `WeChat.Emoji` 仅记录 ready/unavailable/unhook_failed 及异常类型，不记录正文。真实表情图片仍为独立待办；源码、完整签名、回调时机、失败行为与回归矩阵见[通知表情调查](wechat-emoji-notifications.md)。ready 日志或构建通过不代表真实通知显示验证完成。

Markdown 在微信进程的 `WeChatDecorator.Local.apply(NotificationManager, String, int, Notification): Decorating` 末段、`MessagingBuilder.flatIntoExtras(MessagingStyle, Bundle): void` 之后执行。`onCreate(SharedPreferences): void` 读取开关，设置存储 schema 为 5，升级默认关闭。`EXTRA_TEXT` 和 `EXTRA_MESSAGES[*].text` 使用可序列化的 Android `StyleSpan`/`StrikethroughSpan`，代码使用 `TypefaceSpan("monospace")`/`BackgroundColorSpan(0x30808080)`。代码内标记保持字面量，正文空白和换行保留，未闭合或空围栏保留原样。原文保存于 `nevo.markdown.rawText`（消息内置于 `extras`），转换的消息还保存 `nevo.markdown.owner=com.tencent.mm`；读取规范消息时清除两项展示元数据，关闭功能后的正常重建也清除它们。重复重建先读取原文，图片预览复制身份时也保留原文；发送 RemoteInput 和输入历史仍保留用户原始文字。回复、图片、应用内回复和撤回仍按上表分别匹配，不因展示可用而视为完成适配。

代码样式恢复 hook 有两种完整签名画像，均为 `android.app.Notification$MessagingStyle$Message` 实例方法的 AFTER 回调。Android 16 布局使用 `stripNonStyleSpans(CharSequence): CharSequence`，仅修改清理后的返回值，由 framework 原调用者赋回正文。2026-10-25 新增的 Android 17 布局使用 `stripNonStyleSpans(): void`，从 `Message.getText(): CharSequence` 读取清理后的正文，仅在确需恢复代码样式时写回已校验的实例域 `mText: CharSequence`；该域必须非 static 且非 final。两条路径均读取 `Message.getExtras(): Bundle`。安装时先尝试完整旧画像，再尝试完整新画像，不混用调用链；选择依据为参数、返回值、static 属性及必要域的全部运行时校验，不以 Android SDK 数字或微信版本名/版本号推断匹配。

作用进程仍限定 `com.tencent.mm` 主进程和 `com.android.systemui`，沿用这两个 LSPosed 作用域。缺失、签名不符或安装失败记录 unavailable，保留原生输出。实际回调须同时满足微信通知增强与 Markdown 开关开启、消息 owner 标记匹配、存在原文、解析未超限、清理后的全部文字与原文解析结果一致。原方法异常时不修复；仅补回代码范围中的字体/灰底，保留系统已清理后的其他样式，重复回调不累积 span。旧画像曾离线核对 Android 16 / API 36 的设备 framework；新画像依据 [AOSP Android 17 源码固定提交](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/app/Notification.java) 核对，并已由本轮 Android 17 / API 37 仪器用例调用实际清理入口验证恢复及 bundle/Parcel 往返；这不证明微信/SystemUI 中已安装新 hook 或取消优化生效。其他 Android/OEM 仍按完整签名校验并保守回退，额外清理路径需独立核验。修改 Markdown 设置需重启微信进程；安装包含新 hook 的 APK 后，微信及 SystemUI 两个进程均需重新加载模块才能验证完整链路，相关设备操作须按授权范围执行。

诊断标签 `WeChat.Markdown`：启动记录 `source=markdown_settings enabled=<true|false>`；Debug 在字符数、样式数、嵌套或工作量超限时记录 `source=markdown_skipped reason=limit`；运行时解析失败记录 `source=markdown_fallback type=<异常类名>`，不记录消息正文。回归须分别检查三种强调样式、行内代码/不同长度反引号/带语言标签围栏/缩进与 CRLF、转义和残缺标记、中文与 emoji 范围、重复通知/回复/撤回及图片切换、关闭开关恢复原文，以及折叠、展开、锁屏和悬浮通知在明暗主题下的字体与底纹。自动化和真实微信/SystemUI 展示的验证层级见[专题文档](wechat-notification-markdown.md)。

恢复 hook 日志：`source=code_styles_hook_ready process=<进程>` 表示安装成功；`code_styles_hook_unavailable ... type=<异常类>` 表示保守回退。首次实际补回样式记录 `source=code_styles_restored process=<进程>`，之后仅 Debug 记录；回调失败记录 `source=code_styles_fallback type=<异常类>`。均不记录正文。新增回归须覆盖原生清理→恢复→Parcel 的重复往返、灰底被清理但字体保留的情况、未标记/owner 不符/正文已改变/解析超限的消息保持原生结果、关闭设置后移除展示元数据，以及两个进程的实际 hook 与通知字体/底纹验证；ready 或 restored 日志仍不能替代 UI 验收。

ART 内联处理：安装 hook 前通过 libxposed API 102 的 `deoptimize(Executable): boolean` 对所选画像的全部调用者取消优化，避免 boot image/JIT 的内联清理代码绕过私有方法 hook。旧画像依次为 `Message.ensureColorContrastOrStripStyling(int): void`、`MessagingStyle.getBundleArrayForMessages(List, boolean, int): Bundle[]`（static）、`MessagingStyle.addExtras(Bundle, boolean, int): void`、`MessagingStyle.makeMessagingView(int): RemoteViews`。新画像依次为 `MessagingStyle.getBundleArrayForMessages(List, boolean): Bundle[]`（static）、`MessagingStyle.addExtras(Bundle, boolean): void`、`MessagingStyle.makeMessagingView(int): RemoteViews`。除注明 static 的方法外均为实例方法。完整画像校验在任何取消优化或 hook 安装之前完成；取消优化失败时不安装恢复 hook 并记录 unavailable。就绪日志增加 `layout=return_value` 或 `layout=message_field` 以区分画像，恢复/回退诊断沿用 `source=code_styles_restored`、`source=code_styles_fallback`、`source=code_styles_hook_unavailable`，不输出正文。范围仍限定上述两个进程，不改变无标记消息的原生清理结果；回复、图片、应用内回复与撤回各自的版本门禁不变。

2026-10-25 测试报告修复：`replyAlertsAfterIncoming` 改为显式覆盖默认关闭与开启两种展示状态，分别断言 C/C+D 和 B1+B2+C/B1+B2+C+D，并保留输入历史、回复刷新静默、后续来信、补图与原始静默标记检查；每轮测试开始及 finally 清理精确通知 ID 的静态归档，避免复用回复 UUID 的去重状态污染下一轮。这不修改消息展示策略。Markdown framework 用例复用生产签名画像选择实际清理入口，继续做三轮恢复及 bundle/Parcel 往返。主机回归覆盖旧/新完整画像、跨代调用链拒绝、方法返回值/static 属性及新布局正文域约束。Android 17 / API 37、微信 8.0.79 (3182) 环境的完整 framework 仪器测试 64/64 通过，已安装微信 descriptor 检查 40 项通过；测试包及 runner 已移除并核验，最终已安装签名 Release。该结果不代替注入 hook 安装、实际声音/震动/浮动或真实微信/SystemUI 显示验收，原始日志仅保存在本地忽略目录。

`makeMessagingView` 是受限非 SDK 方法，通过统一的精确签名画像解析。生产查找仅在已注入的 LSPosed 进程执行，不在普通设置 App 中调用；访问限制、签名或取消优化失败仍保守回退，须以两个进程的 hook 日志和实际通知验证。仪器测试仅为测试进程放行隐藏 API，以直接检查所选完整画像。未全局关闭 lint 或修改 targetSdk。

2026-10-13 验收分层：134 项主机测试、签名构建及 Release lint 通过；PJZ110 Android 16 上 46/46 framework 仪器用例实际通过，包含新增三项清理恢复回归。用户手动重启系统界面后确认界面生效，测试 APK 已卸载，保留修复版。当前日志缓冲区没有保留专用 ready/restored 记录，未额外确认两个进程的 hook 调用现场；所有通知形态、明暗主题、真实回复投递和其他 OEM 尚未逐项验收。源码、设备测试、用户界面确认与缺失的日志证据分别记录于[专题文档](wechat-notification-markdown.md)。

### 3.2 图片事件（8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.79/3182）

2026-10-24 历史变更：当时 SQL 不读 `msglocalid`，消息对与下载首参均使用基础图片行 `id`。2026-10-26 已按用户要求恢复此前分离逻辑，下述 2026-10-23 参数分工再次适用于当前代码；本轮设置、诊断与验证边界见本文顶部更新。

2026-10-23 普通大图下载身份修复：固定 SQL 增加读取 `ImgInfo2.msglocalid`，与图片行 `id` 分开保存。下载接口 `b(long, MsgIdTalker, int, Object, int, <版本回调接口>, int, boolean): int` 的首参仍为选中基础行的图片 `id`，`com.tencent.mm.plugin.msg.MsgIdTalker(long, String)` 则使用 `msglocalid` 和该行的 `msgTalker`；不使用服务端 ID 或 `reserved1` 代替消息本地 ID。缺少列时查询失败并回退，`msglocalid <= 0` 时不提交下载，但仍可读取该基础行已有的本地图片。仍只在微信主进程、图片总开关及大图开关启用后请求；四组版本名与版本码必须同时匹配，完整运行时方法/返回类型校验及 hook 时机保持原有规则，回复、应用内回复和撤回门禁各自独立。

本次离线补查使用既有 8.0.72/3085 与 8.0.79/3182 源码；8.0.79 的参数用途和完整证据见 [findings](wechat-8.0.79-findings.md)。共用下载器的修复也作用于两组 8.0.77 画像，本次未重新取得其 APK 核对新增列，未声明新版设备兼容性通过。沿用 Debug `NX_IMAGE stage=large_ready`、`large_query_failed`、`large_submitted`、`large_submit_failed` 诊断，不输出图片/消息真实 ID；无效消息本地 ID 的请求直接返回 false，不新增专用日志。回归须覆盖图片 ID、消息本地 ID、服务端 ID、`reserved1` 各不相等时的参数分离，消息 ID 无效时的本地预览与超时回退，以及普通大图完成、取消和迟到结果；构建与主机测试不代表真实下载通过。

本次本地验证：162 项主机单元测试全部通过，其中 `WeChatImageDownloaderTest` 的 7 项包含新增两项回归；使用 `-PrequireReleaseSigning=true` 离线完成 Debug 和仪器 APK 构建，两包证书均与现有 release 密钥一致。本轮没有安装 APK、执行仪器测试、重启微信/SystemUI 或操作设备，未将历史设备验收计入本次修复结果。

2026-10-21 展开图片改为 `fitStart`：生产首次补图与后续重新挂接共用 `ImagePreviewLoader.buildPreviewNotification()`，使用 `DecoratedCustomViewStyle` 保留系统通知头和动作，展开区域为模块资源包提供的 `RemoteViews`，图片区域高 160 dp、等比完整显示并左上对齐；收起和正常横幅仍使用原通知正文。保留点击入口、回复 PendingIntent/RemoteInput、消息身份与原文、请求 token、静默补图及 1.5 秒等待策略，`EXTRA_PICTURE` 继续保留给图片元数据/诊断。撤回/失效清理同时移除本模块的自定义展开图片，避免残留。Debug 发布/重新挂接日志改为 `style=FitStart`，Release 仍无图片诊断日志；不新增 hook，既有图片/大图开关、四组精确画像及运行时签名校验不变。缺失资源或构建失败沿用现有补图失败处理，保留当前通知；SystemUI 的实际宽度和高度仍由系统约束。

此次 138 项单元测试通过，Release / Debug / 测试 APK 构建和既有 release 证书核对通过。Android 16 / API 36 真机的 49 项模块框架测试全部通过，新增用例核对包内及微信资源上下文中的模块布局、Parcel/重建后 `fitStart` 的比例和左上定位、占位正文、回复动作/身份/静默标记，以及撤回清理。Release 已覆盖安装并回拉核对；独立测试 APK 执行后已卸载并核验。上述没有发送真实微信消息，不替代微信实际收图、1.5 秒下载时限、连续图文、通知取消和撤回的完整 UI 验收。

最终 Release 应用源码 lint 为 0 errors / 33 warnings，安装后确认 `debuggable=false`，Debug 测试 Activity 不可解析。已重启微信，SystemUI PID 保持不变，未重启整机；本轮启动日志中未检出可独立确认模块注入的标记，不把进程重启写成 hook 安装或真实图片展示通过。测试包清理纪律已写入项目 AGENTS.md，设备备份与原始证据仅存于本地忽略目录。

仅 Debug 的六组图片缩放手动对比（两张图片分别使用 `fitCenter`、`centerCrop`、`fitStart`）见[测试页说明](image-scale-debug.md)。该页面自发通知，使用打码素材和独立渠道，不安装微信/SystemUI hook，不改变图片开关、画像匹配及生产回退；测试页展示不能作为真实微信图片链路验收。

2026-10-20 图片预览等待策略：共享 `ImagePreviewPolicy.QUALITY_WAIT_MS` 从 5000ms 调整为 1500ms，计时从通知图片请求开始；合格候选提前就绪时提前发布，到期使用当前最佳本地图片，无有效图片时保留原通知。此值不保证微信下载或图片最终呈现能在时限内完成，已提交的微信下载任务仍可能在后台继续。版本门禁、运行时签名校验和回退不变。本次仅完成源码与文档核对，未运行自动化测试或构建，也未做运行时 hook 安装及真机行为验证。

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

`WeChatAppReplyEvents.Profile.forVersion()` 的已核对精确画像要求版本名与版本号**同时**匹配；同名未知代码仅在上述完整签名唯一通过时实验性安装。只有状态更新参数为 2、更新后本人消息状态仍为 2、未命中排除条件且消息 ID 与时间有效时，才把会话标识、消息 ID 和时间交给轮次逻辑。它不读取回复正文；模块通知栏回复通过 `NotificationReplyOrigins` 排除。无签名匹配或歧义时不安装钩子，原有通知行为保留。此门禁与 `WeChatReplyProfile` 的通知栏回复门禁不同，也不随图片预览开关启停。

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

`NotificationItem.i` 的 `toString()` 标签虽为 msgId，`notification.x` → `m0.a(...)` 的调用链证明它取自上述服务端 ID accessor，不能与本地 `getMsgId()` 混用。记录的是运行时原名 `f/h/i/g/c`，不是 JADX 的生成别名。已核对的 8.0.72/3085、8.0.78/3180、8.0.77/3141、8.0.77/3160 与 8.0.79/3182 均按版本名**且**版本号精确匹配；同名未知代码须独立通过唯一完整签名预检。8.0.76、未知版本名、零命中和歧义组合不启用撤回映射。

非空会话与正数服务端 ID 用于通知身份标记、撤回索引和归档裁剪。重建先过滤精确目标及已确认的撤回提示，再推进轮次；正文回退、MessagingStyle、RemoteInput 历史和可关联的回复 ID 一起清理。当前归档实例及活动通知 token 均匹配时才重新发布，已移除或替换的通知不能被旧事件复活；裁剪为空时取消通知并返回 `StopPost`。

无精确 ID 的旧消息保留；通知栏本人回复只有模块 UUID，不能凭发送顺序绑定服务端 ID。未经过该通知入口或改用异步事件入口的路径不承诺精确裁剪。进程索引最多 64 个会话、每会话 128 个撤回 ID；撤回重建取消旧图片预览并回退为剩余文字，剩余图片的视觉回归属于计划第 4 项。实现、证据路径及详细边界见[撤回通知历史裁剪](wechat-recall-history.md)。两版均未完成本次真机验收。

### 3.6 Android/SystemUI 与本地通知 hook 合约

- **2026-11-01 横屏紧凑横幅**：在微信主进程和 SystemUI 增加 `android.app.Notification$Builder.createCompactHeadsUpContentView(): android.widget.RemoteViews` AFTER hook。运行时要求本类声明、零参数、实例方法、准确返回类型，并校验 `Builder.mContext: android.content.Context` / `Builder.mN: android.app.Notification` 非静态字段；沿用既有 create/make 内容视图调用者去优化。受微信增强总开关、微信 Context、模块外观标记和非汇总约束，不新增开关或微信版本映射。微信版本名/版本号不参与该 framework 能力选择；回复、图片、应用内回复、撤回仍各自遵守原先的 `&&` / `||` 匹配和签名规则，不据此扩大适配范围。
- 紧凑横幅只处理已归一化、非群聊、非已确认 bot 的 MessagingStyle，并要求两种已核对的 framework 紧凑模板之一及标题/正文/本地化格式资源均存在。克隆最终 RemoteViews，标题来自模块保留的联系人名（缺失时用消息 Person），正文选择最后来信或无来信时的最后本人回复，清除该行额外 header/应用名及分隔符。全屏、未知模板、缺内容/资源或入口不可用均保留系统行为，不改原消息、身份、回复和归档。该入口与普通折叠、展开和头像 hook 独立安装；失败不禁用其他入口。
- 匿名诊断：`WeChat.Appearance source=compact_hook_ready` 仅证明安装；Debug `compact_render` 的 `changed`、layout ID、进程及资格字段用于确定实际回调与回退；`compact_payload` 的消息数、来信/会话标题/正文联系人前缀布尔值用于区分正文来源。没有联系人、正文、账号、URI 或设备标识。入口/渲染失败分别记录 `compact_hook_unavailable` / `compact_render` 和异常类型。新增回归覆盖完整签名拒绝、重复字段、reapply、Parcel/Builder、补图、连续更新及回复选择、本人回复、合法正文和群聊/bot/未知模板等回退。AOSP 离线源码与新增 framework 检查详见[通知外观](wechat-notification-appearance.md)；framework 检查本轮仅编译，尚未执行 hook 安装、实际设备 framework/OEM 核对或真实横屏视觉验收。用户反馈证明 `daefa7b` 的普通 extras 检查不足以代表横屏修复完成，最终根因仍待本次运行时数据确认。

- SystemUI listener 调试入口不依赖微信版本画像，适用于现有 SystemUI hook 范围；在 `android.service.notification.NotificationListenerService` 所有已声明构造函数的 AFTER 回调中刷新弱引用，不改 posted/removed 目标的完整签名、查找校验和兼容回退。`getNLS()` 与 `cancelNotification(String): void` 供同一 SystemUI 进程、模块类加载器内的手工调试使用，不新增自动调用或功能开关。弱引用为空时取消无操作，否则沿用 listener 的取消行为及异常传播；Debug 保留 `cancelNotification ` 日志，Release 不输出该日志。运行时回归应覆盖实例刷新、空引用和按 key 取消，并单独核对下述 listener cancel 不清空微信轮次的规则；源码与构建验证不能代替这些真机检查。
- 微信主进程的 `android.app.NotificationManager.notify(String, int, Notification): void` BEFORE hook 必须遵守本地装饰返回的 `Decorating.StopPost`，阻止空撤回提示和已抑制快照再次发布；这属于 framework 合约，不新增微信混淆版本映射。
- 标准 SystemUI 移除通知仅在 `NotificationListenerService.REASON_CLICK`、`REASON_CANCEL`、`REASON_CANCEL_ALL` 时发出定向清理；listener/app cancel 不推断已读。接收端只接受归档中实际存在的通知 token，删除该代次及其前驱，并按消息原始代次裁掉新快照中继承的已读历史，保留晚到的新来信。
- 2026-10-23：新增默认关闭的“显示你最后回复的消息”（`WeChatDecorator.show_last_reply`），普通新来信按开关显示 C 或 B+C；设置迁移到 schema 6，不覆盖显式选择。开关在后续构建读取，不冻结轮次设置。通知栏有效发送操作即建立已读边界，不等待派发结果；2026-10-25 起当前通知保留 A 到下一条符合条件的来信，满足既有派发条件后才另存 B，失败不记录 B，也不撤销已读。`EXTRA_REMOTE_INPUT_HISTORY` 继续只保存待回应回复。应用内发送记录当时的通知代次，迟到成功回调只提交已被更新来信跨过的发送边界；下一次正常推进也按代次清理边界前的无时间戳来信。
- 2026-10-23：划除已读恢复上游实现，直接将 CarExtender 的 `on_read` PendingIntent 设为通知的 `deleteIntent`；没有车载扩展时保留原删除动作。移除模块的 `DISMISS` 动态接收器、删除动作包装及专用控制器回调；系统直接发送微信原生目标，不等待本地历史清理。点击、划除和清空的本地历史清理统一依赖上述 SystemUI 移除回调与通知代次校验；hook/广播不可用时可能保留本地历史，但不阻断原生已读动作。无原生 `on_read` 或动作失效时不推断微信已读。不新增微信混淆 hook、版本画像、签名门禁或开关，适用于既有装饰范围。消息/已读边界随图片与 Builder 重建保存；只取消已读代次图片任务，实际活动 token 不匹配时不重发。
- 2026-10-25 回复展示轮次：A → 通知栏回复 B 后，两种展示开关均保留 A+B；下一条严格更新的原始来信 C 才提交延迟已读边界，按开关显示 C 或 B+C。replyReadToken 随 ROUND 保存，已规范化通知、Parcel/Builder 与补图不推进；同时间戳、旧时间戳和零时间戳的处理不变。失败发送仍请求原生已读但不写 B；用户明确移除仍立即清理，迟到回调不复活已移除通知；C 已先于旧目标读取或回调到达时，沿用代次边界保留 C。该变更适用于现有通知装饰范围，不新增混淆 hook、完整签名、回调时机、作用进程、版本匹配或功能开关；各功能的精确版本门禁继续独立生效。沿用 source=round advanced 诊断，不新增正文或身份日志。回归覆盖 A+B 保留、两种开关、连续回复、失败无 B、迟到回调、移除、Parcel 重建和输入历史清理；本轮 framework 用例仅编译，真实微信、OEM UI 和提醒行为未验证。
- 完整行为、诊断与回归边界见[通知历史与最后回复展示](notification-history-policy.md)。沿用 Debug 的 `WeChat.Identity`、`removal_receiver matched` 与匿名轮次/合并日志；Release 不增加消息、联系人或 token 明文日志。回归覆盖普通/媒体通知和 Builder 重建后的原生删除目标、关闭装饰器后直接发送，以及 SystemUI 清理缺失时的边界；framework 用例不代替微信未读数、划除/清空和进程回收场景的真机验证。
- 原生回复转发只执行一次 `RemoteInput.addResultsToIntent()`；@ 前缀调整在 results Bundle 内完成，不再拷贝旧 ClipData。原生 PendingIntent 回调 `resultCode != 0` 时中止预留，不记成功回复历史。
- 2026-10-23 回复已读时机：在原生/合成接收器取得有效 ID、输入及该路径必需元数据后、派发前，统一请求微信原生已读并捕获通知代次；2026-10-25 起延迟当前快照中 A 的展示清理，直到下一条符合条件的原始来信。`read_requested` 只证明用户发送操作进入此阶段，不证明微信已读成功。原生非零回调、PendingIntent 取消、合成派发不可用/异常均不阻止此前已读请求；失败仍中止来源预留且不写入 B。本地清理与原生动作分别捕获运行时异常，保留匿名通知 ID 的诊断，并继续回复派发。媒体回复挂接时一并缓存 CarExtender 的原生已读目标；无目标时不另造微信广播。当前快照保留 A 与待回应组供回调更新，SystemUI 移除仍可删除该快照；并发新来信和换会话沿用代次/会话实例保护。不改微信混淆 hook、版本门禁、作用进程或功能开关。回归须覆盖失败仍已读但无 B、连续回复组、迟到回调、新来信及移除后不复活；真实未读数和通知 UI 待真机验收。
- 成功回复历史的保存独立于活动通知重发：通过上述校验后先按当前展示开关保存规范化快照，再检查活动 token 并决定是否发布。普通取消、listener cancel 或活动查询无匹配项均保留成功回复与去重 UUID；用户确认移除后归档为空则仍拒绝迟到回调。无活动通知的 framework 回归使用无操作重发入口，覆盖连续成功回复、C 先于回调到达、重复回调及两种开关下后续 C/D；构建不代表该用例或真实微信已验证。
- 回复结果边界：`native_pending_intent_callback resultCode=0` 仅表示模块收到该广播回调值，并非微信业务成功码或网络送达回执；`PendingIntent.send()` 传入初值 0，已核对的 8.0.79 接收器不主动设置结果码，提前返回也可能保留 0。合成路径的 `synthetic_dispatch_complete` / `synthetic_dispatched` 仅表示直接调用 `onReceive(Context, Intent): void` 正常返回，内部提前返回同样可能被视为派发完成。微信出现对应新消息可证明已进入本地消息处理，不能反推回调数值、调用路径或对端送达；精确数值以日志为准，实际发送状态与已读数另验。[Android 回调定义](https://developer.android.com/reference/android/app/PendingIntent.OnFinished)
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
应用内回复/撤回不依赖预览开关。同名未知代码按各组件的完整签名分别实验性启用；零命中、歧义或签名失败时分别关闭对应组件，保留现有回退。
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

已离线核对的精确组合为名称 `8.0.79` **且**代码 `3182`；同名未知代码须按各功能分别执行完整签名唯一匹配，实验性启用条件见本手册开头。
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
- 候选唯一且校验通过时，`probe/profile-proposal.java` 给出包含版本码的画像常量与精确 `versionName && versionCode` 映射行。仍须人工核对调用链与目标版本证据后采纳。
- 若报告说 `patch: skipped, RELEASE_x_y_z_code already present and matches the probe`（历史画像可能使用旧命名），说明该精确组合已存在且声明与探针一致；这不表示真实收发已经验证。
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

- 核对生成的版本名和版本码后，将 `profile-proposal.java` 的常量与精确映射加入 `WeChatReplyProfile.java`，或先 `git apply --check .debug-artifacts/device/<时间戳>/probe/profile-proposal.patch` 再应用。不得将同时匹配改为任一匹配；不得覆盖另一个构建的已有画像。
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

模块自身的 framework 仪器测试可以独立执行，无需重启微信或 SystemUI。Android 16/API 36 应使用含同包 runner 的签名 Debug APK：先备份设备原模块，再安装 Debug；核对 `pm list instrumentation` 出现 `com.oasisfeng.nevo.xposed/com.oasisfeng.nevo.decorators.wechat.MessageIdentityInstrumentation (target=com.oasisfeng.nevo.xposed)` 后运行，不安装独立 androidTest APK。测试脚本使用 `try/finally` 恢复原模块并核对回拉哈希，且核验独立测试包及 runner 不存在：

```powershell
adb -s <设备序列号> shell am instrument --no-hidden-api-checks -w -r com.oasisfeng.nevo.xposed/com.oasisfeng.nevo.decorators.wechat.MessageIdentityInstrumentation
adb -s <设备序列号> shell am instrument --no-hidden-api-checks -w -r -e verify_wechat_profiles true com.oasisfeng.nevo.xposed/com.oasisfeng.nevo.decorators.wechat.MessageIdentityInstrumentation
```

`--no-hidden-api-checks` 仅关闭本次测试进程的隐藏 API 限制，以覆盖注入环境使用的 `Notification.mSortKey` 等字段；不修改系统全局设置。Android 16 未加该参数时可出现 `NoSuchFieldError`，不能据此把字段改名。runner 只运行模块的 framework 用例，不安装微信混淆 hook、不向真实会话发送消息，也不能代替微信端到端验收。缺少运行时 framework 签名的用例须明确计入 skipped，不得算作 passed；检查通过/跳过/失败计数和最终 `INSTRUMENTATION_CODE: -1`，adb 的退出码 0 本身不表示通过。

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
| 通知栏回复后的新来信提醒 | A → 通知栏连续回复 B1/B2 → C → D，分别检查“显示你最后回复的消息”关闭/开启，另检查补图及原本静默的通知 | 回复刷新保持静默；默认关闭时显示 C、C+D，开启时显示 B1+B2+C、B1+B2+C+D；两者均清除已回应输入历史，不新增 `FLAG_ONLY_ALERT_ONCE`；图片和原有静默策略保留。源码标记检查与声音/震动/浮动真机验收分别记录 |
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
- 图片事件及普通大图的离线已核对精确映射覆盖 8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.79/3182、3183、3200；未映射组合自动关闭。3141 hook 与描述符检查通过，实际收图/下载和 3160 真机待验收；3183/3200 的本轮证据边界见本手册开头和 8.0.79 findings。
- 应用内回复轮次的离线已核对精确映射为 8.0.72/3085、8.0.77/3141、8.0.77/3160、8.0.78/3180、8.0.79/3182、3183、3200；同名未知代码仅在本组件完整签名唯一通过时实验性启用。3141 hook 就绪，实际发送与轮次推进待验收；无匹配或歧义组合保留原轮次行为。
- 撤回身份/事件的离线已核对精确映射为上述七组；同名未知代码须独立唯一匹配完整字段、方法与继承签名，两个 hook 全装后才 ready。3141 hook 就绪，3182 分层验证见对应 findings；3183/3200 尚无本次真实消息验收。实际撤回裁剪、清除后不复活及图片回退待真实消息验收。新合成回退、SystemUI 移除原因和生命周期行为同样不能借用历史原生回复验收。
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
- 此模板描述现有**通知栏回复候选画像**：历史 8.0.72/8.0.78 版本名或版本号任一命中（`||`）的回退仍保留；同名未知代码的实验性路径须唯一完整签名。未知版本选择 `wechat-legacy` 候选，不能仅凭候选标签声称支持。应用内回复、图片、撤回的已核对精确门禁采用同时匹配（`&&`）；同名未知代码的实验性路径分别按各组件探测，不得复制通知回复的历史 `||`。
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


### 同版本名图片画像运行时探测（实验性）

图片事件与大图下载使用独立的 `image_events` / `image_download` 选择结果和候选缓存。
精确 `versionName && long versionCode` 画像优先，并且仍须通过完整签名验证；未知 code
仅枚举同版本名候选，唯一完整匹配才实验性启用。8.0.77 的 3141/3160 两个候选同时
通过时，该组件以 `ambiguous` 关闭，不按列表次序选择。8.0.79/3183 只有在当前进程
完整匹配 3182 的该组件描述符时才启用，不能据此推断另一个组件也可用。

图片事件先解析 state getter、simple-message 的继承字段 `d:int`、message 的继承字段
`field_msgSvrId:long`、全部读访问器、三个 pipeline 回调目标、路径方法与 static VFS。
8.0.79/8.0.72 的路径方法依据现有离线源码核对为实例方法；8.0.77 路径方法沿用既有
参数/返回类型校验，其 static/instance 修饰符没有新增离线证据，不作已验证声明。
所有 hook 安装完成前回调均 inert；任何安装失败使 `available=false`，失败批次回调
即使随后重试成功也保持 inert。ready 日志增加 `match=exact/experimental`。

大图组件独立解析 static kernel/service getter、唯一非 static 数据库字段、实例 query、
service base 与 service/implementation、download interface 与 implementation 的继承关系、
实例下载 getter、完整 `b(...) : int`、非抽象 `MsgIdTalker(long,String)`、callback interface 和 static WXGF decoder。探测只做反射，
不构造消息对、不查询数据、不获取服务、不触发下载或更改通知。候选正缓存写入
微信应用自身私有偏好，并在每个新进程重验；缓存不可用时仍执行现场签名探测。

现有图片预览和大图开关继续分别控制安装；本地图片优先、缩略图和等待超时回退保持不变。
失败诊断保留 `events_unavailable` / `large_unavailable` 与原因或 stage/type。
主机测试覆盖同名选择、独立匹配、错误 state/download 签名、继承拒绝与部分 hook 失败。
没有 3183 APK，未做该构建离线字节码验证、运行时 hook 安装、真实图片/消息或 OEM 行为验证；
签名匹配和主机测试通过不等于设备行为兼容。

### 2026-10-28 合成回复接收器完整预检与安装门禁

微信主进程的通知栏合成回复（已知画像、legacy 回退及同版本名未知代码的实验性候选）现在统一要求接收器继承 `android.content.BroadcastReceiver`、类为 public 且非 abstract，并具有 public 无参数构造函数；入口仍为 public、非 static、非 abstract 的 `onReceive(android.content.Context, android.content.Intent): void`。构造检查仅用反射读取描述符，不创建接收器或派发消息，对齐实际 `getDeclaredConstructor().newInstance()` 不调用 `setAccessible` 的派发约束。RemoteInput helper 和三道 static boolean 无参车载门禁的原有完整签名校验不变。

`Application.onCreate(): void` AFTER 的初始化只有在该画像接收器 hook 成功且三道门禁全部安装成功后，`MainHook.isSyntheticReplyAvailable()` 才返回 true；接收器 hook 失败保留 `hookAutoReplyReceiver failed` 诊断并关闭合成动作，不能仅凭 `profile_ready` 或门禁 ready 判为可用。原生 `PendingIntent` 回复独立于上述条件，现有聊天、会话 key、contentIntent 和功能开关条件继续生效；应用内回复、图片和撤回也各自独立。版本名与代码的既有精确/OR/legacy 规则以及同名未知代码唯一匹配规则不变。

主机回归覆盖 receiver hook 未成功而门禁已 ready、成功状态恢复，以及 abstract、无无参构造、private 无参构造和非 public 接收器的拒绝；覆盖构造与安装门禁不代表消息已送达。3183 仍无 APK 来源、SHA-256 或反编译证据，本轮未安装设备、未验证运行时 hook、真实回复或 OEM 展示。

2026-10-28 试验性 IPC 版本的设备 instrumentation 为 4/70；大量旧用例因测试 APK 与目标 APK 跨类加载器的 package-private `IllegalAccessError` 失败。单独修正后的接收器故障注入用例为 1/1 通过，但只证明直接调用的异常保护。该 IPC 路径已移除，这些结果不能当作当前画像缓存或真实微信行为验收。当前缓存的设备证据见本手册开头；完整 framework instrumentation 仍需使用最终 APK 重新运行，测试包必须在 finally 卸载并核验。
