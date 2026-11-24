# 图片身份核查与语音自动转文字

## 微信 8.0.80 / 3200

新版语音使用 w81.w0 及新的缓存和媒体接口；图片事件、普通下载与中图路径分别完成离线核对。完整描述符和验证范围见 [8.0.80 核对记录](wechat-8.0.80-findings.md)。

本轮基线为 `f897cdf6f3be571210ee0ffcc1768c7fce9d700c`，仅参考上游 `3ffd54908d20c4159c988a71fb9c35a641ce2828` 的功能和接口，不合并其历史、布局、发布流程或 APK。

## 图片覆盖审计

| 需求 | 当前实现和上游差异 | 本轮处理 |
| --- | --- | --- |
| 精确关联 | 现有 NotificationItem 会话／服务端 ID、图片事件索引和 ImgInfo2 查询已关联本地 ID；上游从消息对象派生附件描述符 | 保留现有链路，修复下载轮询完成条件仅比较图片行 ID 的缺口；同一任务复用也须核对本地 ID、服务端 ID 和会话 |
| 中图下载 | 现有 `MsgIdTalker(long,String)` 使用本地消息 ID，调用微信正常图片下载；上游也调用正常下载服务，但原有尺寸判断会跳过尺寸足够的缩略图 | 修复触发条件：缩略图或未知质量候选即使覆盖显示尺寸也进入基础行查询和中图请求；已有足尺寸 HD 候选不重复请求，不主动下载原图 |
| 原生解码 | 已有 `MMWXGFJNI.wxam2PicBuf(byte[],int,int): byte[]` 及有界转换缓存 | 已覆盖，保留普通解码失败后的回退；不引入上游解码落盘及跨进程文件授权路径 |
| 异步刷新 | 现有通知代次、活动卡片及图片身份校验 | 保留；图片结果不能重新发布旧轮。已有文件缺少本地 ID 时仍可作精确候选，但不启动下载 |

新增回归 `completedRowMustRetainBothMessageIdsAndConversation` 已在修复前失败：相同图片行 ID、不同本地消息 ID 被误判完成。修复同时拒绝服务端 ID 或会话变化。现有连续图片、重复内容、缓存与晚到结果测试继续执行。

## 语音行为

- `WeChatDecorator.voice_auto_transcribe` 默认 true，schema 13；缺失值使用默认，显式本地选择优先，其次保留远端选择。迁移不预填缺失本地键，避免覆盖已有远端 false。
- 新安装／升级默认开启；开关经既有偏好同步保存，微信进程初始化时读取一次，与图片设置一致；修改后重启微信生效，不要求即时响应。关闭但未重启时沿用旧快照；重启后关闭状态不安装语音 hook、不发起转写，重新开启也不扫描旧通知。
- 仅观察微信主进程中准确画像的服务端消息 ID getter AFTER；只捕获 type 34、isSend 0 的消息。捕获不触发转写，不遍历聊天历史。只有新的、带已核验 NotificationItem 会话／ID 的通知才请求转写；消息首次代次必须等于当前通知代次，因此关闭期间见过的消息重新开启后被原样重投也不会补转写。
- 账号、会话、本地 ID、服务端 ID、文件标识及当前适配器实例一起约束任务。原生文件名 getter 可能递归调用服务端 ID getter，因此观察路径有线程局部重入保护。
- 优先使用微信 VoiceTransText 缓存，额外校验缓存 `field_msgId`。未命中时核对 voiceinfo 的文件标识、两种消息 ID、会话及接收完成状态，再检查微信原生语音文件路径与 VFS 可用性。适配器统一接受微信的接收完成状态 99 或已达标的接收字节数。同步载荷可能直接落盘，字节计数仍为零。最多检查 20 次，间隔 1 秒，等待微信原生接收流程。
- 单个转写在途，任务记录最多 64 条、存活 30 分钟，转写超时 60 秒。微信原生事件忙／失败、空结果或超时均保留原语音占位；不取消微信全局转写服务，不无限重试。旧回调不能完成后续任务。
- 完成后在原消息条目追加一行普通文本，最多 4,000 UTF-16 单元且不截断代理对；不解析 Markdown，不创建新消息、不调整顺序、发送者或附件，不标记已读，不播放声音。
- 刷新需当前账号、会话实例、原消息轮次、消息 ID、撤回记录和本地发布状态同时有效。更新存活归档后，仅静默重发身份匹配的活动卡片；首次头像延迟发布读取更新后的归档。不存在活动卡片时不自行创建通知。
- 回复结束旧轮、通知取消、消息撤回、账号切换均让相关任务失去发布资格；进程退出销毁队列及捕获索引。进程结束不持久化任务或模块转写结果。

