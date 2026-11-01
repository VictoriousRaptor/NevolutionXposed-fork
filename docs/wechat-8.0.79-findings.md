# 微信 8.0.79 / 3182、3183、3200 适配记录

## 2026-11-02 3183 自动真机验证与大图探测修正

在先 rebase 到本地主检出区 `master` 的 `daefa7b` 后，使用现有 release 证书签名的 Debug 在 Android API 37、微信 8.0.79/3183 上复测。回拉 base SHA-256 为 `90096f785f1227f5456f3d099ea9855822d5601754565de5dc45ab5c1df7e3f0`，与下节 APKMirror 样本相同，来源和离线完整映射继续有效。

普通大图首轮两次 `no_match` 已定位：原始类 `lh5.k0` 是 interface，其 `f(java.lang.String, java.lang.String[], int): android.database.Cursor` 是抽象实例方法；`pq0.a0` 原始字段 `f: lh5.k0` 保存实际接收对象。证据为 `.debug-artifacts/wechat-same-name-profile-probing/apkmirror-8079/3183-sources/sources/lh5/k0.java` 与同目录 `pq0/a0.java`，APK 来源/哈希见下节。生产解析器现只对数据库 query 允许 abstract，继续要求精确返回/参数类型与非 static；服务 getter 和下载 `v61.j.b(long,MsgIdTalker,int,Object,int,eb0.w,int,boolean): int` 仍要求非 abstract 实例方法。3200 的 `og5.k0` 同类契约由主机夹具覆盖，本轮未装 3200 复测。

| 验证项 | 最终结果及边界 |
| --- | --- |
| 主机测试与构建 | 284/284；Debug、androidTest、Release 构建成功，证书与现有 release 相同，zipalign 通过，Release 无测试入口 |
| 同包 framework instrumentation | 84/84、0 skipped、0 failures；已修正 runner 名称少 3 项及待图/完成态测试共用通知实例的问题 |
| 安装包描述符与生产解析器 | 45 项通过，包含各独立生产门禁；只解析，不执行真实会话/数据库/下载行为 |
| Emoji、应用内回复、撤回、图片事件 | 两次新微信主进程均记录各自 `ready match=exact`，证明对应 hook 安装 |
| 合成通知回复 | 两次 `profile_ready match=exact usable=true`，未触发实际发送或验收门禁绕过 |
| 普通大图 | 两次 `large_ready profile=8.0.79/3183 match=exact`，证明解析成功，未实际下载 |
| 清理 | `finally` 恢复测试前模块，回拉哈希相同；测试包与临时 runner 均不存在 |

完整 Debug/Release lint 为 0 errors，分别保留 35/14 warnings。Debug framework 入口显式限定 API 28+，低版本取消并给出原因；描述符模式独立。测试 helper 的 API/隐藏 API 标注已补齐，最终入口复测在 `.debug-artifacts/framework-final-3183/`。

版本名和代码同时匹配、主进程 AFTER 初始化、各功能独立门禁与开关、失败保守回退保持。用户选择先完成自动验证，真实收图、回复、应用内回复、Emoji、撤回和 OEM/SystemUI 外观保留待人工验收；没有重启设备或 SystemUI。原始证据仅保存在 `.debug-artifacts/verified-3183/`，细节见[适配手册](wechat-version-adaptation-playbook.md)本日章节。下方 2026-10-29 的 3200 `no_match` 和 framework 结果仍是当时证据，不能拿本轮 3183 结果替换。

## 2026-10-29 framework 测试入口复测

在 Android API 36 手机使用 Debug 模块 APK 内的同包 instrumentation，避开此前独立 androidTest APK 与模块 APK 跨类加载器的包私有访问问题。结果为 68/69 通过、1 项因当前 framework 缺少 Markdown 清理签名而明确跳过、0 失败；已安装微信 8.0.79/3200 的描述符检查 40 项通过。Debug 和 Release 构建与现有 release 证书一致，Release 不含自测入口。结束时已恢复测试前模块并核对哈希，独立测试包及 runner 不存在。此结果不改变下节普通大图下载 `no_match` 或真实消息功能尚未验收的结论；执行方式与证据范围见[适配手册](wechat-version-adaptation-playbook.md)。

