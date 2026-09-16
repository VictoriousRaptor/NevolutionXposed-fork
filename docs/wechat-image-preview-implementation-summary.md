# 微信图片通知预览实现总结与性能分析

日期：2026-09-16
分支：`codex/wechat-image-preview-clarity`
基线：`0077b3d`（本地 `master`）
目标版本：微信 8.0.72 / 3085

## 1. 当前状态

本轮已经实现并完成一次真机端到端验证：

- 通知在收到图片后主动查询 `ImgInfo2` 基础行，并请求微信的普通压缩大图。
- 真机在 253ms 内得到 `277×513` 普通大图，在请求开始后约 460ms 发布通知。
- 日志确认没有跟随 `reserved1` 指向的原图行。
- 实际展开通知中的图片文字清晰可读。

当前仍未完成：

- 最新源码构建出的 APK 尚未重新安装到真机。
- 开关关闭、连续多图、多会话、通知清除、断网、慢网、WXGF/HEVC 等回归场景。

## 2. 分支修改总览

### 2.1 设置与安装入口

- 保留 `WeChatDecorator.image_preview` 总开关。
- 新增 `WeChatDecorator.image_preview_large`，默认 `false`。
- 只有图片预览总开关和普通大图开关同时开启时，才安装 `WeChatImageDownloader`。
- 设置 schema 从 3 升级到 4，旧用户迁移后默认关闭普通大图请求。
- 设置说明明确标注最多等待 5 秒、不请求原图，以及提交后微信任务可能继续在后台完成。

相关文件：

- `src/main/java/com/oasisfeng/nevo/decorators/wechat/WeChatDecorator.java`
- `src/main/java/com/oasisfeng/nevo/xposed/RemotePreferenceStore.java`
- `src/main/res/xml/main_preference.xml`
- `src/main/res/values*/strings.xml`

### 2.2 图片事件和消息身份

- `ImageEventIndex` 从仅保存路径升级为保存“路径 + 质量等级 + `msgSvrId`”。
- 同一消息的重复事件会按路径合并，质量取较高值，排序后最多保留 4 个候选。
- 新增 `putIdentity()`，允许先保存 `msgSvrId`，随后再补充图片路径。
- `WeChatImageEvents` 从图片消息对象及父类读取 `field_msgSvrId`。
- 新增对 `com.tencent.mm.storage.f9.I0(): long` 的 hook，用于在图片管线事件之外尽早获得稳定消息身份。
- 图片路径按高清缩略图和普通缩略图标记质量，不再依赖路径到达顺序决定候选优先级。
- revision 更新为 `image-events-9`。

相关文件：

- `src/main/java/com/oasisfeng/nevo/decorators/wechat/ImageEventIndex.java`
- `src/main/java/com/oasisfeng/nevo/decorators/wechat/WeChatImageEvents.java`

### 2.3 候选选择、等待和解码

- 提取 `ImagePreviewPolicy`，集中处理候选评分、目标覆盖、5 秒等待、尺寸适配和采样率。
- 目标尺寸优先读取系统 `notification_big_picture_max_width/height`，缺失时回退到屏幕宽度和半屏高度。
- 单张图片硬上限为 1,048,576 像素，使用 `ARGB_8888`，约 4 MiB 位图数据上限。
- 候选按实际解码尺寸和来源质量比较，不因路径顺序或先落盘而停止寻找更优候选。
- 普通大图完成且已有 HD 候选后，不再为了等待“覆盖通知目标”而空等剩余时间。
- 增加 `publishing` 状态，阻止同一请求被并发调度时重复解码和重复发布。
- WXGF 文件只在 Android 解码器无法识别时调用微信 `MMWXGFJNI` 解码。

相关文件：

- `src/main/java/com/oasisfeng/nevo/decorators/wechat/ImagePreviewPolicy.java`
- `src/main/java/com/oasisfeng/nevo/decorators/wechat/ImagePreviewLoader.java`

### 2.4 普通大图下载

