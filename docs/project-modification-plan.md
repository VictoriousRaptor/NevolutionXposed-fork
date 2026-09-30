# 项目全局修改计划

更新日期：2026-09-30

状态：第 1、2、3 项源码、本地自动化及 33 项真机模块仪器测试已完成；真实微信 hook 与通知行为验证待完成

## 基线与范围

- 当前基线：`master`，提交 `87adbc5`，版本 3.1.3。
- 本文档统一跟踪当前已明确的六项后续工作。各专题文档保留设计依据和历史验证记录；其中记录的旧 APK 哈希、Lint 数量和设备状态不作为当前基线的验收结果。
- 计划按下列顺序实施。每完成一项，更新本文件的状态、实际验证结果及剩余事项。

| 顺序 | 事项 | 当前状态 |
| --- | --- | --- |
| 1 | 微信聊天通知回复资格与应用内回复轮次 | 源码及本地自动化验证完成；真机验证待完成 |
| 2 | 撤回消息后的通知历史裁剪 | 精确 ID 裁剪、保守行为及本地自动化验证完成；真机验证待完成 |
| 3 | 标准 SystemUI 通知与回复链路清理 | 源码及本地自动化验证完成；真机验证待完成 |
| 4 | 图片通知普通大图的完整回归 | 待验证 |
| 5 | 剩余 Lint 警告清理 | 基线已记录：35 条警告；清理待实现 |
| 6 | 微信 8.0.77 版本适配准备 | 待收集目标 APK 与精确版本信息；当前未发现本地 8.0.77 适配材料 |

## 1. 微信聊天通知回复资格与应用内回复轮次

目标：仅向真正的微信聊天通知添加回复动作，避免好友请求等非聊天通知获得回复入口。

实施内容：

1. 在修改通知、添加任何回复动作之前，根据**原始** `tickerText` 判断聊天资格：非空，且从第二个字符起包含 `:`。将判断写成可单独测试的逻辑。
2. 将同一判断用于 CarExtender 原生回复、通知现有 actions、合成回复，以及媒体、表情、文件等保留微信原布局时附加的回复动作。
3. 聊天资格不依赖微信是否提供回复 `PendingIntent` 或 `RemoteInput`。符合条件的通知仍优先使用原生回复；缺少原生对象时，仅在接收器及派发条件已验证的情况下保留合成回复。
4. 保留现有杂项渠道和语音、视频通话排除规则；不改变通知内容及消息归档的既有行为。

验收：自动化测试覆盖单聊、群聊、媒体消息、好友请求、空 ticker、无冒号 ticker，以及缺少原生回复对象但具备合成回复条件的聊天通知。所有回复入口遵循同一资格判断。

实现：`WeChatMessage.isChat()` 为所有模块回复入口的共同门禁；CarExtender 缺少回复 PendingIntent 时继续尝试现有 actions 和合成路径。合成路径还必须具备点击目标、已验证的目标会话 key 和可用的接收器画像；目标 key 随代理传递至微信接收器，缺少任一条件时不显示合成回复。非聊天通知不新增或代理回复动作，保留微信原有内容与动作。

