# 微信 8.0.72 通知普通大图预览修复计划

日期：2026-09-15
状态：已实现并完成一次真机端到端验证；最新源码 lint 已通过，完整回归场景待完成
目标分支：`codex/wechat-image-preview-clarity`

实现总结、性能分析和当前验证证据见：
`docs/wechat-image-preview-implementation-summary.md`。

## 1. 已确认依据与目标

- 真机已证明：图片通知能正常发布，但收图时本地候选只有 65×120；用户打开聊天图片后，同一消息的基础高清候选变为 277×513。清晰度瓶颈是资源尚未落盘，不是当前采样或解码造成。
- WeKit 的 `NotificationsEvolved.kt` 提供 `WAIT_LARGE` 模式；`WeMessageApi.kt` 使用 `msgSvrId` 查询 `ImgInfo2`、调用微信内部下载服务、轮询文件落盘并处理 WXGF；`WeServiceApi.kt` 给出下载方法和相关服务的 DexKit 特征。
- `MessageInfo.kt` 直接从消息对象及父类读取 `field_msgSvrId`。`WeDatabaseApi.kt` 从 `MMKernel` CoreStorage 中取得 WCDB 数据库后执行参数化 `rawQuery`。
- 修复目标是主动获取微信压缩后的普通大图，使通知在合理时间内获得可读图片；不得请求发送方提供的原图，不得扫描账户目录，不得因迟到任务复活已取消或已替换的通知。
- 参考源码：
  - <https://github.com/Ujhhgtg/WeKit/blob/master/app/src/main/java/dev/ujhhgtg/wekit/features/items/notifications/NotificationsEvolved.kt>
  - <https://github.com/Ujhhgtg/WeKit/blob/master/app/src/main/java/dev/ujhhgtg/wekit/features/api/core/WeMessageApi.kt>
  - <https://github.com/Ujhhgtg/WeKit/blob/master/app/src/main/java/dev/ujhhgtg/wekit/features/api/core/WeServiceApi.kt>
  - <https://github.com/Ujhhgtg/WeKit/blob/master/app/src/main/java/dev/ujhhgtg/wekit/features/api/core/WeDatabaseApi.kt>
  - <https://github.com/Ujhhgtg/WeKit/blob/master/app/src/main/java/dev/ujhhgtg/wekit/features/api/core/models/MessageInfo.kt>

## 2. 固定范围与接口

- 仅支持微信 8.0.72、versionCode 3085；描述符、参数类型或数据库结构任一不符时，大图获取功能 fail closed，继续使用现有本地缩略图链路。
- 保留现有 `WeChatDecorator.image_preview` 总开关；新增 `WeChatDecorator.image_preview_large=false`。只有两个开关同时开启才允许触发普通大图获取，默认行为和升级后的现有用户均不产生新增流量。
- 设置说明明确写为“主动获取普通大图，最多等待 5 秒；不会请求原图，但微信下载任务提交后可能继续在后台完成”。不把超时描述为取消微信 CDN 请求。
- 扩展内部图片事件元数据，保存经过验证的 `msgSvrId`；不记录消息内容、私有路径或联系人标识。没有有效 `msgSvrId` 时禁止主动请求。
- 新增包内版本配置与下载组件，职责分别为：8.0.72 映射、数据库只读访问、基础行选择、下载提交、文件落盘观察。无公共 Java API 变更，不引入 WeKit、DexKit 或 WCDB 编译依赖。
- 继续使用现有 `BigPictureStyle + Bitmap`、候选评分、像素上限、通知令牌和连续性校验；不移植 WeKit 的 FileProvider、消息历史、通知接管、回复或已读功能。

## 3. 分阶段实现

### A. 8.0.72 映射验证