`WeChatImageDownloader` 只支持微信 8.0.72 / 3085，所有映射在安装时验证：

- `msgSvrId` 通过固定参数化 SQL 查询 `ImgInfo2`。
- 查询只读取基础行自身字段，绝不根据 `reserved1` 查询或下载原图行。
- 单行结果直接作为基础行；多行结果只接受唯一一个指向其他行的基础行；歧义时失败关闭。
- 下载服务链：`w85.n0.c(n70.y.class) -> m70.e.Bh() -> n70.x/l11.j`。
- 下载方法：`l11.j.b(long, MsgIdTalker, int, Object, int, n70.w, int, boolean): int`。
- 返回码大于等于 0 才视为提交成功。
- 最多 4 个并发下载任务，同一 `msgSvrId` 去重。
- 首个轮询在 50ms，之后每 100ms 检查基础行的 `offset`、`totalLen` 和文件状态。
- 请求 token 变化或通知请求失效时取消模块侧观察。

相关文件：

- `src/main/java/com/oasisfeng/nevo/decorators/wechat/WeChatImageDownloader.java`

### 2.5 通知连续性和诊断

- `ImageNotificationIdentity` 要求活动通知令牌、会话、唯一图片事件、消息前缀和图片数量全部一致。
- 后到的文字消息可以保留同一图片和回复 action；通知被移除、替换或出现歧义时失败关闭。
- `MainHook` 增加 `notify_before`、`notify_decorated`、`notify_outgoing` 三个 Debug 追踪点。
- Debug 日志只记录阶段、匿名请求 ID、尺寸、质量和布尔结果，不记录消息正文、talker 或完整私有路径。
- 诊断 revision 为 `image-diag-2`。

相关文件：

- `src/main/java/com/oasisfeng/nevo/decorators/wechat/ImageNotificationIdentity.java`
- `src/main/java/com/oasisfeng/nevo/xposed/MainHook.java`

### 2.6 测试

- `ImageEventIndexTest`：路径质量合并、`msgSvrId` 保留和身份合并。
- `ImagePreviewPolicyTest`：候选比较、等待策略、普通大图完成后的立即发布、像素预算和采样。
- `ImageNotificationIdentityTest`：通知令牌、事件、消息前缀和多图歧义。
- `WeChatImageDownloaderTest`：基础行选择、原图行禁止、talker 不符和完成条件。
- 当前 Debug 单测共 47 项、9 个测试套件，失败和错误均为 0。

## 3. 图片通知完整加载流程

### 3.1 模块启动

1. LSPosed 将 `MainHook` 注入微信主进程。
2. `WeChatDecorator.Local.onCreate()` 读取图片预览总开关和普通大图开关。
3. 图片预览关闭时直接跳过所有图片 hook。
4. 图片预览开启时安装 `WeChatImageEvents`。
5. 普通大图开关也开启时安装 `WeChatImageDownloader`。

真机启动标记：

```text
NX_IMAGE stage=process_init revision=image-events-9
NX_IMAGE stage=events_ready profile=8.0.72/3085 revision=image-events-9 scans=0 base=g.d
NX_IMAGE stage=large_ready profile=8.0.72/3085 revision=image-large-1 base_row_only=true
```

### 3.2 接收图片并记录事件

1. 微信图片管线经过 `b80.m` 的初始化、远端数据或本地文件回调。
2. `WeChatImageEvents` 在回调后读取 `key_msg_info`，确认类型是图片且不是自己发送。
3. 从 `v65.z.d(...)` 读取最多 4 个图片路径：
   - `key_write_hd_thumb_path`
   - `key_hd_thumb_path`
   - `key_write_thumb_path`
   - `key_thumb_path`
4. 高清字段标记为 `QUALITY_HD`，普通字段标记为 `QUALITY_THUMBNAIL`。
5. 同时通过 `I0()` 和消息父类字段记录 `msgSvrId`。
6. `ImageEventIndex` 使用 `talker + messageId` 作为事件 key，合并同一消息的重复事件。