## 2026-10-29 3200 当前分支初次设备验证

设备已安装微信 8.0.79/3200 的 base APK SHA-256 与下节离线样本相同。在签名 Debug 模块 `912a574` 的微信主进程连续两次启动中，Emoji、合成回复画像、应用内回复、撤回和图片事件分别得到 `match=exact` 的 ready 日志；普通大图下载两次均为 `large_unavailable reason=no_match`。源码中 `og5.k0` 是接口，`f(String,String[],int): Cursor` 为抽象方法，而 `WeChatImageDownloader.resolve()` 使用的 `method()` 拒绝抽象方法；这是该失败的明确候选原因，仍待定向运行时确认。ready 只证明对应 hook 安装或画像解析，未执行真实消息、回复、收图、撤回或通知外观验收。设备 framework instrumentation 只有 4/69，通过数受跨 APK 类加载器 `IllegalAccessError` 影响，不代表本次功能回归；测试 APK 已卸载并核验不存在。详细边界和本地证据位置见[适配手册](wechat-version-adaptation-playbook.md)本日小节。

## 2026-10-29 APKMirror 3183/3200 离线适配

下文 2026-10-28 至 29 日写的“3183 无 APK”“3200 仅 Emoji 命中”是当时的历史状态；本节记录新取得安装包后的实现。两个构建的画像仅在 `versionName == 8.0.79 && versionCode == 对应值` 时精确优先；同名未知代码仍逐功能探测完整签名，零命中或多候选歧义时关闭该组件。画像缓存修订版升至 2，新进程读取旧候选不会直接启用，缓存命中也须重新探测。原生 `PendingIntent` 回复及 framework 通知外观与这些微信画像独立。