- 扩展现有离线探针，对 8.0.72 全部 dex 使用以下锚点定位并输出完整描述符：
  - 下载方法：`ModelImage.DownloadImgService` 与 `] add failed, task already done`。
  - `msgIdTalker` 类型来源：`MicroMsg.ChattingDataAdapterV3`、`[handleMsgChange] isLockNotify:`、`msgIdTalker`、数字 100、boolean 返回值。
  - CoreStorage getter：`MicroMsg.MMKernel`、`Kernel not null, has initialized.`、`mCoreStorage not initialized!`。
  - 数据库包装类：`MicroMsg.SqliteDB` 与 `sql is null `。
- 对每个定位强制唯一命中，并验证：下载方法参数数目和类型、返回类型、声明类构造方式、回调接口位置、`msgIdTalker(long, String)` 构造函数，以及消息对象继承链中 `field_msgSvrId: long`。
- 先生成只含诊断的 Debug 构建：捕获数据库是否就绪、`ImgInfo2` 查询列是否存在、同一 `msgSvrId` 的行数和结构关系，只记录布尔值、计数、尺寸和匿名数值 ID。此阶段不得调用下载方法。
- 基础行选择规则固定为：查询所有 `msgSvrId=?` 行；唯一行直接作为基础行；多行时仅接受唯一一个 `reserved1` 指向另一行的行作为基础行；其他情况判为歧义并放弃。生产路径绝不按 `reserved1` 查询或下载其目标行。

### B. 只读数据库与消息身份

- 图片消息事件从现有 `com.tencent.mm.storage.f9` 对象读取 `field_msgSvrId`，沿父类查找并验证类型为 `long`；将其与 talker、现有 messageId 和事件 key 一起存入有界索引。
- 在微信主进程尽早 hook CoreStorage getter，从返回对象中定位数据库包装字段和返回 WCDB SQLiteDatabase 的无参方法；若 hook 前已经初始化，首次图片请求只允许调用已验证的静态 getter补获一次。
- 数据库调用全部使用反射和参数绑定，只执行固定的 `SELECT id, msgSvrId, msgTalker, bigImgPath, hevcPath, midImgPath, offset, totalLen, reserved1 FROM ImgInfo2 WHERE msgSvrId=?`。结果通过 Android `Cursor` 读取并及时关闭。
- 数据库未就绪、表或列缺失、异常、多行歧义、talker 不一致、`localId <= 0` 均直接回退，不重试其他账户或扫描 `MicroMsg` 目录。

### C. 普通大图获取

- 请求开始时先检查基础行自己的 `bigImgPath`、`hevcPath`、`midImgPath`。只解析基础行路径，使用现有微信 VFS resolver；多个已存在文件按实际可解码尺寸选择最佳者，不根据字段名猜测质量。
- 已有可读大图则不联网，直接进入现有安全解码和发布流程。
- 本地无合格大图且 opt-in 开启时，使用基础行 `localId` 与 talker 构造 `msgIdTalker`，调用已固化的下载方法。调用参数保持经 8.0.72 验证后的形状；任何差异都禁用大图模式，而不是猜测参数。
- 严禁读取或调用 `reserved1` 指向行的 `localId`。Debug 日志记录 `source=base_row original_row_followed=false`，不记录路径或 talker。
- 采用非阻塞定时状态机观察基础行：首次 50ms，随后每 100ms，截止时间沿用当前请求开始后的 5 秒。最多 4 个并发大图任务、每消息一次提交、相同 `msgSvrId` 去重；不在现有图片解析 worker 中做阻塞轮询。
- 完成条件同时要求 `offset == totalLen`、`totalLen > 0`、基础行候选文件存在且非空，并通过图片格式/尺寸检查。不要相信单独的完成标志或最终路径字符串。
- 文件经 VFS 读取；普通 JPEG/PNG/WebP 直接解码。若检测到 WXGF，则调用已验证的 `MMWXGFJNI.wxam2PicBuf(byte[], int, int)`，失败时回退缩略图。输入、解码位图和发布位图继续受现有 20 MiB 源文件及 1,048,576 像素上限约束。
- 五秒内成功时发布普通大图；超时、失败或通知代次失效时使用当前最佳本地缩略图。截止后不做迟到升级，不再次通知。微信内部请求可能继续完成，但结果只能供后续正常微信行为或未来通知本地命中。

