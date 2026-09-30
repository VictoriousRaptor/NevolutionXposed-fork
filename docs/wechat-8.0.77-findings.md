# 微信 8.0.77 适配记录

更新日期：2026-09-30。分支 `codex/wechat-8.0.77`，基线 `f132a2f1889ea3f4abdbfa78159a411e8b410361`。

## 当前完成状态

通知回复、普通图片事件/缩略图预览、大图下载、应用内回复轮次和撤回精确关联的代码映射已完成。
覆盖 `8.0.77/3141` 与 `8.0.77/3160` 两种布局，分别核对完整 DEX 签名。
3141 真机已安装同 release 签名的 Debug APK；40 项已安装微信描述符检查、33 项模块框架测试通过，
五条链路在微信主进程均出现 ready 日志。3160 仅有 APK、源码和 DEX 核对证据，未在 3160 真机运行。

自动检查阶段按用户要求只完成安装和不发送消息的检查。2026-09-30 用户随后反馈“测试基本通过”，
并授权合并到 `master`、推送到自有仓库；本轮整体验收记为基本通过（用户实机反馈）。
用户未逐项列出具体通过场景，实际送达、收图/下载、应用内发送时序和撤回视觉效果的完整覆盖仍待记录；
ready 或模块框架测试不替代真实行为证据。

## APK 与设备事实

| 项目 | 3141（当前真机） | 3160（用户提供文件） |
| --- | --- | --- |
| 包名 / 版本 | `com.tencent.mm` / `8.0.77` / `3141` | `com.tencent.mm` / `8.0.77` / `3160` |
| 来源 | ADB 从已安装微信拉取 base 与 5 个 split APK | `com.tencent.mm_8.0.77-3160_minAPI24(arm64-v8a)(nodpi)_apkmirror.com.apk` |
| base APK SHA-256 | `464a3a73ddf30f2541285af33483676df616d051b52bbe2ea1e03a4cf2eec236` | `18714afe662d2ca58e8e9389ad727c9ff562ed8f3711a93c904819bb753215d2` |
| minSdk / targetSdk | `24` / `35` | `24` / `34` |
| 证据根目录 | `.debug-artifacts/wechat-8.0.77/device-3141/` | `.debug-artifacts/wechat-8.0.77/` |
| 主 DEX | base 中 17 个 DEX；5 个 split 不含 DEX | 17 个 DEX |

真机：PJZ110 / Android 16 / SDK 36，LSPosed 2.2.0 API 102。诊断原件和反编译源码均仅保存于忽略目录。
JADX 使用 `--rename-flags none`，运行时描述符另通过 class definitions、method prototypes 和 flags 核验。
JADX 在部分桥接方法仍会展示合并别名，hook 只使用已核对的 DEX 原始方法名。

## 本次修改内容

| 文件 | 实际改动 |
| --- | --- |
| `WeChatReplyProfile.java` | 为 3141 新增 `bs1.a[e,g,c]` 画像，保留 3160 的 `lq1.a[e,h,c]`；8.0.77 改为名称与版本码同时匹配，未知构建不复用另一个 8.0.77 布局 |
| `WeChatImageProfile.java` | 新增精确版本画像，包含缩略图、消息身份、VFS、数据库和下载服务描述符；保留原 8.0.72/3085 |
| `WeChatImageEvents.java` | 从画像解析类、方法及消息 accessor；保留全部签名/字段校验、事件索引、收发过滤和预览路径质量规则 |
| `WeChatImageDownloader.java` | 从画像解析内核、数据库、服务、下载及回调；缓存已选图片服务 Class，移除 `downloadService()` 内另一个固定版本类名；保持普通大图、base-row、任务上限和失败回退规则 |
| `WeChatAppReplyEvents.java` | 为两种 8.0.77 构建增加精确发送状态画像；保留 AFTER hook、发送成功过滤与通知回复来源排除 |
| `WeChatRecallEvents.java` | 将两版条件选择提取为完整画像，新增两种 8.0.77 payload/服务端 ID/会话映射；保留通知与事件 BEFORE hook、精确裁剪和不复活规则 |
| `src/main/AndroidManifest.xml` | 仅声明 `com.tencent.mm` 的 package query，供模块进程查询已安装微信及执行只读描述符检查；未增加全包查询权限 |
| `WeChatReplyProfileTest.java`、`RecallIndexTest.java`、`WeChat8077AdaptersTest.java` | 测试构建隔离、混合版本拒绝、各组件身份映射一致性及旧版本支持边界 |
| `MessageIdentityInstrumentation.java`、`WeChatDescriptorChecks.java` | 新增 opt-in 的 `verify_wechat_profiles=true` 检查；加载已安装微信类但不初始化业务、不安装 hook、不访问数据库、不发送消息，校验完整签名与字段类型 |
| `README.md`、适配手册及相关专题文档 | 同步支持范围、精确门禁、验证层级和待验收项 |