### 3.3 通知请求与事件关联

1. 微信发出图片通知，`WeChatDecorator` 识别文本以 `[图片]` 结尾。
2. `ImagePreviewLoader.request()` 创建请求 token，并写入通知 extras。
3. 请求进入单线程 `NX-image-preview` worker，初始状态允许等待 talker 异步解析。
4. worker 根据 talker、通知时间和观察时间从 `ImageEventIndex` 选择唯一图片事件。
5. 同一会话若同时匹配多个图片事件，则安全放弃，不猜测具体消息。

### 3.4 本地候选扫描

1. 对事件中的路径逐个调用微信 VFS resolver。
2. 去重解析后的路径，检查文件存在、可读、大小和修改时间。
3. 使用 `BitmapFactory.inJustDecodeBounds` 读取尺寸和 MIME，不立即加载全图。
4. WXGF 无法被 Android 识别时，读取文件并调用微信 JNI 转换成普通图片字节。
5. 按目标覆盖、来源质量和实际像素数选择最佳候选。
6. 候选未达到目标或仍非 HD 时，在 5 秒窗口内按 100ms 到 500ms 的节奏重试。
7. 当前最佳候选满足要求，或大图请求已完成且已有 HD 候选时，进入解码。

### 3.5 普通大图获取

只有同时满足以下条件才会主动请求：

- 总图片预览开关开启。
- 普通大图开关开启。
- 事件带有有效 `msgSvrId`。
- 当前本地候选为空，或未达到目标显示要求。

后续步骤：

1. 按 `msgSvrId` 查询 `ImgInfo2` 基础行；首次没有命中时，每 200ms 在 5 秒窗口内重查。
2. 基础行已完整且有可读路径时，直接加入候选扫描，不发网络请求。
3. 基础行不完整时，使用基础行 `id` 和 talker 构造 `MsgIdTalker`。
4. 调用微信普通大图下载方法，按返回码 `>= 0` 判断提交成功。
5. `WeChatImageDownloader` 独立 worker 每 100ms 查询同一基础行。
6. 只有 `id` 相同、`totalLen > 0`、`offset >= totalLen` 且至少一个路径非空时才算完成。
7. 下载完成回调重新调度图片解析 worker，扫描新文件。
8. 若 5 秒内没有达到完成条件，继续使用当前最佳本地候选。

### 3.6 解码与发布

1. 图片解析 worker 计算目标尺寸和最大安全 `inSampleSize`。
2. 普通格式直接 `decodeFile`；WXGF 使用 JNI 返回的普通字节解码。
3. 解码前后再次检查文件长度和修改时间，避免发布写入中的文件。
4. 位图按目标尺寸做最终等比缩放，不主动放大。
5. `publishing` 阻止同一请求重复解码和重复发布。
6. 主线程重新查找活动通知，校验 token 和 pending 请求实例。
7. 使用 `Notification.Builder.recoverBuilder()` 保留原始 action 和 intent，替换成 `BigPictureStyle`。
8. 调用 `NotificationManager.notify()` 更新通知，并缓存位图 30 秒。
9. 后续文字消息通过 `keepPreview()` 重新挂载图片；通知 token 不连续时拒绝重挂。
10. 请求完成、超时、取消或发布失败时移除 pending，并取消对应大图观察任务。

## 4. 性能分析

### 4.1 线程模型

- `NX-image-preview`：单线程，负责路径解析、候选扫描、解码调度和重试。
- `NX-image-download`：两个线程，负责大图提交、数据库轮询和 WXGF 解码。
- 通知发布、重新挂载和位图缓存清理在主线程执行。
- 请求上限为 8，图片 worker 队列阈值为 16，大图任务上限为 4。
- 单线程 worker 避免多个高分辨率图片同时解码，限制内存峰值，但也意味着多个会话同时收图时会串行处理。

### 4.2 数据库与轮询

