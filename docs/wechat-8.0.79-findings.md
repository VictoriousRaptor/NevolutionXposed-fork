# 微信 8.0.79 / 3182 适配记录

更新日期：2026-10-16。模块版本：3.2.1 / code 14。

## 范围与证据

本次为手机实际安装的 `com.tencent.mm` / `8.0.79` / `3182` 增加独立画像。
通知回复、图片事件、普通大图下载、应用内回复和撤回均要求版本名与版本码同时匹配，
随后分别校验完整运行时描述符；不推断其他同名构建可用。
旧版画像、功能开关、消息身份、回复来源排除和保守回退保持原有行为。

设备为 PJZ110 / Android 16 / API 36，系统 `PJZ110_16.0.10.501(CN01)`。
APK 由 ADB 从当前已安装微信拉取，安装来源字段为 `com.apkmirror.helper.prod`；
minSdk 24 / targetSdk 35。base 含 18 个 DEX，5 个 split 未发现 DEX。

| APK | SHA-256 |
| --- | --- |
| base.apk | `4382254fbb256f37b7b430e6f3d984c512c603e46d39f9c323cd8a6fa75d8364` |
| split-1.apk（arm64_v8a） | `0c931a367bd979d96e338829da0c02b34156ff2ab453f57abffcbcf633cfcd54` |
| split-2.apk（xxxhdpi） | `b11ce8e2669318eec93da4698a514967963e2ffd3aafed79a22c213bca7fe245` |
| split-3.apk（zh） | `60616b3802443051979eeeefeddbd9650aac4c75e760d1a6bad2c12595938897` |
| split-4.apk（delivery） | `9ec87b8f91ffd1f877d3e95e2e05b87b31794b836250a74814cab6db30923bb7` |
| split-5.apk（delivery.arm64_v8a） | `8f427125ce458297682a4975b14063fdd63eaefca7dcdd274697b1b03d9a9ea8` |

只读采集根目录为 `.debug-artifacts/device/20261016-005759/`；回复探针源码位于
`probe/sources/sources/`。本次定向反编译使用 JADX `--rename-flags none --no-res`，
源码位于 `.debug-artifacts/wechat-8.0.79/mapping-sources/sources/`；其余依赖源码位于
`.debug-artifacts/wechat-8.0.79/sources/sources/`。这些产物均仅保存在本地忽略目录。
下文使用原始类/字段/方法名，不采用 JADX 的桥接合并别名。

## 通知回复

| 依赖 | 完整运行时描述符 |
| --- | --- |
| 接收器 | `com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver extends android.content.BroadcastReceiver` |
| 入口 | `onReceive(android.content.Context, android.content.Intent): void` |
| RemoteInput 辅助方法 | `z2.s1.b(android.content.Intent): android.os.Bundle`，static |
| config gate | `lt1.a.c(): boolean`，static |
| car mode gate | `lt1.a.d(): boolean`，static |
| Android Auto gate | `lt1.a.b(): boolean`，static |

证据：`classes9.dex` 的 `MMAutoMessageReplyReceiver.java` 与 `lt1/a.java`。
接收器先检查 `key_username`，再读取 `key_voice_reply_text`，依次检查 c/d/b；
`MM_AUTO_REPLY_MESSAGE` / `MM_AUTO_HEARD_MESSAGE` 两个广播 action 未变。
画像标签为 `wechat-8.0.79/3182`。名称为 8.0.79 或版本码为 3182、但组合不精确时，
在旧版名称或版本码任选规则之前退回 legacy；legacy 仍须通过既有签名校验。
原生 PendingIntent 优先，合成回复仍须有已知会话 key 和有效画像，不根据正文猜目标。

## 图片事件与普通大图

