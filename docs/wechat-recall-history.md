# 撤回通知历史裁剪

更新日期：2026-09-30。源码实现及离线映射已完成；真实微信运行验证待执行。

8.0.77 新增两组精确画像：3141 为 `RevokeMsgEvent.g: en.gs`、`en.gs.c: storage.e9`，
会话/服务端 ID 为 `N0(): String` / `J0(): long`；3160 为 `fm.fs`，对应 `Q0()` / `K0()`。
通知发布字段、BEFORE hook 与精确 ID 语义沿用并逐项核对；3141 已确认 `recall_ready`，
33 项框架回归通过，真实消息撤回裁剪待验收。完整证据见 [8.0.77 记录](wechat-8.0.77-findings.md)。

## 精确关联依据

微信通知正文、ticker、CarExtender 消息列表没有稳定的逐条消息 ID。仅解析“撤回了一条消息”不能确定撤回目标；旧实现把提示作为当前来信，经 `NotificationMessages.rebuild()` 与旧规范化快照合并，因而保留 B 并追加 C。

本次直接读取微信发布通知所用的 `NotificationItem`，把服务端 ID 保存到当前来信的消息 extras。该字段在 `toString()` 中标为 msgId，但通知工具的实参证明它是服务端 ID，不能与 `getMsgId()` 返回的本地 ID 混用。

| 入口 | 8.0.72 / 3085 | 8.0.78 / 3180 |
| --- | --- | --- |
| 通知发布 | `NotificationItem.a(Context): void`；`f: Notification`、`h: String`、`i: long` | 同左 |
| 通知 ID 的来源 | `notification.x` 把 `f9.I0(): long` 传给 `m0.a(...)`，后者写入 `NotificationItem.i` | `notification.x` 把 `e9.F0(): long` 传给 `m0.a(...)`，后者写入 `NotificationItem.i` |
| 撤回事件 | `RevokeMsgEvent.g: pm.ds`，`ds.c: storage.f9` | `RevokeMsgEvent.g: fm.ks`，`ks.c: storage.e9` |
| 事件发布 | `IEvent.e(): boolean` | 同左 |
| 撤回记录的会话和服务端 ID | `f9.O0(): String`、`f9.I0(): long` | `e9.N0(): String`、`e9.F0(): long` |

离线证据保存在 Git 忽略的 `.debug-artifacts/`：

- 8.0.72 基础 APK SHA-256：`59cfc54474ed23ff7276d1eea36ea779db7e6893d7b2cb925f28a0f3301784be`。完整源码在 `device/20260910-234552/sources/sources/`；`b01/u.java` 的撤回处理填充 `RevokeMsgEvent.g.c` 并调用 `e()`，通知监听器使用同一记录触发通知。
- 8.0.78 基础 APK SHA-256：`ff507d8ca93d735342a5e29d374beadce166e1c3cba8302f655f0c194331fb3b`。本次摘录在 `recall-8078/`、`recall-map-8078/`、`recall-data-8078/`；通知监听器读取 `RevokeMsgEvent.g.c`，通过 `F0()` 把同一服务端 ID 送入通知工具。
- JADX 的 `f64279f` 等名字是输出别名。运行时采用其注释中标明的原名 `f/h/i/g/c`，并核对字段类型、方法参数、返回类型及事件父类。版本名或版本号不符、任一签名校验失败时，不启用该版本的精确 ID 关联。

## 裁剪行为与边界

- 按已验证的会话 key 和正数服务端 ID 关联，私聊、群聊共用同一路径。撤回事件到来后，更新该会话的归档删除记录；对仍在通知栏且最新代次 token 匹配的通知，在主线程重建。再次检查归档实例，避免旧事件重新发布已移除或已替换的通知。
- 重建时先剔除目标 B 和系统提示 C，再进行身份规范化、轮次推进及历史合并。提示不是来信，不推进对方消息时间；有精确 ID 的同文同时间消息保持各自身份。
- 删除记录随规范化快照和图片样式重建保存；即使重新发布旧规范化快照，也从同一会话归档合并删除记录，防止 B 复活。正文回退、`EXTRA_MESSAGES`、RemoteInput 历史及可关联的回复轮次 ID 同步裁剪。
- 若没有任何剩余消息，取消该会话通知并通过 `StopPost` 阻止空撤回提示重新发布。撤回重建时取消该会话待发布或缓存的图片预览，回退到剩余消息的文字展示，避免旧图片再次出现；剩余图片的视觉回归属于计划第 4 项。
- 进程内最多保存 64 个会话、每个会话 128 个撤回 ID；通知快照保留同样有界的删除记录。日志只记录版本、阶段及异常类型，不输出会话 key、消息 ID、正文或联系人信息。

保守行为：没有精确 ID 的旧消息不会被删除；只排除已确认的系统撤回提示。通知栏本人回复目前只有模块 UUID，不能仅凭应用内成功事件的顺序将它绑定到服务端 ID，因此这类缺少精确 ID 的本人消息同样保留。未支持版本、签名失败、未经过上述通知发布入口或异步事件入口的场景不承诺精确裁剪，后续适配需单独核对。

## 自动化覆盖与设备验收

`RecallHistoryTest`、`RecallIndexTest` 覆盖正数精确 ID、重复撤回、有界状态、会话隔离和精确版本门禁；`ConversationRoundTest` 覆盖裁剪后的回复 ID 清理。

`Plan123Instrumentation` 覆盖真实 Android Bundle / MessagingStyle 的 A → B → C → D、私聊与群聊、缺少 ID、同文同时间、连续撤回、旧规范化快照重放、图片身份元数据、可精确关联的本人回复、回复历史与轮次，以及通知 ID 复用。仪器 APK 编译通过不代表这些用例已执行。

2026-09-30 后续按用户授权安装并执行：PJZ110 / Android 16 上全部 33 项模块仪器测试通过，包括上述撤回用例和空撤回抑制。测试命令使用 `--no-hidden-api-checks` 以允许本测试进程访问 framework 隐藏字段；合成资格检查在普通进程缺少 Xposed API 时保守返回不可用。没有重启微信/SystemUI，两包 release 签名及安装后回拉哈希已核对；完整执行记录见[项目修改计划](project-modification-plan.md)，日志在 `.debug-artifacts/device/20260930-plan123-instrumentation/`。此结果验证模块的 framework 逻辑，未执行真实微信事件 hook。

真机仍需验证两种微信版本上的 `source=recall_ready`、事件时序、A/D 保留及 B/C 移除、主动撤回本人消息的保守行为、通知清除后不复活和图片文字回退。安装及运行按当次授权执行；不需要也不自动执行整机重启。