- `ImgInfo2` 查询带参数并使用固定列，正常情况下成本较低。
- `queryBaseRow()` 在 `databaseLock` 内执行，多个大图任务不会同时访问数据库。
- 每个任务每 100ms 查询一次，最长 5 秒，单任务最多约 50 次查询。
- 4 个任务并发时，最坏情况接近每秒 40 次数据库查询，存在 WAL、锁竞争或耗电风险。
- 这是当前最值得真实 profiling 的模块内热点；如果批量收图耗时升高，可先评估将轮询退避从固定 100ms 改为 100/200/500ms。

### 4.3 文件与图片解码

- 模块不扫描账户目录，每个请求只处理事件关联的最多 4 个路径。
- 每次重试都会重新做 VFS resolve、文件 stat 和 `inJustDecodeBounds`。
- 单个请求在 5 秒内可能对同一组文件重复读取几十次图片头信息，但不会重复解码完整位图。
- WXGF 路径需要同时持有压缩源字节和解码字节，单次额外内存上限约 2 × 20 MiB。
- 单张位图上限约 4 MiB；发布缓存最多 4 张，理论位图缓存上限约 16 MiB。
- 如果未来出现低端设备或超大 WXGF，JNI 解码前的字节数组分配是首要内存风险。

### 4.4 事件 hook

- 图片管线 hook 是 after hook，会增加微信原线程少量执行时间。
- 每个图片事件最多进行 4 次路径反射读取、若干消息 accessor 调用和一次小索引写入。
- `f9.I0()` 的 hook 负责捕获 `msgSvrId`，但该方法可能被高频调用；每次调用会先检查返回值，之后对图片消息调用 type、sender、talker 和 id accessor。
- 当前没有证据显示 hook 是主要瓶颈，但如果聊天列表滚动或批量同步时出现卡顿，应优先 profiling `I0` hook 的调用次数。
- Debug 配置下每个路径字段都会输出日志，路径探针还会每 2 秒运行 120 秒；这些诊断在 Release 中不会执行。

### 4.5 主线程和通知构建

- 最终 `recoverBuilder()`、`BigPictureStyle` 构建和 `NotificationManager.notify()` 在主线程执行。
- 发布前会遍历活动通知以校验 token，活动通知很多时存在额外成本。
- 当前每张图只发布一次，且成功路径大约在 500ms 内完成，常规场景不应形成主线程持续负载。
- 如果后续出现通知很多时的卡顿，可优先检查 `getActiveNotifications()`、通知重建和连续多图发布频率。

### 4.6 正确性优先于极限性能的设计

- 同一会话同时匹配多个图片事件时直接失败关闭，避免串图，但会降低连续多图命中率。
- `msgSvrId`、通知 token、talker、事件 key 和消息前缀共同校验，带来少量 map 和字符串比较成本。
- 图片完成后的立即发布避免空等 5 秒，是本次修复的重要性能改进。
- `publishing` 防止重复解码，减少一次不必要的约 568 KiB 位图分配和一次被丢弃的发布。

## 5. 真机验证记录

设备：`1c5c1144`，PJZ110，微信 8.0.72 / 3085。

最新源码完整 lint 结果：

- `lintDebug` 成功，`0 errors / 60 warnings`。
- 新增图片链路文件没有任何 lint 项。
- 60 条均为仓库既有警告，集中在 SDK 版本死代码、未使用资源、布局可访问性、依赖更新和旧静态字段检查。
- 详细分类和报告位置见下一节的 lint 记录。

已安装并完成端到端验证的 APK：

- SHA-256：`8B5E7B9FC28D5A65F6DAF49D0DE6B91877ED70FFEFCDF8ACD5D6F6192F221196`
- 该构建包含立即发布和重复解码修复。
- 之后只做了纯策略提取和测试补充；最新本地 APK SHA-256 为 `5A5B45F7B4A36CAE66803FB6F57ED6884A6B16F5B2B8849F412FA7F4D4EF3741`，尚未重装。