| 构建与来源 | 本地忽略目录文件 | SHA-256 | 包签名 |
| --- | --- | --- | --- |
| [APKMirror 8.0.79/3183 APKM](https://www.apkmirror.com/apk/wechat/wechat/wechat-8-0-79-release/wechat-8-0-79-3-android-apk-download/)，32 splits | `.debug-artifacts/wechat-same-name-profile-probing/apkmirror-8079/wechat-8.0.79-3183.apkm`；抽取 `3183-base.apk` | APKM `11a4fe01140090ae14874db2cd6a40a34680504bd0394fb547c10126c0ecab0c`；base `90096f785f1227f5456f3d099ea9855822d5601754565de5dc45ab5c1df7e3f0` | Tencent 证书 SHA-256 `0fe4ff85c215918396dadc7cd8ce6963339af33d37751a56e54c7206b63a3c7c` |
| [APKMirror 8.0.79/3200 APK](https://www.apkmirror.com/apk/wechat/wechat/wechat-8-0-79-release/wechat-8-0-79-2-android-apk-download/)；腾讯官方 CDN 取得的文件与该页 MD5、SHA-256、字节数完全一致 | `.debug-artifacts/wechat-same-name-profile-probing/apkmirror-8079/wechat-8.0.79-3200.apk` | `f2a725bc04302ad218a563533ae79356e66b64aa711b275bc919a8d432f7e15d` | 同一 Tencent 证书 |

`apkanalyzer` 对 base/APK 分别读出 3183/3200 和 `8.0.79`；`apksigner` 核对证书。反编译证据保存在同目录 `3183-sources/sources/`、`3200-sources/sources/` 和同目录的单类源码文件；以下均采用 JADX 注释里的**原始**混淆名，不能使用它生成的 `f数字` 别名作为 hook 名。

| 独立功能 | 3183 原始描述符 | 3200 原始描述符 |
| --- | --- | --- |
| 通知栏合成回复 | `MMAutoMessageReplyReceiver.onReceive(Context, Intent): void`；`z2.s1.b(Intent): Bundle` static；`lt1.a.c()/d()/b(): boolean` static | 接收器、helper 相同；门禁为 `os1.a.c()/d()/b(): boolean` static |
| 通知原生 Emoji | `com.tencent.mm.smiley.g.d(String): String`；`com.tencent.mm.booter.notification.m0.a(storage.e9,long,boolean,boolean,String,int,String,String,Intent,boolean): void` | 两个实例方法签名相同 |
| 应用内回复 | `storage.e9.r1(int): void` 本类覆盖；`C0/M0(): int`、`N0(): String`、`getMsgId/getCreateTime(): long`、`a3/M2/F2(): boolean` | `storage.e9.s1(int): void` 本类覆盖；`z0/M0(): int`，其余相同 |
| 撤回 | `RevokeMsgEvent.g: en.js`、`en.js.c: storage.e9`；`e9.I0(): long`、`N0(): String` | `RevokeMsgEvent.g: fm.js`、`fm.js.c: storage.e9`；`e9.H0(): long`、`N0(): String` |
| 图片事件 | `gh5.z.f(String): Object`、`lr3.f`、`sb0.l.l(gh5.z): jh5.b`、`handleDataFromRemote(gh5.z,h61.e): jh5.b`、`handleDataFromFile(gh5.z,gb0.d): jh5.b`；`v61.l0.I3(e9,String,boolean): String` | `jg5.z.f(String): Object`、`oq3.f`、`sa0.l.l(jg5.z): mg5.b`、`handleDataFromRemote(jg5.z,h51.e): mg5.b`、`handleDataFromFile(jg5.z,ga0.d): mg5.b`；`v51.l0.s4(e9,String,boolean): String` |
| 图片消息身份 | `e9.N0(): String`、`C0(): int`、`I0(): long`；父类 `hn.c8.field_msgSvrId: long` | `e9.N0(): String`、`z0(): int`、`H0(): long`；父类 `im.c8.field_msgSvrId: long` |
| 普通大图下载 | `pq0.i1.x(): pq0.a0` static、`pq0.a0.f: lh5.k0`、`lh5.k0.f(String,String[],int): Cursor`；`kj5.n0.c(Class): kj5.m` static，`db0.e implements eb0.y`、`Bj(): eb0.x`、`v61.j implements eb0.x`、`v61.j.b(long,MsgIdTalker,int,Object,int,eb0.w,int,boolean): int` | `pp0.i1.x(): pp0.a0` static、`pp0.a0.f: og5.k0`、`og5.k0.f(String,String[],int): Cursor`；`ni5.n0.c(Class): ni5.m` static，`da0.e implements ea0.y`、`Bj(): ea0.x`、`v51.j implements ea0.x`、`v51.j.b(long,MsgIdTalker,int,Object,int,ea0.w,int,boolean): int` |

两版图片都继续校验 `com.tencent.mm.vfs.z6.i(String,boolean): String` static、`MsgIdTalker(long,String)` 和 `MMWXGFJNI.wxam2PicBuf(byte[],int,int): byte[]` static。`simplemsginfo` 的原始 `d: int` 基准偏移与消息字段类型逐项核对；3200 的 `sa0.k` 是另一个协程远端下载阶段，事件 hook 选择仍具有完整同步签名的 `sa0.l`，不把 `sa0.k.i(...)` 当成旧 hook。撤回还要求 `NotificationItem.f/h/i`、`a(Context): void`、事件直接继承 `IEvent` 及 `IEvent.e(): boolean`，不因负载类存在就直接启用。

各组件在微信主进程分别做完整反射签名校验并批量安装：回复看 `profile_ready` 与接收器/门禁安装，Emoji 看 `emoji_hook_ready`，图片看事件/下载 ready，应用内回复看 `app_reply_ready`，撤回看 `recall_ready`。任一组件签名或安装失败只关闭自身，保留微信原生行为；日志不记录账号、消息或身份。以上是 APK 离线源码核对与主机测试范围，不等于 3183/3200 的运行时 hook 安装或真实回复、收图、撤回、通知 UI 验收；此前 3200 的 `emoji_hook_ready match=experimental` 只属于旧模块，不代表本次精确画像已做真机回归。

## 2026-10-31 图片占位与刷新呈现

本次没有新增或修改微信混淆 hook、映射或版本门禁。重复图片仍用下文已核验的会话/服务器消息 ID 选取事件；展开视图只在 render 副本中清空精确 incoming 图片占位。为避免已显示的 SystemUI `MessagingLayout` 把旧占位行留在淡出 transient view 中，对图片预览开启且带占位的待图 RemoteViews 反射设置并验证 `FLAG_REAPPLY_DISALLOWED`。AOSP 固定源码只检查旧 RemoteViews 的该标志，并在重绑禁止时 inflate 新展开树；接口不可用时不显示补图，保留 `[图片]`。若 SystemUI 仍缓存变更前的无 flag 视图，或待图 inflate 被更新取消，新 READY 视图的 flag 不能保证首次转换也 inflate；这项边界未在真机验证。源通知、原始消息与回复历史不改写。

图片获取与微信下一条文字通知存在异步交错：发布前仍检查请求实例和活动 token；安全连续的文字通知会安排最多 4 次、每 150ms 的重检，在补图先于原文字 notify 提交时尝试重新挂图。未用 loader 锁覆盖 `NotificationManager.notify()`，避免反向锁序。该机制尚无真机/SystemUI 回调与 OEM 视觉验收；固定 AOSP 源码说明见[适配手册](wechat-version-adaptation-playbook.md)。

## 2026-10-30 重复图片的精确通知身份复用

图片请求改为优先使用通知中已有的核验会话与服务器消息 ID。沿用本文件“应用内回复与撤回”的离线证据：`notification/x.java:533` 将消息 `I0(): long` 传给 `m0.a(...)`，`m0.java:543/963` 写入 `NotificationItem.i: long`；`NotificationItem.a(Context): void` 同步发布 `f: Notification`。已有 BEFORE hook 将 `h: String` 会话与 `i` 写入通知 extras，图片路径事件的 `hn.c8.field_msgSvrId: long` 及身份事件的 `I0()` 与之关联。源码位置、来源 APK SHA-256 与下文记录相同，本轮未更换 APK 或增加混淆目标。

通知增强在归一化前固定这组身份。对核验消息按会话和服务器 ID 精确选取，避免重复发送同一图片时重新观察到的旧身份造成误冲突；相同文件不合并消息。有精确 ID 但事件未到、路径未到或多个本地消息声称同一 ID 时不退回其他图片；缺核验身份时原时间窗口与多候选拒选保持。待处理请求去重也比较该身份，后续文字仍验证原图片的连续性。图片仅精确匹配版本名 `8.0.79` **且**版本码 `3182`；其他图片画像、通知身份、回复、普通大图、应用内回复和撤回保持原独立门禁。没有修改 hook 签名或回调时机。

本轮新增主机回归及补图/Parcel 身份保留的 framework 用例；没有手机，运行时 hook 安装、真实重复图片收发、跨会话及 SystemUI 显示均未执行。先前临时 Debug 包的真机通过不能当作本次最终代码的验收。

## 2026-10-30 真实通知复测补记

Android 17 真机、微信 8.0.79/3182、模块通知增强与 Markdown 开启。在微信私聊中，单独发送 `~~123~~` 后用户确认通知显示删除线；`## Lorem ipsum` 保留 `##`，因为当前解析器未实现标题。图片补图的 Debug 实时日志曾出现 `no_unique_message_event`，另一次不同图片完成解析、解码和发布，系统通知记录包含 `imageReady=true`。SystemUI 已加载展开 Hook 并通过归属检查，但用户展开后仍看不到图；记录中 `EXTRA_PICTURE` 已为空，`EXTRA_PICTURE_ICON` 为 URI 图标。详见[通知外观](wechat-notification-appearance.md)。

后续代码按用户批准的方案加入行首 `# ` 和 `## ` 展示解析：去掉标记，一级标题粗体，二级标题原字号中等字重，沿用系统会话通知布局；原文、删除线及代码规则保留。同步增加二级标题字体在系统清理后的窄范围恢复，复用原 owner/原文一致性与 hook 签名门禁。手机已拔掉，新标题实现仅可完成主机测试及框架测试编译，本段先前的真实删除线结果不构成标题验收。标题视觉与系统清理回调须在设备重连后测试；详情见[通知 Markdown](wechat-notification-markdown.md)。

用户随后提供的私聊通知截图确认缩略图贴在卡片左缘，而原消息正文从头像右侧开始。设备 framework 的 2025 通知内容缩进为 58 dp；新增图片作为 `notification_main_column` 的子视图没有继承它。私聊和群聊共用的 `wechat_group_image_preview` 现在仅在 2025 展开会话/消息模板下按 framework 实际尺寸给图片起始侧加入相同 padding。非消息通知的 `wechat_image_preview` 回退不改；模板或尺寸资源缺失时保持原行为。新增私聊/群聊及回退模板合成用例尚未上机，不能把此前“看到缩略图”的结果当作对齐修正的视觉验收。事件候选有歧义时继续拒绝补图，避免通知时间滞后引起旧图误配。

本轮没有修改微信混淆 hook 目标、完整签名、作用进程或版本画像；图片路径仍须版本名 8.0.79 **且**版本码 3182 同时匹配，并经过原有运行时签名校验与图片开关。Android/SystemUI 的 `Notification.Builder.createExpandedContentView(): RemoteViews` AFTER hook 目标及完整签名不变，仅调整有图判断与传递方式；缺失资源或应用失败保留 `[图片]`，Debug 记录不含私人内容。新图片图标支持的合成 framework 回归已在手机上 77/77 通过、测试包卸载。安装最终 Debug、用户手动重启 SystemUI 后，同一私聊的新图片通知由用户确认看到了缩略图；实时日志显示 `bitmap=false pictureIcon=true` 且展开 Hook 返回新视图。随后再次发送相同图片，用户仍看到缩略图，实时日志也记录新请求的解码、补发与 SystemUI 图标渲染。群聊和撤回后的视觉结果仍未单独验收。原始通知和日志只留在本地忽略目录 `.debug-artifacts/live-diagnosis-20261030/`。
## 2026-10-28 8.0.79/3183 应用内回复与撤回实验性探测

3182 仍按版本名 **8.0.79 且 versionCode 3182** 精确优先。3183 及其他同名新代码仅在各组件完整签名唯一通过时分别标记 `experimental`。应用内回复要求 `storage.e9.r1(int): void` 本类覆盖、实例 `C0/M0(): int`、`N0(): String`、`getMsgId/getCreateTime(): long`、`a3/M2/F2(): boolean`；撤回要求 `NotificationItem.f: Notification/h: String/i: long`、`a(Context): void`、`RevokeMsgEvent.g: en.js`、`en.js.c: storage.e9`、事件直接继承 `IEvent`、`IEvent.e(): boolean`、`e9.I0(): long` 与 `N0(): String`。撤回双 BEFORE hook 全装前回调惰性；第二个失败时撤销首个，无法撤销的旧回调也保持惰性。两项独立于通知回复、Emoji 与图片开关；无匹配或歧义只关闭相应组件。3183 无 APK 来源、SHA-256、反编译位置或目标原始名证据；本次主机合成类型测试与构建不能证明字段语义、真实微信 hook、应用内发送/撤回行为或跨启动缓存复用。

## 2026-10-28 8.0.79/3183 及其他同名新代码的实验性探测

3182 精确画像继续要求版本名 **8.0.79 且 versionCode 3182**。3183 无安装包，来源、SHA-256、反编译位置及目标构建的原始混淆名未知；这里只让运行时独立尝试沿用本文件已核对的 3182 候选，绝不宣称 3183 布局相同。合成回复要求 `MMAutoMessageReplyReceiver.onReceive(Context, Intent): void`、静态 `z2.s1.b(Intent): Bundle`、`lt1.a.c()/d()/b(): boolean` 三个静态门禁完整通过，且三道门禁 hook 全部安装成功；失败时只保留原生 `PendingIntent` 与既有 legacy 回退。Emoji 独立要求实例非 abstract `com.tencent.mm.smiley.g.d(String): String` 与 `com.tencent.mm.booter.notification.m0.a(com.tencent.mm.storage.e9,long,boolean,boolean,String,int,String,String,Intent,boolean): void` 两个完整签名和直接调用者取消优化成功；开关仍实时读取。其他图片、应用内回复、撤回需各自完成探测，不能借用这两项结果。

日志把新代码标为 `experimental`，`ready` 只表示当前进程 hook 安装，绝非真实回复或 Emoji 显示验收。主机测试覆盖合成类型和失败回退；3183 仍无 APK 的离线核对、运行时注入或真机消息/OEM 检验。2026-10-28 至 29 日 API 36、8.0.79/3200 的真机试验确认旧模块 provider 报 `Unknown authority`，显式广播引导 Binder 长期排队，显式服务绑定返回 `false`；这些跨进程入口已从最终代码移除。当前候选缓存仅在微信应用自身私有偏好中同步保存画像 ID，键包含功能、版本名、长版本码、更新时间及画像修订版。3200 首次运行记录 `candidate_persisted feature=emoji`，下次微信进程启动记录 `cache_revalidated feature=emoji`，证明该候选的缓存读取和完整签名再次通过。两项日志不代表 hook 安装或 Emoji 实际显示；回复、图片、应用内回复、撤回门禁及运行时验证仍独立。

2026-10-29 最终签名 Debug 版在 8.0.79/3200 的微信主进程记录 `emoji_hook_ready match=experimental`；合成回复、应用内回复、撤回、图片事件和大图下载均因各自 `no_match` 关闭。framework 头像、折叠头和展开头 hook 均 ready；Markdown 代码样式目标因 `NoSuchMethodException` 不可用。最终版 249 项主机测试通过，安装包回拉哈希一致，测试包不存在。3200 的这些日志仅证明对应 hook 安装或保守门禁；没有真实消息、回复、图片、撤回和通知显示验收，3183 也未获 APK。

更新日期：2026-10-16。模块版本：3.2.1 / code 14。

2026-10-26 当前实现：恢复 `fbcf426` 之前的 ID 分离，固定 SQL 重新读取 `msglocalid`，仅用于 `MsgIdTalker`，下载首参仍为基础图片行 `id`；消息本地 ID 非正数时跳过主动下载，保留本地候选。设备微信 base APK 哈希与下文来源一致，重新核对 `v61/j.java:65`、`v61/t0.java:604/621/625` 和 `v61/b0.java` 的字段读写后确认两个 ID 语义独立。四组版本画像、原始类/方法名及完整签名不变，不用反编译器别名作为 hook 名称。

2026-10-26 同轮新增可配置文件等待值 `WeChatDecorator.image_preview_wait_ms`：默认 1500 毫秒，100–30000 毫秒，设置页与远端存储使用数字字符串，非法值回退默认。文件重试、画质等待和普通大图观察使用每请求固定的同一截止点；两项图片开关及微信主进程初始化时机保持不变。历史 1.5 秒性能数据对应默认配置，不证明新配置下的真实收图效果；当前实现和回归边界见[适配手册](wechat-version-adaptation-playbook.md)。

2026-10-24 历史变更曾将消息对和下载首参都设为基础图片行 `id`；该行为本轮已撤销。真实下载和通知展示仍需独立验收，不能将签名检查或主机/framework 测试等同微信端成功。

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

## Unicode emoji 的通知占位符（2026-10-23 离线补查）

本版本的通知构造会经过 `com.tencent.mm.smiley.g.d(String): String`；其 `com.tencent.mm.smiley.p0.a(a1, int, int): boolean` 回调在 emoji 名称查询失败时写入 `R.string.a11`，base 资源实值为 `[表情]`（`0x7f100411`）。所以 `[表情]` 也可能来自原始 Unicode emoji 的转换，不能一概视为贴纸消息。

按 split-4.apk 自带 `assets/color_emoji` 和 base 中文名称表模拟，👌、😀、🙂、🤔、😎 落入通用占位符；😊、😂、👍 分别有 `[开心]`、`[破涕为笑]`、`[强]` 名称。运行时 `color_emoji_new` 更新资源、语言及其他构建可能改变结果；本次未在微信进程执行内部函数或真机收发测试。完整调用、数据字段、反编译位置、资源来源及验证边界见[通知表情调查](wechat-emoji-notifications.md)。

2026-10-23 增加默认关闭的 `WeChatDecorator.preserve_emoji`：仅版本名 8.0.79 **且**版本码 3182，在主进程 `Application.onCreate(): void` AFTER 初始化后安装。两个精确实例方法分别为 `com.tencent.mm.smiley.g.d(String): String` 的 BEFORE、`com.tencent.mm.booter.notification.m0.a(com.tencent.mm.storage.e9, long, boolean, boolean, String, int, String, String, Intent, boolean): void` 的 BEFORE/AFTER；前者只在后者的当前线程嵌套作用域内、总开关和新开关同时开启时返回原始参数。完整参数、返回值、非 static / 非 abstract 校验及直接调用者 `m0.a(...)` 的取消优化成功后才安装；两项 hook 完成前不激活，部分失败则回退并清理。未知组合、签名不符、取消优化/安装失败均保留微信转换。`WeChat.Emoji` 的 ready 日志只证明安装，真实 emoji 显示、设置切换、其他调用者、回复与撤回行为仍须真机回归。来源和完整回归矩阵见上述专题文档，不影响其他功能画像。

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
`id/msglocalid/msgSvrId/msgTalker/bigImgPath/hevcPath/midImgPath/offset/totalLen/reserved1`。
下载接口参数形状不变，仍选择普通图、只查基础行，不沿 reserved1 额外查询原图；
任务上限、大小限制、等待、取消和缩略图回退保持现有实现。
图片事件与下载服务分别校验，预览总开关关闭时不安装；普通大图请求还要求大图开关开启。
未知版本或签名不符时对应组件关闭，不扩大匹配范围。

### 普通大图的图片 ID 与消息本地 ID（2026-10-23 离线补查）

使用上文同一 ADB 来源、同一 SHA-256 的 8.0.79/3182 APK，本次未重新采集设备。
`.debug-artifacts/wechat-8.0.79/sources/sources/v61/l0.java:71` 的原始 schema 分别声明
`id INTEGER PRIMARY KEY` 与 `msglocalid INT`，并为 `msglocalid` 建索引；两者不是同一个 ID。
`com/tencent/mm/plugin/msg/MsgIdTalker.java:20` 的构造函数把 `long` 存入 `d: long`、
把会话存入 `e: String`；`a(): String` 返回会话。
`v61/j.java:65-70` 的 `b(long, MsgIdTalker, int, Object, int, eb0.w, int, boolean): int`
将首参及 pair 的 `d` 分别交给任务。`v61/t0.java:599-625` 再分别以图片 ID 调用
`v61.l0.y2(Long): v61.b0` 查询图片、以消息本地 ID 调用
`oy0.k0.Zi(String, long): com.tencent.mm.storage.e9` 查询消息，后者的 `I0(): long`
用于请求的消息服务端 ID。源码原名取自本地反编译，不使用生成的字段别名作为 hook 目标。

共用下载器因此新增读取 `msglocalid`，仅用于 `MsgIdTalker`，首参继续使用基础行 `id`；
缺列时回退，消息本地 ID 非正数时跳过主动下载，保留本地文件预览。运行时反射签名、微信主进程、
图片总开关/大图开关、8.0.79 **且** 3182 门禁及其他功能画像均沿用原规则。
已有图片可由本地命中或微信自身下载提供；`v61.t0.H(v61.b0, String, long): boolean` 的 CDN
分支还从图片行元数据取得 URL/密钥，不能从看到大图反推消息参数正确，也不能将本缺陷描述为必然下载失败。
新增主机回归检查 Cursor 读值及实际反射提交参数，使用互不相同的图片、消息、服务端和关联原图 ID；
另验无效消息 ID 不提交且不丢弃已有本地行。真实微信主动下载、取消、超时与各网络分支仍待授权设备验证。

本次 162 项主机测试通过，Debug/仪器 APK 构建及现有 release 证书一致性校验通过；没有安装或执行设备测试。

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
不构造消息对、不查询数据、不获取服务、不触发下载或更改通知。候选正缓存通过模块
provider 持久化，并在每个新进程重验；缓存不可用时仍执行现场签名探测。

现有图片预览和大图开关继续分别控制安装；本地图片优先、缩略图和等待超时回退保持不变。
失败诊断保留 `events_unavailable` / `large_unavailable` 与原因或 stage/type。
主机测试覆盖同名选择、独立匹配、错误 state/download 签名、继承拒绝与部分 hook 失败。
没有 3183 APK，未做该构建离线字节码验证、运行时 hook 安装、真实图片/消息或 OEM 行为验证；
签名匹配和主机测试通过不等于设备行为兼容。

### 2026-10-28 合成回复接收器完整预检与安装门禁

微信主进程的通知栏合成回复（已知画像、legacy 回退及同版本名未知代码的实验性候选）现在统一要求接收器继承 `android.content.BroadcastReceiver`、类为 public 且非 abstract，并具有 public 无参数构造函数；入口仍为 public、非 static、非 abstract 的 `onReceive(android.content.Context, android.content.Intent): void`。构造检查仅用反射读取描述符，不创建接收器或派发消息，对齐实际 `getDeclaredConstructor().newInstance()` 不调用 `setAccessible` 的派发约束。RemoteInput helper 和三道 static boolean 无参车载门禁的原有完整签名校验不变。

`Application.onCreate(): void` AFTER 的初始化只有在该画像接收器 hook 成功且三道门禁全部安装成功后，`MainHook.isSyntheticReplyAvailable()` 才返回 true；接收器 hook 失败保留 `hookAutoReplyReceiver failed` 诊断并关闭合成动作，不能仅凭 `profile_ready` 或门禁 ready 判为可用。原生 `PendingIntent` 回复独立于上述条件，现有聊天、会话 key、contentIntent 和功能开关条件继续生效；应用内回复、图片和撤回也各自独立。版本名与代码的既有精确/OR/legacy 规则以及同名未知代码唯一匹配规则不变。

主机回归覆盖 receiver hook 未成功而门禁已 ready、成功状态恢复，以及 abstract、无无参构造、private 无参构造和非 public 接收器的拒绝；覆盖构造与安装门禁不代表消息已送达。3183 仍无 APK 来源、SHA-256 或反编译证据，本轮未安装设备、未验证运行时 hook、真实回复或 OEM 展示。

2026-10-28 试验性 IPC 版本的设备 instrumentation 为 4/70，大量旧用例因测试 APK 与目标 APK 跨类加载器的 package-private `IllegalAccessError` 失败；单独修正后的接收器故障注入为 1/1 通过。该 IPC 路径已移除，这些数字不代表当前缓存实现的验收。3200 当前实现的首次持久化及下次进程复核由上述运行时日志单独确认；3183 仍待取得 APK 和对应真机证据。
