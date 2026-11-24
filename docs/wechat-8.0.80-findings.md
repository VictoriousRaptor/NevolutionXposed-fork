# 微信 8.0.80 / 3200 适配记录

样本来自指定真机上已安装的 `com.tencent.mm`。包管理器报告 versionName **8.0.80**、versionCode **3200**。从设备回拉的 base APK SHA-256 为 `2acfe78eab287899c2d7ee77ccdb624e57af5cdb1969e3418fb22daceb9515d3`。样本、DEX、原始描述符和定点 JADX 输出保存在本地忽略目录 `.debug-artifacts/wechat-8.0.80/`。8.0.79 的同号安装包另有独立映射。

下表使用 DEX 原名。`storage` 代表 `com.tencent.mm.storage`，`events` 代表 `com.tencent.mm.autogen.events`。String、Object、Boolean、Runnable、Intent、Context、Bundle、Bitmap、Notification、Cursor 分别使用 Java 或 Android 平台类型。

| 功能 | 本次核对的签名和语义 |
| --- | --- |
| 通知回复 | `com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver.onReceive(Context,Intent): void` 调用静态 `a3.s1.b(Intent): Bundle`，读取 `key_voice_reply_text`，使用 `bu1.a.c()/d()/b(): boolean` 三个静态门禁。沿用接收器 BEFORE、辅助方法和既有车载门禁处理。 |
| 消息身份 | `storage.f9` 继承 `dz0.q3`、`ln.d8`。实例方法 `I0(): long` 读 `field_msgSvrId: long`，`P0(): String` 读 `field_talker: String`，`D0(): int` 读 `field_isSend: int`。本地 ID、创建时间与消息类型仍由 `getMsgId(): long`、`getCreateTime(): long`、`getType(): int` 提供。 |
| 已读与头像 | `public boolean storage.m4.b0(String)` 为本类实例方法，包含 updateUnreadByTalker 分支。BEFORE 捕获当前会话轮次，AFTER 仅在正常返回 true 时提交。头像读取 `public static Bitmap com.tencent.mm.modelavatar.g.a(String)`。 |
| Emoji | `com.tencent.mm.booter.notification.m0.a(storage.f9,long,boolean,boolean,String,int,String,String,Intent,boolean): void` 直接调用 `com.tencent.mm.smiley.g.d(String): String`。两者为实例方法，沿用通知构建范围内的转换处理和调用者取消优化。 |
| 撤回 | `events.RevokeMsgEvent.g: in.hs`，`in.hs.c: storage.f9`。事件直接继承 `com.tencent.mm.sdk.event.IEvent`，沿用 `e(): boolean`。通知入口 `com.tencent.mm.booter.notification.NotificationItem.a(Context): void`，字段 `f: Notification`、`h: String`、`i: long`。 |
| 图片事件 | `dc0.l.l(gh5.z): jh5.b`、`handleDataFromRemote(gh5.z,y61.e): jh5.b`、`handleDataFromFile(gh5.z,rb0.d): jh5.b` AFTER。状态读取 `gh5.z.f(String): Object`。消息对象 `ks3.g` 继承 `ks3.h.d: int`，原始构造器按 localId、svrId、createTime、talker、type、…、isSender 注册字段，对应偏移 0、1、2、3、4、9。使用继承的 `getLong(int): long`、`getString(int): String`、`getInteger(int): int`。普通路径入口为实例方法 `m71.k0.z4(storage.f9,String,boolean): String`。 |
| 图片数据库与下载 | 静态 `fr0.j1.x(): fr0.b0`，唯一实例字段 `fr0.b0.f: lh5.k0`，`lh5.k0.f(String,String[],int): Cursor`。静态 `kj5.n0.c(Class): kj5.m` 解析服务 `pb0.y`，实现 `ob0.e.Kj(): pb0.x`。下载实现 `m71.j.b(long,com.tencent.mm.plugin.msg.MsgIdTalker,int,Object,int,pb0.w,int,boolean): int`，消息对构造器 `(long,String)`。VFS 静态 `com.tencent.mm.vfs.z6.i(String,boolean): String`，特殊解码静态 `com.tencent.mm.plugin.gif.MMWXGFJNI.wxam2PicBuf(byte[],int,int): byte[]`。 |
| 中图 | 静态 `pw5.e0.valueOf(String): pw5.e0` 取 MID_IMAGE，`pw5.x.a(storage.f9,pw5.e0): pw5.x` 创建描述，`pw5.y.g(pw5.x,Boolean,boolean,int,Object): String` 获取路径。沿用消息身份核对，解析失败仅跳过中图路径。 |
| 语音转写 | AFTER 观察服务端 ID getter。文件标识由 `storage.f9.C0(): String` 提供，当前账号由静态 `c61.y1.u(): String` 提供。静态 `w81.w0.Ij(): storage.lb`，继而 `storage.lb.r1(String): storage.kb` 读取缓存的 `field_msgId: long`、`field_content: String`。静态 `w81.w0.Kj(): w81.n1`，`w81.n1.r1(String): w81.d1` 取得音频记录。字段 b/x 为 String，m/e 为 long，g/h/i 为 int，分别为文件名/会话、本地/服务端 ID、已收/总字节数/状态。资源类型 AUDIO，媒体服务 `jq3.u0` 的实现 `pd0.b.kk(pw5.x,String,boolean): String`，VFS 静态 `z6.j(String): boolean`、`z6.k(String): long`。 |
| 语音事件 | `events.ExtTranslateVoiceEvent()` 直接继承 IEvent，g/h 为 `in.z8` / `in.a9`。输入 a/b/g 为 String，c/d/f 为 int，e 为 Runnable；输出 a 为 String、b 为 boolean。沿用原生事件和完成回调。 |

## 验证范围

主机单元测试 383 项通过。Debug、androidTest、Release 构建通过，三个 APK 均通过签名校验并匹配既有 release 证书。在指定真机用单独的 app_process 进程运行模块的 WeChatDescriptorChecks，加载已安装微信的类，42 项描述符检查通过。临时检查文件已清理。中图资源路径及图片字段顺序另做了 DEX 核对。

本次没有在微信主进程观察 hook 安装，也没有执行真实回复、应用内已读、收图、撤回、语音转写或通知显示验收。