## 适配与诊断

仅 `versionName=8.0.79` **且** `versionCode=3183/3200` 分别匹配。完整接口表、APK 来源及 SHA-256 见 `wechat-8.0.79-findings.md`。不进行语音同名版本探测；任一必需签名失败关闭语音能力，保留图片、原通知和回复。

`WeChat.Voice` 安装日志为 `adapter_ready code=…` 或 `adapter_unavailable type=…`；Debug 任务日志只包含 `cache_hit`、`requested`、`audio_unavailable`、`expired_or_invalidated`、`completed` 或异常类别。不输出账号、会话、文件名、语音内容或转写正文。`completed` 只表示转写结果通过任务资格检查，不是实际 OEM 通知视觉验收。

## 验证边界

本轮分别执行 APK 哈希复算、离线描述符核对、主机／Robolectric 测试和 release 密钥签名构建。Debug 描述符入口增加完整语音画像检查，但本轮没有安装 APK 或运行设备测试。两版运行时 hook 安装、真实私聊／群聊转写、微信服务联网结果、中图实际下载、特殊图片实际解码与 OEM 通知表现仍待真机验收。构建或签名成功不能代替这些验证。

本轮不增加表情、动图、语音播放器、气泡布局或新的 SystemUI hook。

### 本轮执行结果

- 完整主机测试 367 项通过，0 失败、0 错误、0 跳过；包含 API 26／28 消息与附件往返、转写跨新消息保留／跨结束轮次隔离，以及关闭期间旧消息重投资格回归。
- `-PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest` 通过；沿用仓库既有离线 Robolectric init 配置，将临时目录及 SDK jar 指向本地忽略目录。
- Debug 和 androidTest 均经 apksigner 验证，与本轮从现有 release keystore 导出的证书一致，16 KB zipalign 检查通过。版本保持 3.5.0／18，不创建新发布。
- Debug APK SHA-256：`5eb8b8ce9782af75edbb536733f91a370b8f1d3910099d4c0255e99e99ee419e`。本地交付包、构建日志、签名核验记录与隔离 lint 材料位于 `.debug-artifacts/voice-transcription-20261115/`，不加入 Git。
- 首轮测试的临时目录／Robolectric 锁文件访问失败已经通过既有离线配置解决；图片新增回归的修复前失败单独保留，不混同于环境故障。原工作区 lint 分析被中止后转至源码一致的隔离副本。
- 最终 `lintDebug` 通过：0 errors、37 warnings。隔离副本与当前工作区的 163 个源码及构建输入逐文件 SHA-256 一致；未用修改源码或构建规则规避 lint。

### 2026-11-15：尺寸足够的缩略图仍请求中图

图片清晰预览开启、消息身份及版本画像验证通过时，候选质量低于 `QUALITY_HD`（缩略图或未知质量）即使覆盖显示区域，也必须进入 ImgInfo2 基础行查询及普通图片下载判断。外层查询入口与内层提交入口统一使用 `ImagePreviewPolicy.shouldRequestNormalImage`；查询后若已找到覆盖显示区域的 HD 文件，则直接使用，不再下载。没有候选或 HD 尺寸不足时沿用原请求行为。既有 `largeSubmitAttempted`、任务去重及身份校验继续限制提交。

下载接口、compressType=0、原生解码、版本门禁均不变。保留图片开关、清晰预览开关、默认 1500 ms 的可配置等待及超时回退；本次不延长后台跟踪、不调整输出分辨率、不新增 SystemUI hook 或通知布局。下载未在等待窗口内完成时仍可能显示缩略图，因此此修复不承诺最终清晰度与上游一致。