依据：[微信回复实施计划](implementation-plan.md#chat-notification-eligibility-implemented-all-supported-wechat-versions)。

应用内回复轮次：源码已按会话记录已发送消息的匿名 ID 与时间；下一条新来信到来时清除旧展示历史，不采集 App 内回复正文。已核对微信 8.0.72 / 3085 与 8.0.78 / 3180 的离线映射；版本签名不符时保持现有行为。真实微信运行时行为仍待验证，细节见[应用内回复轮次方案](wechat-in-app-reply-round-plan.md)。

## 2. 撤回消息后的通知历史裁剪

现象：消息序列为 A → B → C（“XXX 撤回了一条消息”，其中 B 被撤回）→ D 时，通知仍显示 A、B、C、D。期望收到 C 后，通知历史删除被撤回的 B 和撤回提示 C；后续显示不再包含这两条消息。

实施与分析：

1. 沿现有撤回识别路径核对通知文本解析、私聊/群聊分类、`NotificationMessages.rebuild()` 与归档快照合并，确定旧消息 B 和提示 C 各自从哪个来源进入最终通知。
2. 先确认撤回提示中是否有稳定的目标消息 ID、服务端 ID 或可验证关联字段。只有精确关联到 B 时才移除 B；不得仅凭“撤回提示前一条消息”、相同正文或时间接近来猜测目标，以免删错并发到达的消息。
3. 在通知历史生成阶段同步剔除 B 和 C，确保被裁剪内容不会从旧归档快照重新进入通知。若当前通知本身是撤回提示，也不把 C 当作普通对话消息加入后续历史。
4. 覆盖私聊与群聊、撤回本人/他人消息、同文消息、同时间消息、连续撤回、通知归档重建、消息 ID 缺失和通知 ID 复用；无可靠关联数据时定义保守行为并记录限制。

验收：A → B → 撤回提示 C → D 后通知不再显示 B、C，A 与 D 仍正确；无目标消息标识或模糊数据时不得误删其他消息。`EXTRA_MESSAGES`、回退通知正文和相关轮次元数据保持一致，日志不写入聊天正文或联系人信息。

依据：[撤回与发送者身份记录](wechat-sender-identity-fix.md)、[应用内回复轮次方案](wechat-in-app-reply-round-plan.md)。

实现：离线核对 8.0.72/3085、8.0.78/3180 的 `NotificationItem` 与 `RevokeMsgEvent`，用会话 key 和服务端 ID 关联；过滤历史、保存删除记录、防止旧快照复活，并同步更新正文和轮次。无剩余消息时取消并阻止重发；仍在通知栏的通知按最新 token 检查后重建。缺少精确 ID 的消息保留，包括目前无法与服务端 ID 精确关联的通知栏本人回复。完整映射、图片文字回退与版本边界见[撤回通知历史裁剪](wechat-recall-history.md)。

## 3. 标准 SystemUI 通知与回复链路清理

目标：删除无消费者状态和重复转发，保留标准通知栏的回复能力及通知轮次安全约束。

实施内容：

1. `WeChatNotificationRemoval.shouldResetRound()` 仅接受点击、单条划除和清除全部对应的 `REASON_CLICK`、`REASON_CANCEL`、`REASON_CANCEL_ALL`；同步调整单元测试。
2. 从 `proxyDirectReply()` 删除未使用的 `Notification` 参数及调用实参，并删除代理 Intent 中不再消费的 `input_history` 数据。通知本身的回复历史仍由 `NotificationMessages.recordReply()` 维护。
3. 回复接收器在入口检查结果后，不再重复判断非空；转发至微信原始回复 `PendingIntent` 时只执行一次 `RemoteInput.addResultsToIntent()`，不再复制重复的 `ClipData`。保留 mention 前缀写回结果 Bundle 的处理。
4. 删除 `WeChatDecorator` 中已确认无引用的 `EXTRA_PICTURE`、`STORAGE_PREFIX` 和 `now()`；将第三方通知面板专属注释改为标准转发语义。
5. 在 `WeChatDecorator.Local.onDestroy()` 调用 `MessagingBuilder.close()`，统一注销已注册的回复、缩放和轮次重置接收器。

保留：回复代理、`attachReplyAction()`、CarExtender fallback、合成回复、actions 序列化、轮次 token、定向广播及图片样式重建时的 token 传递。

验收：单元测试确认只有三种指定移除原因重置轮次；仪器测试覆盖原生回复结果、mention 前缀、连续回复历史、图片样式 token 和移除后的新轮次。自动化构建与检查按“共同验证门禁”执行。

依据：[性能与冗余清理计划](perf-cleanup-plan.md)。

实现：移除原因已收紧为三种 SystemUI 操作；代理的未使用参数和历史已删除；原生 RemoteInput 只编码一次，mention 前缀仍写回结果。派发回调失败时不记录成功回复；无引用成员已移除，`Local.onDestroy()` 关闭 MessagingBuilder 并注销轮次重置接收器。

## 4. 图片通知普通大图的完整回归

目标：在当前代码和 APK 上补齐图片通知的运行验证，确认失败回退、通知身份及原图边界均符合设计。

执行内容：

1. 从当前源码生成使用现有 release 证书签名的 Debug APK；记录版本、文件大小、SHA-256，并验证 APK v2 签名及证书一致性。安装设备前按当次指令取得授权，安装后核对设备 APK 哈希与运行标记。
2. 验证预览总开关或普通大图开关关闭时不会主动下载；验证图片后紧跟文字、连续多图、两个会话同时收图、通知清除和进程退出时不会串图或复活旧通知。
3. 验证断网、慢网、下载超时、基础行延迟落库、WXGF/HEVC 路径及解码失败时的回退行为；确认回复动作仍保留。
4. 核对模块始终只使用 `ImgInfo2` 基础行，不读取或下载 `reserved1` 指向的原图行。记录实际图片尺寸、耗时及异常，不以旧版本的验证结果代替本轮结论。

验收：逐场景记录当前 APK 哈希、运行标记、结果及未覆盖原因；发现失败时先定位并修复，再重做受影响场景。涉及真机的安装与进程操作遵循当次用户授权；整机重启须单独取得明确同意。

依据：[普通大图计划](wechat-image-large-preview-plan.md)、[实现总结](wechat-image-preview-implementation-summary.md)。

## 5. 剩余 Lint 警告清理

目标：独立处理前述功能修复后仍存在的旧警告，避免将无关的机械清理混入回复、撤回或图片改动。

实施内容：

1. 已在当前改动上运行 `lintDebug`：35 条警告，其中 `ObsoleteSdkInt` 17 条、`UnusedResources` 14 条、`UnusedAttribute` 4 条。
2. 清理 `ObsoleteSdkInt` 的不可达版本分支，并确认受支持 Android 版本上的逻辑不变。
3. 逐项处理 `UnusedAttribute`；优先删除已废弃的红色阴影属性，确需保留的属性再移至合适的版本限定资源。
4. 对其余 `UnusedResources` 确认不存在动态名称引用后，同时清理默认及中文资源。依赖升级与 target SDK 变更另行评估，不作为本项的隐含工作。

验收：`lintDebug` 成功，记录清理前后警告差异；单元测试、Debug APK 和 androidTest APK 构建通过。

依据：[Lint 修复处理计划](lint-remediation-plan.md)。

## 6. 微信 8.0.77 版本适配准备

目标：基于准确的 8.0.77 构建产物，确定现有微信适配功能的兼容情况；不沿用 8.0.72 或 8.0.78 的混淆类名推测 8.0.77。

准备步骤：

1. 收集目标安装包或经授权设备上的 WeChat 包信息，记录 `versionName`、`versionCode`、渠道/变体、基础 APK 与 split APK 的 SHA-256。当前工作区未发现命名为 8.0.77/8077 的本地适配材料，需先取得目标构建产物。
2. 只读检查通知栏回复链路及完整签名，确认 `WeChatReplyProfile` 的接收器、RemoteInput 辅助类和门禁方法是否变化。
3. 单独定位应用内回复轮次事件入口；核对本人方向、成功/失败状态、talker、消息 ID、时间和被排除消息类型。未验证的映射不得加入 `WeChatAppReplyEvents`。
4. 核对图片通知事件及大图下载映射（目前门禁只明确支持 8.0.72/3085），并回归通知文本、撤回提示、私聊/群聊分类。各功能按自己的完整签名与版本门禁分别记录，不因一个画像通过就推定其他功能兼容。
5. 新建 `docs/wechat-8.0.77-findings.md` 记录包哈希、反编译位置、映射、变更、自动化验证及未覆盖项；为新增版本画像补充精确版本测试。

验收：形成可复核的 8.0.77 版本事实和逐功能兼容结论；只有源码与签名核对通过的功能才加入版本门禁。安装或设备运行验证按当次授权执行。

依据：[微信版本适配手册](wechat-version-adaptation-playbook.md)、[8.0.72 适配记录](wechat-8.0.72-findings.md)、[8.0.78 适配记录](wechat-8.0.78-findings.md)。

## 共同验证门禁与交付记录

- 代码修改后运行 `git diff --check`，并按项目默认要求执行 `testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest` 和适用的 `lintDebug`。
- 构建 Debug APK 时传入 `-PrequireReleaseSigning=true`，使用现有本地 release 签名配置；签名配置不可用时报告阻塞，不改用默认 debug 证书。
- 项目 `gradle.properties` 统一使用 4G Gradle 堆；Lint 按默认并行度执行。
- 交付 APK 前核对签名证书与现有 release 证书一致，并记录版本、文件大小和 SHA-256。密钥文件及密码不得进入代码、文档或日志。
- 每项完成时记录变更范围、自动化结果、适用的运行或设备验证结果，以及仍未完成的风险。设备安装、应用启动和进程操作遵循当次明确授权。

## 2026-09-27 执行记录

- 应用内回复轮次源码已实现，保留通知栏回复内容，但不采集应用内回复正文；支持离线核对过的微信 8.0.72 / 3085 和 8.0.78 / 3180，真实微信运行时行为尚待验证。
- 项目 Gradle 堆已调整为 4G；使用默认并行度执行 `testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug`，构建成功。95 个单元测试全部通过；仪器测试 APK 仅构建，未在设备上运行。
- 本次 Debug APK 为 3.1.3，大小 4,034,697 字节，SHA-256 为 `FC21265F680A46366CC85A722A175F0993D615724D69DED8C131175FFEFAEEF4`；APK v2 签名已验证，证书 SHA-256 与现有 release 证书一致。

## 2026-09-30 第 1、2、3 项执行记录

- 当前基线仍为 `master` / `87adbc5`。保留工作区原有应用内回复轮次实现及用户的文档修改；本轮完成聊天资格、精确撤回裁剪和标准 SystemUI 清理，未扩展第 4、5、6 项。
- 使用现有 release 签名，按默认门禁执行 `-PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain`，最终构建成功。106 个单元测试通过，失败及错误为 0；Debug 与 androidTest APK 构建通过，33 个仪器用例已编译但未执行。`lintDebug` 通过：0 错误、34 条警告，较 35 条基线少 1 条；其余警告仍按计划第 5 项独立处理。
- `git diff --check` 通过。新生成的 Kotlin 构建缓存目录已加入 `.gitignore`。
- Debug APK：`build/outputs/apk/debug/NevolutionXposed-debug.apk`，3.1.3 / versionCode 11，4,039,679 字节，SHA-256 `3AE372C1B9FBF0D4A25D00FFE84B275042DED1A83660945C6F21F1D754101958`。
- `apksigner verify --verbose --print-certs` 通过，APK v2 签名有效。从现有 release keystore 重新导出的证书与 APK 签名证书 SHA-256 均为 `7A707A90A36C94D231678813514D2CE815AD81FBF79558EDC67EAB4F288C4DF9`。验证日志和产物信息保存在 Git 忽略的 `.debug-artifacts/plan123-*` 文件中，未记录密码。
- 真机安装、仪器测试执行、微信回复送达、应用内回复轮次、撤回事件时序及视觉显示仍待当次授权和实际验收；本轮自动化结论不代替这些运行结果。没有精确服务端 ID 的旧消息或通知栏本人回复保留，撤回后图片暂时回退为文字，细节见[撤回通知历史裁剪](wechat-recall-history.md)。

## 2026-09-30 真机安装与仪器测试执行记录

- 用户授权安装 Debug 和测试 APK、运行仪器测试，并明确禁止重启微信或 SystemUI。设备为 PJZ110，Android 16 / API 36；整个过程没有执行这两个进程或整机的重启，微信 PID 18437、SystemUI PID 5523 在前后检查中均不变。
- 首轮通过 31/33：`emptyRecallStaysSuppressed` 因测试进程隐藏 API 限制无法反射 `Notification.mSortKey`；`replyEligibility` 在普通进程调用 `MainHook` 时缺少 compile-only 的 `XposedModule`。仅对测试进程使用 `am instrument --no-hidden-api-checks` 后通过 32/33，确认前者为测试运行条件问题，未修改系统全局设置。
- 修复 `MessagingBuilder` 的合成回复可用性检查：普通进程缺少 Xposed API 时捕获 `NoClassDefFoundError` 并关闭合成入口，保留消息重建和原生回复。仪器用例新增断言确保不暴露未经验证的合成按钮；微信版本映射及 hook 签名不变，已同步适配指南和版本 findings。
- 重新使用现有 release 签名运行默认构建门禁及 `lintDebug`：106 项单元测试通过，Debug/仪器 APK 构建成功，lint 为 0 错误、34 警告。新 Debug APK 为 3.1.3 / 11、4,039,719 字节，SHA-256 `15081AD30CAAD3026EDA6F5604C82652D2BA00919C89FF5D7C099B6330D7B7A9`；测试 APK 为 50,011 字节，SHA-256 `5689C356CE2008FD1BFFF7CD8B9443F29AA98927EC67EAD848322098AEADBA94`。
- 两包均通过 APK v2 签名验证，证书与现有 release 证书一致（SHA-256 `7A707A90A36C94D231678813514D2CE815AD81FBF79558EDC67EAB4F288C4DF9`）；两个 `adb install -r` 均成功，安装后回拉的 APK 哈希分别与本地产物一致。
- 最终运行 `com.oasisfeng.nevo.xposed.test/com.oasisfeng.nevo.decorators.wechat.MessageIdentityInstrumentation`，结果 `Identity instrumentation: 33/33 passed`、`INSTRUMENTATION_CODE: -1`。撤回序列、缺少 ID、同文同时间、本人回复/轮次元数据、通知 id 复用、空提示抑制、回复资格及原生转发用例均实际通过。
- 日志、签名、回拉包与摘要在 `.debug-artifacts/device/20260930-plan123-instrumentation/`。这是模块 framework 代码的真机仪器结果，不代表微信混淆 hook、真实收发消息、撤回事件时序或通知视觉效果已验收；这些项目仍待实际微信验证。
