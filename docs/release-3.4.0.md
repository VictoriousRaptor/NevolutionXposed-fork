# NevolutionXposed 3.4.0

日期：2026-10-24，2026-10-27 更新。Android `versionCode`：17（上一版本 3.3.1 为 16）。设置 schema：11。

2026-10-27 合并验证（下述审查精简前）：图片布局修复与私聊头像/展开应用名改动一起运行 `testDebugUnitTest assembleDebugAndroidTest assembleRelease --offline`，强制使用现有 release 签名。212 项主机测试全部通过，失败、错误、跳过均为 0；framework 测试 APK 编译成功，包含新增的包查询失败场景及既有外观回归，未安装执行。Release 的 `lintVitalRelease`、签名证书比对及 zipalign 检查通过，实际 APK 为 3.4.0 / 17、minSdk 26、非 debuggable。完整 Lint、真实微信收图、隐藏 API 访问及 SystemUI/OEM 展示未在本轮验收；不推送、不安装。产物与原始验收记录仅存于本地忽略目录。

## 2026-10-27 群聊样式最终真机验证

213 项主机测试、68/68 项 Android 17 / API 37 真机框架用例及 40 项微信描述符检查全部通过；Debug、androidTest、Release 均使用既有 release 证书，签名和 zipalign 检查通过，Release 的 lintVitalRelease 通过。首轮一项标题断言按 framework 原生标题合成规则修正，完整重测通过，不改变标题业务逻辑。所需图片接口/布局失败直接保留 `[图片]`，不尝试替代接口；隐藏接口的局部 Lint 声明与完整签名/失败边界见[通知外观说明](wechat-notification-appearance.md)。

精确测试包和 runner 均已移除；手机最终为同版本非 debuggable Release，回拉哈希与最终本地 APK 一致。未主动重启整机、微信或 SystemUI，未发送真实消息；framework 使用 `--no-hidden-api-checks`，不代表真实 LSPosed 注入或微信/OEM 通知视觉验收，未运行完整 Lint。以下较早验证记录保留其原始范围。

## 变更

- 修复图片解码成功后，微信进程无法查询模块包导致展开布局创建失败的问题。使用框架提供的模块 ApplicationInfo 和完整反射构造签名创建模块布局，保留 minSdk 26、fitStart 及回复动作；构造不可用时保留文字通知。
- 私聊通知头像使用微信原头像交由系统会话布局呈现。取消应用名修改、设置及分隔符隐藏动作，schema 11 清理本地/远端旧键；撤销额外的群名行和首位发送者标题改写，应用名及内容标题沿用系统模板。作用范围、签名门禁及尚未完成的真机展示验收见[通知外观说明](wechat-notification-appearance.md)。
- 群聊通知主图标在折叠、展开时使用原群头像和系统微信角标；群聊图片补图保留群聊文本的会话标题、发送者和消息排版，追加完整 `fitStart` 图片，并清理视图复用时的旧预览。框架用例已在真机通过，真实微信/OEM 外观待验收。
- 图片文件等待时间可配置为 100–30000 毫秒，默认 1500；文件重试、画质等待和普通大图观察共用每请求截止点，并受图片事件身份期限限制。下载首参使用图片行 `id`，消息对独立使用 `msglocalid`。
- Markdown 代码样式恢复按完整签名选择旧返回值布局或 Android 17 消息字段布局；不混用两代调用链，签名或去优化失败时沿用系统清理结果。
- 私聊通知未展开时使用对方名字作为会话标题：`MessagingStyle` 保留现有会话名，同时继续标记为非群聊。群聊标题规则不变；跳过装饰的媒体或非聊天通知继续沿用原处理路径。实际 Android/OEM 折叠布局待真机确认。
- 通知栏回复 B 后保留本轮来信 A 和回复 B；下一条符合条件的原始来信 C 到达后，默认显示 C，开启“显示你最后回复的消息”时显示 B+C。发送前仍请求原生已读，失败不记录 B；用户明确移除仍清理对应通知代次，迟到回调不复活已移除通知。详见[通知历史规则](notification-history-policy.md)。
- SystemUI 移除回调兼容四参数与三参数签名，安装失败可重试；原生已读动作绑定当前会话实例和通知代次，不继承旧通知动作。会话身份缓存最多保留 256 个最近使用的 ID，活跃归档中的会话保留。
- WXGF 输入限定读取长度并检查 EOF，拒绝空、截断或多余数据；图片路径探针仅在 Debug 且显式开启诊断时运行。移除无当前生产者的旧 Zoom 动作及遗留分支，保留现代图片预览、动作身份和撤回清理。完整兼容边界见[适配手册](wechat-version-adaptation-playbook.md)。
- 完整移除独立媒体通知增强：实现、同步取色、专用布局、设置入口、全局反射注解 hook、Palette 依赖及功能测试；本地和远端设置迁移时清除旧媒体键。
- 保留微信图片、视频、文件、表情消息的原生布局与回复动作，以及 SystemUI 的通知移除广播、上下文捕获、NLS 诊断和 Markdown 链路。
- CarExtender 缺少会话时保留原删除动作；会话存在但 participants/messages 不完整时，仍允许使用已有原生 `on_read`，不扩大划除已读的条件。
- 旧渠道迁移原样保留静音、灯光及振动开关；API 35+ 的非空振动 effect 沿用原值，其余复制 pattern 后恢复显式振动开关。
- WXGF 质量等待期间复用未变化候选的转换结果。整个加载器共用单槽，最多保留 20 MiB；匹配请求身份、路径、大小和修改时间，候选仍须出现在当前路径列表。取消、替换、失败或生成最终 Bitmap 后关闭缓存写入，阻止迟到结果复活。