关键日志：

```text
01:54:38.390 large_lookup request=11610862700875 row=true originalRowFollowed=false
01:54:38.392 large_submitted request=11610862700875 return=0
01:54:38.644 large_ready elapsedMs=253
01:54:38.746 candidate quality=2 exists=true bytes=22828 source=277x513 mime=image/jpeg
01:54:38.748 decoded sourceSize=277x513 sample=1 output=277x513 bytes=568404 elapsedMs=460
01:54:38.755 published style=BigPicture actions=1 picture=277x513
```

本次日志只出现一次 `decoded` 和一次 `published`，证明重复解码和空等 5 秒问题已修复。

本地截图：

- `build/nx-notification.png`

截图中普通大图已经清晰显示，主要标题和说明文字可辨认。

## 6. 待完成事项

1. 对最新源码运行完整 `lintDebug`，记录最终错误和警告数量。
2. 安装最新源码构建的 APK，重新校验设备 APK SHA-256 和启动标记。
3. 验证普通大图开关关闭时没有主动下载。
4. 验证文字消息、图片后紧跟文字、连续多图、两个会话同时收图和通知清除。
5. 验证断网、慢网、下载超时、基础行延迟落库和进程退出。
6. 验证 WXGF/HEVC 图片路径和 WXGF 解码失败回退。
7. 检查原图行在测试前后没有被模块读取或下载。
8. 最终确认 APK v2 签名、版本号、文件大小和 SHA-256。

lint 最终状态：

- 最新源码完整 lint 已通过，结果为 0 错误、60 条警告。
- HTML、文本和 XML 报告位于 `build/reports/lint-results-debug.*`。
- 当前没有 error；warning 清理由后续独立重构处理，避免混入本次图片链路修复。

## 7. lint 报告分类

### 7.1 ObsoleteSdkInt，18 条

项目 `minSdk` 为 26，因此部分 `SDK_INT >= O`、`SDK_INT >= N` 或 `SDK_INT < 26` 判断永远不会走低版本分支。主要位于：

- `MessagingBuilder.java`
- `WeChatDecorator.java`
- `VoiceCall.java`
- `MainPreference.java`

这类警告属于死代码清理，不是当前运行错误。新增图片链路没有引入此类判断。

### 7.2 UnusedResources，17 条

包括 2 个颜色资源和 15 个字符串资源，主要来自早期 decorator 功能或已废弃文案。新增的普通大图开关字符串不在未使用列表中。

清理前需要确认资源没有被动态名称查找、其他模块或外部入口引用。

### 7.3 ContentDescription，14 条

集中在媒体通知自定义布局中的 `ImageView`。这些图片多为装饰或通知内容本身，需要逐个决定是添加有意义的描述，还是明确标记为不可访问，不能机械地全部加空字符串。

### 7.4 UnusedAttribute，4 条

`outlineAmbientShadowColor` 只支持 API 28 及以上，而项目最低支持 API 26。可以移入 `values-v28`/布局限定目录，或在确认视觉要求后移除。

### 7.5 GradleDependency，3 条

`androidx.core`、`androidx.annotation` 和 `androidx.appcompat` 有更新版本。升级可能引入 API 和行为变化，不应作为图片预览修复的附带改动。

### 7.6 StaticFieldLeak，2 条

分别指向 `NevoDecoratorService` 的静态 application/package context 和 `SystemUIDecorator` 的静态 service 引用。Application context 通常是有意长期持有，但 service 引用需要结合生命周期确认；不能仅根据 lint 文本直接删除。

### 7.7 其他，2 条

- `OldTargetApi`：`targetSdk` 为 34，会触发最新版本兼容性提示。提高目标版本会影响运行时行为，应单独评估。
- `SdCardPath`：`MessagingBuilder.java:800` 在无法获得数据目录时回退到硬编码 `/data/data/com.tencent.mm`。该路径主要用于读取头像，可考虑失败关闭，但与本轮图片通知链路无关。