| 依赖 | 8.0.79 / 3182 描述符 |
| --- | --- |
| state | `gh5.z.f(String): Object`，实例方法，可返回 null |
| simple message | `lr3.f extends lr3.g`，继承 `d: int` 和 `getString(int): String` / `getLong(int): long` / `getInteger(int): int` |
| message | `com.tencent.mm.storage.e9`，继承 `hn.c8.field_msgSvrId: long` |
| talker / sender / server ID | `N0(): String` / `C0(): int` / `I0(): long` |
| 共用 accessor | `getMsgId(): long`、`getCreateTime(): long`、`getType(): int` |
| 初始 AFTER hook | `sb0.l.l(gh5.z): jh5.b` |
| remote AFTER hook | `sb0.l.handleDataFromRemote(gh5.z, h61.e): jh5.b` |
| local AFTER hook | `sb0.l.handleDataFromFile(gh5.z, gb0.d): jh5.b` |
| 路径 AFTER hook | `v61.l0.I3(storage.e9, String, boolean): String` |
| VFS | `com.tencent.mm.vfs.z6.i(String, boolean): String`，static |
| core getter | `pq0.i1.x(): pq0.a0`，static |
| database field | `pq0.a0.f: lh5.k0`，按唯一字段类型解析 |
| SQL query | `lh5.k0.f(String, String[], int): android.database.Cursor` |
| service manager | `kj5.n0.c(Class): kj5.m`，static |
| service / implementation | `eb0.y` / `db0.e implements eb0.y` |
| download getter | `db0.e.Bj(): eb0.x` |
| download implementation | `v61.j implements eb0.x` |
| download method | `v61.j.b(long, com.tencent.mm.plugin.msg.MsgIdTalker, int, Object, int, eb0.w, int, boolean): int` |
| callback | `eb0.w`，interface |
| pair constructor | `com.tencent.mm.plugin.msg.MsgIdTalker(long, String)` |
| decoder | `com.tencent.mm.plugin.gif.MMWXGFJNI.wxam2PicBuf(byte[], int, int): byte[]`，static |

证据定位：`sb0/l.java`、`sb0/k.java`、`v61/m0.java`、`v61/l0.java`、`hn/c8.java`
位于 classes13；`gh5/z.java`、`pq0/i1.java`、`pq0/a0.java`、`lh5/k0.java`、`kj5/n0.java`
位于 classes11；`lr3/f.java` 位于 classes7、父类 `lr3/g.java` 位于 classes3；
`db0/e.java` 位于 classes12；`v61/j.java` 位于 classes4。
`v61/m0.java:317` 起写入四个 thumb 路径键和 `key_msg_info`；`sb0/k.java:111` 起读取下载缩略图路径。
`v61/l0.java:604` 的 I3 调用 R3 返回路径。

状态读取不再是旧版的 `d(String)`。新画像显式记录 `stateGet=f`，旧三组图片画像记录 `stateGet=d`；
生产 hook 与已安装描述符检查共用该字段，均精确校验 `(String): Object`。
simplemsginfo 偏移仍为 `d+0 localId`、`+1 svrId`、`+2 createTime`、`+3 talker`、`+4 type`、`+9 isSender`。
仅接收方向 0、类型 3 进入事件索引。保持四个路径键、质量排序、有界索引和会话归属过滤。

`v61/l0.java:95` 的 ImgInfo2 schema 仍包含当前固定 SQL 使用的
`id/msgSvrId/msgTalker/bigImgPath/hevcPath/midImgPath/offset/totalLen/reserved1`。
下载接口参数形状不变，仍选择普通图、只查基础行，不沿 reserved1 额外查询原图；
任务上限、大小限制、等待、取消和缩略图回退保持现有实现。
图片事件与下载服务分别校验，预览总开关关闭时不安装；普通大图请求还要求大图开关开启。
未知版本或签名不符时对应组件关闭，不扩大匹配范围。

## 应用内回复与撤回

| 依赖 | 8.0.79 / 3182 描述符 |
| --- | --- |
| 发送状态 AFTER hook | `storage.e9.r1(int): void`，本类 override |
| sender / status / talker | `C0(): int` / `M0(): int` / `N0(): String` |
| excluded | `a3(): boolean`、`M2(): boolean`、`F2(): boolean` |
| recall payload | `com.tencent.mm.autogen.events.RevokeMsgEvent.g: en.js` |
| recalled message | `en.js.c: com.tencent.mm.storage.e9` |
| recall talker / server ID | `N0(): String` / `I0(): long` |
| event BEFORE hook | `com.tencent.mm.sdk.event.IEvent.e(): boolean`，RevokeMsgEvent 直接继承该类 |
| notification BEFORE hook | `com.tencent.mm.booter.notification.NotificationItem.a(Context): void` |
| notification fields | `f: android.app.Notification` / `h: String` / `i: long` |

`storage/e9.java:955` 起先 `super.r1(i)`，再检查本人方向、排除条件和状态；
状态 2 发布 `SendMsgSuccessEvent`，状态 5 进入失败分支。只在参数及更新后状态均为 2、
身份/时间有效时记录轮次边界，沿用通知栏回复来源排除，不读取正文。

`RevokeMsgEvent.java` / `en/js.java` 位于 classes4，`NotificationItem.java` / `notification/x.java` /
`notification/m0.java` 位于 classes9。`notification/x.java:97` 读取 `g.c`，
`:533` 将消息 `I0()` 交给 `m0.a(...)`；`m0.java:543` / `:963` 将该值写入 NotificationItem.i。
`hn/c8.java:271` 确认 I0 返回 `field_msgSvrId`，与 N0 会话 accessor 配合进行精确关联。
撤回仍只监听 IEvent.e，同步入口之外的异步发布不推断已覆盖。
没有精确 ID 时保留未知消息，不按文本、时间或相邻位置猜撤回对象。
这两条链路不依赖图片开关，分别按完整版本与签名启用。