hook 作用进程与回调时机沿用基线。图片两条链路由预览开关控制，大图请求仍由大图开关额外控制；
应用内回复和撤回安装不依赖图片开关。签名不符或未知组合时各组件分别关闭。
模块版本号仍为 3.1.3/code 11。

## 完整版本映射

### 通知回复

接收器两版均为 `com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver extends BroadcastReceiver`，
`onReceive(Context, Intent): void`；辅助方法两版均为 `z2.s1.b(Intent): Bundle`，static。
`key_username`、`key_voice_reply_text` 以及 `MM_AUTO_REPLY_MESSAGE` / `MM_AUTO_HEARD_MESSAGE` 保持不变。

| 描述符 | 3141 | 3160 |
| --- | --- | --- |
| config gate | `bs1.a.e(): boolean` static | `lq1.a.e(): boolean` static |
| car mode gate | `bs1.a.g(): boolean` static | `lq1.a.h(): boolean` static |
| Android Auto gate | `bs1.a.c(): boolean` static | `lq1.a.c(): boolean` static |

名称 `8.0.77` 与版本码同时匹配；8.0.72/8.0.78 仍保留原名称或版本码选候选、再运行时签名校验的规则。
原生 PendingIntent 优先；基线合成回退仍只使用已知非空会话 key，不猜用户名。

### 普通图片事件与消息身份

| 描述符 | 3141 | 3160 |
| --- | --- | --- |
| state / `d(String): Object` | `ld5.z` | `pb5.z` |
| simple message / inherited `d:int` | `gp3.f extends gp3.g` | `mn3.f extends mn3.g` |
| inherited accessors | `getString(int): String`、`getLong(int): long`、`getInteger(int): int` | 同左 |
| message class | `com.tencent.mm.storage.e9` | 同左 |
| talker / sender / server ID | `N0(): String` / `C0(): int` / `J0(): long` | `Q0(): String` / `A0(): int` / `K0(): long` |
| shared message accessors | `getMsgId(): long`、`getCreateTime(): long`、`getType(): int`、继承字段 `field_msgSvrId: long` | 同左 |
| initial AFTER hook | `db0.m.l(ld5.z): od5.b` | `n90.m.l(pb5.z): sb5.b` |
| remote AFTER hook | `db0.m.handleDataFromRemote(ld5.z, j51.e): od5.b` | `n90.m.handleDataFromRemote(pb5.z, q31.e): sb5.b` |
| local AFTER hook | `db0.m.handleDataFromFile(ld5.z, ra0.d): od5.b` | `n90.m.handleDataFromFile(pb5.z, b90.d): sb5.b` |
| message path AFTER hook | `x51.l0.d3(e9, String, boolean): String` | `e41.l0.N2(e9, String, boolean): String` |
| VFS | `com.tencent.mm.vfs.a7.i(String, boolean): String` static | 同左 |

simplemsginfo 基址 `d` 的布局两版一致：`+0 localId`、`+1 svrId`、`+2 createTime`、`+3 talker`、
`+4 type`、`+9 isSender`。仅类型 3 且接收方向 0 入预览索引；保持已有字段偏移与消息归属限制。
读取 `key_msg_info` 和四个 thumb 路径键，高清缩略图与普通缩略图分级；事件采集自身不提交下载。

### 普通大图下载