诊断沿用 `large_lookup`、`large_submitted`、`decoded` 的 quality/sourceSize/output 状态，不增加消息正文或附件路径日志。回归覆盖等于／大于显示区域的缩略图、未知质量、足尺寸 HD 不请求、欠尺寸候选及无有效尺寸请求。`displaySizedThumbnailStillRequestsNormalImage` 在旧尺寸策略下先失败，再应用质量判断修复；真实下载与通知视觉效果尚未做真机验收。

本次触发条件修复的主机验证：369 项测试通过，0 失败／错误／跳过；release 签名要求下的 Debug、androidTest 构建通过，两个 APK 与既有 release 证书一致，16 KB 对齐检查通过。交付包为本地忽略目录 `voice-transcription-20261115/NevolutionXposed-thumbnail-fix-debug.apk`（相对于 `.debug-artifacts/`），SHA-256 为 `321200937602cf6c7ff83c6a1bbac9a577223378916ef08f84b9df26a858bfb8`。未安装设备，未提交或推送。
最终 lintDebug 通过：0 错误、37 警告；隔离副本的 163 个源码及构建输入与当前工作区逐文件哈希一致。

## 2026-11-16 开关简化与真机验证结果

按用户最新要求，本轮开关不再要求即时响应；语音与图片均采用微信进程初始化时的偏好快照，修改后重启微信生效。删除语音专用偏好监听、adapter enabled/epoch 和 queue enabled 状态；保留账号、消息、会话、适配器实例、轮次、任务对象及超时校验。`clearRejectsOldCallbacksWithoutRetiringNewTask` 覆盖清理后旧回调与同 ID 新任务隔离。偏好已按用户要求另行写入本地记忆补充，不进入仓库。

- 主机测试 370 项通过，0 失败／错误；Debug、androidTest、Release 使用 `-PrequireReleaseSigning=true` 构建并核验证书和 16 KB 对齐。
- 最终 lintDebug 0 错误、37 警告；隔离副本 163 个源码及构建输入逐文件哈希一致。
- 3183 真机 framework runner 109/109 通过，0 跳过。首次完整描述符检查发现 AUDIO 字段假设错误，改用精确 typed valueOf 后，实机描述符检查 42 项通过；微信进程实际出现语音 adapter_ready 与图片 large_ready exact 状态。
- 独立 androidTest APK 安装长时间无返回，结束核实唯一的该安装客户端后执行清理。主包内 runner 已完成上述自动验证；不能把安装等待视为测试通过。原有版本码 17 的备份恢复被系统降级保护拒绝，最终以本轮同证书、同源码版本码 18 的非 Debug Release 覆盖。回读安装 APK 哈希与交付 Release 一致，DEBUGGABLE 不存在，主包测试 runner、独立测试包 path/package/instrumentation 均核验不存在。
- 已按授权强行停止并启动微信，未重启整机。最终手机保留本轮 Release 3.5.0/18。
- 等待期间未收到可验证的新语音／图片事件：转写 requested/completed、图片 large_submitted/large_wxgf/decoded 均未命中。真实私聊／群聊转写、中图实际下载、特殊图片解码及通知视觉结果仍未验收；3200 亦未实机验证。接口就绪不代替真实功能验收。

本地原始证据位于 `.debug-artifacts/voice-transcription-20261115/device-switch/`，重点为 `framework-resumed.txt`、`descriptors-resumed.txt`（修复前失败）、`descriptors-enum-fixed.txt`、`runtime-resumed.txt`、`features-live.txt`、`final-cleanup.json`。原脚本失败结果保留，最终清理状态以独立 `final-cleanup.json` 为准。

2026-11-19 复核上游 `3ffd549` 后发现，早期“足尺寸 HD”判断同时涵盖高清缩略图。现已独立标记此类候选，3183 另在消息身份核验后尝试从 MID_IMAGE 描述符取图。管理器参数、超时及诊断差异见适配手册同日章节。

2026-11-21 核对两版微信的语音同步接收分支后，适配器统一识别接收完成状态 99。新增的零字节计数回归在原判断下失败，修正后 383 项主机测试通过。Debug 同证书构建并覆盖安装，微信 3183 主进程重启后记录 `adapter_ready code=3183`。本次真机证据覆盖 hook 安装，真实新语音的转写和通知刷新仍待验证。