作用进程仍为微信主进程，回调时机与现有实现一致。
本次未增加 Android/SystemUI hook；既有 Markdown 恢复和通知移除 hook 保持原版本与签名约束。

## 构建依赖与验证边界

直接依赖升级到当前官方稳定版：Core `1.12.0 → 1.19.1`、Annotation `1.7.1 → 1.11.0`、
AppCompat `1.6.1 → 1.8.0`。依据为 [Core 发布说明](https://developer.android.com/jetpack/androidx/releases/core)、
[Annotation 发布说明](https://developer.android.com/jetpack/androidx/releases/annotation)、
[AppCompat 发布说明](https://developer.android.com/jetpack/androidx/releases/appcompat) 及官方 Maven 元数据。
libxposed API/service `102.0.0`、Palette `1.0.0`、Preference `1.2.1`、JUnit `4.13.2` 保持既有稳定版本。
AGP 9.1.1 / Gradle 9.3.1、compileSdk 37、minSdk 26 / targetSdk 34 保持不变。

最终执行 `-PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintRelease`：
138 项主机单元测试全部通过；Release lint 0 errors / 34 warnings；Debug、仪器包和 Release 均构建通过。
依赖首次联网解析后，最终构建离线执行。Kotlin daemon 首轮出现本机临时备份文件异常后使用 fallback 完成；
最终使用进程内编译并关闭增量编译完成，无需修改项目构建配置。

| 验证层级 | 本轮实际结果 |
| --- | --- |
| 设备描述符 | 8.0.79/3182 上 40/40 项完整接口/字段/继承检查通过，`INSTRUMENTATION_CODE: -1` |
| framework 用例 | 46/46 通过，0 失败状态，`INSTRUMENTATION_CODE: -1`，含媒体回复去重及 Markdown 清理/Parcel 回归 |
| 签名 | 三包均与现有 release 密钥和手机原 3.1.4 证书一致；SHA-256 `7a707a90a36c94d231678813514d2ce815ad81fbf79558edc67eab4f288c4df9` |
| Release APK | 3.2.1 / code 14，非 debuggable，3,507,802 bytes，APK v2 签名校验及 16 KiB 对齐检查通过 |
| APK SHA-256 | `4dd5bae5699bac25fcfa2db07578ba34ba80ce6c0982ec392c11932a5318f4d7`；原地安装后回拉哈希一致 |
| 微信运行时 hook | 同证书 Debug 检查确认 `profile_ready ... lt1.a[c,d,b] usable=true`、`app_reply_ready`、`recall_ready`、`events_ready`、`large_ready`；微信 `code_styles_hook_ready` 也通过 |

运行时最初没有模块加载日志。解锁手机后打开 LSPosed 2.2.1 (7912) 实际检查，发现模块已启用，
SystemUI 作用域已选，但微信未选。仅补选微信作用域后重启微信，五条链路均在主进程出现 ready；
没有修改其他模块或微信功能开关。最终换回同一已校验 Release，并重启微信重新加载。
Release 现场再次确认 `profile_ready ... lt1.a[c,d,b] usable=true` 和微信的 `code_styles_hook_ready`；
其他四项详细 ready 日志受既有 Debug 日志开关控制，以前述同源码 Debug 检查为依据。
临时仪器测试包已卸载，主模块保留；本轮未重启 SystemUI 或整机。

本地证据为 `.debug-artifacts/wechat-8.0.79/` 的 `build.log`、`verification.json`、
`descriptors.txt`、`instrumentation.txt`、`lsposed-nevo-scope-ui.xml`、`lsposed-nevo-scope-enabled.xml`、
`live-debug-scoped.txt`、`live-release-scoped.txt` 和 `installed-release.apk`。
截图树、完整日志、签名材料与所有 APK 都不纳入公开提交。

构建、描述符或 ready 均不代表微信真实通知回复送达、收图/下载、应用内轮次推进或撤回裁剪通过。
本轮没有自动向真实会话发送消息或执行撤回；单聊/群聊回复、媒体更新后回复、图片实际下载/取消/超时、
应用内发送后的新轮次、真实撤回精确裁剪与通知不复活、SystemUI 实际展示仍需用户消息验收。

回滚包为 `.debug-artifacts/wechat-8.0.79/previous-3.1.4.apk`（ADB 从手机保存），
SHA-256 `52aee3a34e62da71936a197bf132dd71966878c51f989493c59fbefd24ce8f32`。
如需回滚，先核对同证书和版本降级安装限制；不自动卸载、清数据或重启整机。