| 描述符 | 3141 | 3160 |
| --- | --- | --- |
| core getter | `yp0.k1.v(): yp0.c0` static | `ho0.j1.v(): ho0.b0` static |
| database field | `yp0.c0.f: qd5.k0`（按唯一类型校验并解析） | `ho0.b0.f: ub5.k0`（同策略） |
| query | `qd5.k0.f(String, String[], int): Cursor` | `ub5.k0.f(String, String[], int): Cursor` |
| service manager | `of5.n0.c(Class): of5.m` static | `sd5.n0.c(Class): sd5.m` static |
| service / implementation | `pa0.y` / `oa0.e` | `z80.y` / `y80.e` |
| download getter | `oa0.e.ej(): pa0.x` | `y80.e.Wi(): z80.x` |
| download method | `x51.j.b(long, MsgIdTalker, int, Object, int, pa0.w, int, boolean): int` | `e41.j.b(long, MsgIdTalker, int, Object, int, z80.w, int, boolean): int` |
| pair constructor | `com.tencent.mm.plugin.msg.MsgIdTalker(long, String)` | 同左 |
| decoder | `MMWXGFJNI.wxam2PicBuf(byte[], int, int): byte[]` static | 同左 |

两版 `ImgInfo2` schema 均包含当前 SQL 所需列：`id/msgSvrId/msgTalker/bigImgPath/hevcPath/midImgPath/offset/totalLen/reserved1`。
继续只查服务端 ID 选中的基础行并校验会话，不跟随 reserved1 另查原图行；提交参数沿用同语义的普通图片调用，
保留最多 4 个任务、文件大小限制、有界等待、取消及缩略图回退。

### 应用内回复与撤回

| 描述符 | 3141 | 3160 |
| --- | --- | --- |
| status AFTER hook | `storage.e9.r1(int): void`，本类 override | `storage.e9.u1(int): void`，本类 override |
| sender / status / talker | `C0(): int` / `M0(): int` / `N0(): String` | `A0(): int` / `P0(): int` / `Q0(): String` |
| excluded boolean methods | `a3()`、`M2()`、`F2()` | `Z2()`、`L2()`、`E2()` |
| recall payload | `RevokeMsgEvent.g: en.gs`、`en.gs.c: storage.e9` | `RevokeMsgEvent.g: fm.fs`、`fm.fs.c: storage.e9` |
| recall talker / server ID | `N0(): String` / `J0(): long` | `Q0(): String` / `K0(): long` |

发送事件仍要求状态参数及更新后状态均为 2、本人发送方向为 1、未命中排除条件、身份/时间有效。
沿用 `NotificationReplyOrigins` 和现有轮次推进，不读取发送正文。

撤回两版共用 `NotificationItem.a(Context): void` BEFORE、字段 `f: Notification/h: String/i: long`，
以及 `IEvent.e(): boolean` BEFORE（只处理直接继承 IEvent 的 RevokeMsgEvent）。
`notification.x` → `notification.m0.a(...)` → `NotificationItem.i` 证明来源为上述服务端 ID，不能换成本地 ID。
保留缺少精确身份时不删、当前通知 token 匹配才重建、裁剪为空时 `StopPost`、撤回时取消旧预览等基线行为。

## 离线证据位置

两种证据根目录下均有 `classes.json`（DEX 定义索引）及 `probe/sources/`（按类反编译）。
主要语义证据如下，均相对各自根目录：

- `probe/sources/com/tencent/mm/plugin/auto/service/MMAutoMessageReplyReceiver.java` 和 `bs1/a.java` / `lq1/a.java`：门禁调用顺序、结果键和广播创建。
- `probe/sources/gp3/g.java` / `mn3/g.java`：simplemsginfo 字段顺序与基址；`db0/m.java` / `n90/m.java`：图片流程回调；`db0/l.java` / `n90/l.java`：路径键读写。
- `probe/sources/hn/c8.java` / `im/c8.java`：消息字段 accessor；`storage/e9.java`：发送状态 override 与成功事件过滤。
- `probe/sources/x51/l0.java` / `e41/l0.java`：路径解析和 ImgInfo2 schema；`oa0/e.java` / `y80/e.java`：下载服务获取；`x51/j.java` / `e41/j.java`：下载参数、返回值。
- `RevokeMsgEvent.java`、`en/gs.java` / `fm/fs.java`、`notification/x.java`、`notification/m0.java`、`NotificationItem.java`：事件与通知精确 ID 调用链。
- `.debug-artifacts/wechat-8.0.77/all-dex-signatures.txt`：两份 APK 的图片、发送、撤回完整签名核验 PASS。