微信回复、图片、应用内回复与撤回的版本映射、完整签名校验和未知版本回退不变。详细 hook 及启用条件见[适配手册](wechat-version-adaptation-playbook.md)。

## 待办与兼容边界

- 自己的头像来源仅登记为[未确定待办](project-modification-plan.md)，没有修改头像选择、读取或缓存代码。
- 2026-10-24 两处共用图片行 `id` 的历史实现已于 2026-10-26 撤销，当前 SQL 单独读取 `msglocalid`。8.0.79 的 ID 语义已离线复核，其余版本的新增列及真实下载仍需独立验收，见各版本 findings。
- Android/OEM 实际通知表现、微信已读/回复/下载及新旧版本覆盖安装仍需独立真机验收。构建、签名和缓存契约测试不代表这些行为通过。

## 验证记录

### 2026-10-27 审查精简

- 审查 `5bb56fa..0ed9f71` 的全部 3.4.0 改动，移除图片等待的两个测试专用旧重载、外观安装的未用参数及重复模板常量；保留完整签名画像、回复延迟清理边界和缓存关闭保护。
- 本地设置初始化只迁移已有值及历史必需值，避免提前补默认值覆盖远端关闭选择；新增顺序回归先在旧代码下失败，再于修复后通过。其他设置默认值与历史强制迁移规则不变；本次取消应用名功能通过 schema 11 清除旧设置。本轮未执行设置 UI 首次加载与异步服务绑定的设备时序验证。
- 使用 `-PrequireReleaseSigning=true` 离线完成 `testDebugUnitTest assembleDebug assembleDebugAndroidTest`：213 项主机测试全部通过，无失败、错误或跳过；Debug、androidTest 两包签名均与现有 release 密钥证书一致，zipalign 检查通过。Debug 实际版本为 3.4.0 / 17、minSdk 26。
- 完整 `lintDebug` 在 125 个源码及构建文件哈希一致的本地副本运行，包含主源码、AndroidTest 和 UnitTest 分析：0 errors / 3 warnings。两项 `DiscouragedApi` 来自按名称查询隐藏的系统应用名及分隔符 ID，保留现有兼容逻辑；另一项为既有 Debug 像素复数文案 `PluralsCandidate`。画像生成脚本回归通过。
- 本轮未重建 Release、安装 APK、执行 framework/真实微信测试或推送。日志、证书核验和 Lint 报告保存在本地忽略目录 `.debug-artifacts/review-3.4.0-20261027/`。

### 2026-10-25 Release 构建

- 使用 `-PrequireReleaseSigning=true` 离线执行 `testDebugUnitTest assembleRelease`，构建成功。183 项主机测试结果无失败、错误或跳过；同一功能源码此前已执行，本次测试任务由 Gradle 复用。
- 实际 Release APK 包名为 `com.oasisfeng.nevo.xposed`，版本为 3.4.0 / 17，`debuggable=false`。签名证书与现有 release 密钥一致，签名和 zipalign 校验通过。
- Release APK 的 DEX 和资源中未检出独立媒体实现、Palette 类及专用布局。构建附带的 `lintVitalRelease` 通过，本次没有执行完整 Lint。
- APK 为 3,491,115 字节，SHA-256 为 `3c2e06d67ab572c875efe64130ed3ed1838484b8fb42e804668371a29130dac4`；产物及验证记录保存在本地忽略目录 `.debug-artifacts/release-3.4.0-20261025/`。
- 私聊标题调整未新增 hook、版本画像或功能开关。本次没有安装 APK、运行设备仪器测试或验证微信/OEM 实际通知布局。

### 2026-10-24 历史构建

当日 3.4.0 / code 17 已使用 `-PrequireReleaseSigning=true` 离线重新构建：

- 167 项主机单元测试全部通过，失败、错误、跳过均为 0。
- Debug 和 androidTest APK 构建通过；实际 Debug 包内版本确认为 3.4.0 / 17。
- 两包签名证书均与现有 release 密钥一致，签名和 zipalign 校验通过。
- 实际 Debug APK 的 DEX 和资源中未检出独立媒体实现、Palette 类及专用布局。
- 本次仅修改版本与文档，没有重跑 Lint；升级前同一功能源码的应用源码 Lint 为 0 errors / 31 warnings，两个测试源码 Lint 分析任务未运行。

本地 Debug APK 为 4,686,626 字节，SHA-256 为 `e8b795ea7bfe54af472ebca9f3cb06589073698e1d4e9a50bdfe86790bd8c9bd`。构建、签名和产物检查记录保存在忽略目录 `.debug-artifacts/version-3.4.0-20261024/`，旧版证据继续保留。

没有安装 APK、运行设备仪器测试、重启微信/SystemUI 或发布 GitHub Release。此前设备记录不作为本版本的新验收证据。