### D. 通知与生命周期安全

- 下载任务绑定当前请求 token、通知 key、talker、`msgSvrId` 和事件 key。发布前重新验证活动通知 token 与 pending 实例，沿用已经真机验证的 continuity 规则。
- 新消息替换、跨会话复用通知 ID、连续多图歧义、通知 cancel/cancelAll、会话已读、模块关闭或微信进程退出时立即使任务失效并停止轮询。
- 失效仅阻止后续读取和发布，不宣称撤销已交给微信的 CDN 任务。不得删除微信缓存文件或修改 `ImgInfo2`。
- Debug 诊断仅记录阶段、匿名请求 ID、基础行选择结果、提交返回值、轮询耗时、源尺寸、WXGF 转换结果和回退原因；Release 编译产物保持无 `NX_IMAGE` 诊断字符串。

## 4. 测试与验收

- 单元测试：父类字段读取；基础行唯一/多行唯一/多行歧义；`reserved1` 永不成为下载目标；参数签名拒绝；任务去重、并发上限、超时、取消、迟到完成；普通格式和 WXGF；文件写入中变化；低清回退。
- 回归测试：图片后紧接文字、连续两图、两个会话同时收图、通知被清除、通知 ID 复用、零时间戳、回复 action 保留、预览总开关关闭、主动大图开关关闭。
- 本地门禁依次执行 `assembleDebug`、`testDebugUnitTest`、完整 `lintDebug`；构建成功后验证 APK v2 签名、版本和 SHA-256，再安装设备。不得在编译失败或 lint 最终状态未知时开始新一轮真机验收。
- 真机先运行“只诊断、不下载”版本，证明 8.0.72 映射和基础行选择；再运行主动大图版本。每轮安装后校验设备 APK 哈希，重启微信并确认模块、profile、数据库和图片下载映射 marker。
- 网络场景覆盖 Wi-Fi、移动网络、断网和慢网。对含原图的消息，在测试前后只核对原图行文件存在状态和实际调用目标，证明下载调用始终使用基础行；不打开聊天、不点击图片，避免混入微信自身下载行为。
- 成功标准：单图通知在 5 秒内显示明显高于 65×120 的基础行图片；日志证明 `original_row_followed=false`；无串图、通知复活、重复提交、主线程阻塞或回复动作丢失。若基础行请求仍只能得到 65×120，停止发布结论并保留诊断，不升级到原图行。

## 5. 交付边界

- 本轮只改微信 8.0.72 图片链路、相关设置、探针、测试和本文档；不适配 8.0.78 图片下载，不改回复链路，不修改 `docs/perf-cleanup-plan.md`。
- 参考 WeKit 的行为与运行时锚点，独立实现当前项目所需最小代码；不直接复制大段 GPL 源码，许可证兼容性未确认前不做代码搬运。
- 完成前不提交、不推送、不发布 Release。交付时报告源码变更、测试数量、lint 最终结果、APK SHA-256、设备运行标记和每个真机场景的实际证据。

## 6. 当前未完成事项

1. 安装最终纯策略提取后的 APK，重新校验设备 APK SHA-256、版本和运行标记。
2. 完成开关关闭、连续多图、多会话、通知清除、断网、慢网、WXGF/HEVC 和进程退出回归。
3. 最终确认 APK v2 签名、版本号、文件大小和 SHA-256。

已完成的本地门禁：

- `testDebugUnitTest`：47 项，0 failures，0 errors。
- `assembleDebug`：通过。
- `lintDebug`：通过，0 errors，60 warnings；新增图片链路文件无 lint 项。

详细实现和性能分析见 `docs/wechat-image-preview-implementation-summary.md`。