## 构建、签名、安装与设备检查

构建命令：

```powershell
.\gradlew.bat -PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
```

- 110 项单元测试通过，0 failures/errors；lint 0 errors、34 warnings。
- Debug 与 AndroidTest APK 构建成功，两包证书与原设备模块及现有 release JKS 一致。
- release 证书 SHA-256：`7a707a90a36c94d231678813514d2ce815ad81fbf79558edc67eab4f288c4df9`。
- 当前 Debug APK：`build/outputs/apk/debug/NevolutionXposed-debug.apk`，4,038,198 字节。
- APK SHA-256：`1bab0409e4097076d4ff94eb3b8548cd96a84a9d3a0f385487a62786153a9260`。
- 用户授权后 `adb install -r` 两包成功；模块回拉哈希与本地一致。
- `verify_wechat_profiles=true`：已安装微信代码的 40 项签名、字段及继承检查 PASS。
- 模块框架仪器测试：33/33 PASS。隐藏 API 参数仅用于本次仪器测试进程。
- 重新启动目标微信进程后，PID 19342 的启动日志包含 `profile_ready usable=true`、`app_reply_ready`、
  `recall_ready`、`events_ready profile=8.0.77/3141 revision=image-events-10`、
  `large_ready profile=8.0.77/3141 revision=image-large-2`；保留用户原预览/大图开关（均为开启）。
- 自动检查阶段未发送或撤回测试消息，未重启整机或 SystemUI。

本地日志：`full-adapters-build.log`，设备证据目录中的 `descriptor-instrumentation.txt`、
`framework-instrumentation.txt`、`runtime-hooks.txt`、`module-before-install.apk` 与 `module-after-install.apk`。

## 尚未逐项确认的验收明细

以下为完整回归矩阵中尚未单列结果的项目。用户已反馈本轮基本测试通过，不据此推定所有细项均已通过。

| 场景 | 状态与后续工作 |
| --- | --- |
| 单聊/群聊、中文/英文/emoji 通知回复 | 代码和运行时画像就绪；待实际送达、会话正确、不重复发送验证 |
| 普通图片预览 | hook 安装及签名通过；待实际收图、文件出现时机、高清/普通候选、连续收图及图片后收文字验证 |
| 普通大图下载 | 服务签名和安装通过；待开关控制、实际下载完成/失败/超时/取消以及缩略图回退验证 |
| 应用内回复轮次 | hook 就绪；待发送成功/失败时序、下一条来信推进、与通知回复并发验证 |
| 撤回裁剪 | hook 就绪，框架裁剪测试通过；待真实撤回的目标/提示裁剪及通知移除后不复活验证 |
| 通知正文、媒体入口、通话、头像 | 共享逻辑继承基线；仍待 8.0.77 真实通知样本与视觉验收 |
| 3160 真机运行 | 本轮连接的是 3141；3160 仍待同构建设备安装和真实行为验证 |

目前没有已知“尚未写入代码映射”的上述四项功能；剩余项是实际行为验收，不能用 ready 或测试计数替代。

## 迁移历史与回滚材料

原工作基线为 `87adbc5`；按用户要求迁入 `f132a2f`，创建独立分支 `codex/wechat-8.0.77`。
首轮仅补 3160 通知回复画像，89 项旧基线测试通过；迁移后 108 项测试通过。
本轮新增四项迁移及3141支持，结果以上述 110 项测试和设备检查为准。
旧包和日志分别保存在 `pre-baseline-debug.apk` / `pre-baseline-build.log`；迁移前文档为 `pre-baseline-findings.md`。
设备安装前模块为 `device-3141/module-before-install.apk`，回滚仍需按用户授权原地安装，不能自动卸载或整机重启。
