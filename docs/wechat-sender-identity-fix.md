# 微信通知发送者身份修复

## 行为

- 所有通知构建路径共用 `NotificationMessages`。`sender_person` / Compat Person 优先于旧式 sender；未知身份不作为本人身份。
- 本人消息仅来自模块回复标记或完整 MessagingStyle 的无 sender 消息；群名、昵称和正文位置均不判断本人身份。
- 车载扩展继续提供回复/已读 PendingIntent，但不再用车载列表与 ticker 的位置推断发送方向。普通新通知以 EXTRA_TEXT 为当前来信；无法明确归属的车载历史舍弃。
- 模块回复记录 UUID 和一次真实时间。RemoteInput 历史继续用于系统回复功能，不再转换成伪造当前时间的新消息。
- 归档使用最近一份匹配的已规范化快照；已规范化通知重复发布不再合并旧快照。跨来源副本按身份、非零时间、正文和附件去重，同一来源的重复消息保留。
- 会话标题、已知 talker key 或点击目标变化时隔离旧状态。没有 talker key 时仍需依赖通知会话信息，无法区分所有原始字段都相同的不同会话。
- 图片预览重建显式保留消息 Person、附件、消息 extras 和模块身份元数据。

## 自动验证

PowerShell 7 中使用现有依赖缓存，Android 用户目录使用工作区缓存：

```powershell
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
$env:ANDROID_USER_HOME = Join-Path (Get-Location) '.gradle/android-user'
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --offline --console=plain
```

`MessageIdentityPolicyTest` 覆盖身份决策、稳定排序、去重、重复正文及会话匹配。
`MessageIdentityInstrumentation` 是无外部测试框架依赖的 Android Instrumentation runner，覆盖实际 Bundle/Person 序列化、头像及附件保留、ticker/车载旧数据、回复后新来信、BigPicture 重建和零时间快照。

仪器测试 APK 的编译通过不等于测试已运行。经用户安排安装应用和测试 APK 后，可以运行：

```powershell
adb shell am instrument -w com.oasisfeng.nevo.xposed.test/com.oasisfeng.nevo.decorators.wechat.MessageIdentityInstrumentation
```

## 真机验收清单（待执行）

1. 私聊：对方来信 → 微信应用内回复 → 返回后台 → 对方再次来信。
2. 私聊：通知栏回复 → 对方再次来信；检查自己旧回复保留，最新正文及头像属于对方。
3. 连续收发相同正文；不能因文本相同丢消息或切换身份。
4. 群聊：成员昵称等于群名或“我”，仍不能自动判为本人；未知发送者不能使用本人头像。
5. 分别开启/关闭图片预览，检查图片发布、后续文字及通知重建后的身份。
6. 清除通知、切换会话、再次通知，检查 ID 复用不带入原会话记录。

Debug 日志标签 `WeChat.Identity` 记录字段存在性、身份判定、合并数量及时间，不记录聊天正文、昵称或头像。现场根因仍需通过上述收发场景确认。

## 连续对话修复（低成本范围）

- 私聊消息在合并前统一内部 Person key、名称及可用头像。内部 key 在会话建立时生成，不随异步 talker 补全改变；保存在通知元数据中。会话替换重新分配；群成员保持各自身份。
- 通知回复 A → B 后先保留 A+B；下一条确认更新的对方消息 C 到来时切换为 B+C，再收到 D 显示 B+C+D。连续本人回复 B1/B2 属于一个回复组。下一次 E→F 切换为 E+F。
- 轮次状态及消息归属保存在 extras。只用严格更新的非零对方时间推进；相同时间、旧消息、无时间事件不推进。清理历史时同步清理 RemoteInput 旧回复，图片重建保留轮次元数据。
- 当前轮次接收到的无时间消息会保留，下一轮再清理；旧快照中的无时间历史不能重新进入新轮次。
- 本期**不采集应用内回复**：打开应用回复后，仍不显示本人应用内正文，也无法据此精确切分轮次，只处理私聊发送者身份一致性。

新增 `ConversationRoundTest` 覆盖连续回复、两轮切换、重复回调、旧/零时间事件及有界状态。仪器测试增加 talker 补全与头像刷新、快照恢复、群成员分离、图片重建、旧历史不复活等用例。设备上的视觉效果及微信收发验收仍须单独执行，编译通过不代表真机已通过。

### 2026-09-19 连续对话修复验证记录

- `testDebugUnitTest`：65 项通过，失败及错误均为 0。
- `assembleDebug`、`assembleDebugAndroidTest`：通过；15 项仪器用例已编译，未安装或在设备执行。
- Debug APK 已使用现有 release 密钥签名，并通过 `apksigner verify`。证书 SHA-256：`7a707a90a36c94d231678813514d2ce815ad81fbf79558edc67eab4f288c4df9`。
- 本次 Debug APK SHA-256：`5af2d96d45bdb6937a599ab5e1f91f99f922675d45e43f07987c3792bc675410`。
- `git diff --check` 通过。未自动安装、推送或发布；真机头像分组及通知往返验收待执行。

## 2026-09-21 私聊“未知发送者”修复

已移除根据 ticker/正文前缀猜测群聊的规则。会话类型仅使用已验证 talker、微信原始 MessagingStyle 显式 group 字段、同一会话的可靠缓存；没有证据时为 UNKNOWN，显示会话标题及头像。模块已生成的 group 字段不视作原始证据，新的类型元数据独立保存证据来源。

每次构建使用独立 Conversation 快照，解析、Person、频道和群聊样式共用同一结论。异步 talker 回调仅写入缓存，校验会话实例及请求代次，不修改当前通知。点击 PendingIntent 变化但已解析的原生回复目标相同，不丢弃会话身份；已确认目标变化仍隔离旧会话。

旧的错误群聊占位消息会随私聊重建统一到对方 Person，并清除残留 group 标志及 conversationTitle。仅纠正模块自己的群聊频道，保留免打扰频道。不以字符串替换方式修改真实昵称。

代价：无法确认类型的真实群聊暂时展示会话标题；获得可靠群聊证据后，后续通知恢复成员解析。应用内回复采集仍不在范围内。

验证结果：

- `testDebugUnitTest`：73 项通过，0 失败、0 错误。
- `assembleDebug`、`assembleDebugAndroidTest`：通过；20 项仪器用例已编译，尚未在设备执行。
- 新用例覆盖原始通知首次/连续私聊、ticker 等于正文或缺失、回调前后和旧代次、旧群聊标记修复、真实群聊及证据恢复。
- Debug APK 与既有 release 证书一致，已通过 `apksigner verify`；APK SHA-256：`e3b1562ad7b5449afce6c0641a9a1a416f21d4e8d499f62ee84a5bed6f1340ab`。
- `git diff --check` 通过。当前修复未安装到手机，冷启动首条及连续私聊的实际显示效果待验收。

## 2026-09-21 提交前综合验证与通知移除重置

- 撤回通知包含联系人名不能作为群聊证据。当前实现依据本次会话分类选择撤回类型，不再将带名字的私聊撤回缓存为群聊。通知历史截图与旧逻辑导致的连续“未知发送者”现象吻合，但缺少现场原始字段，尚不能确定每次问题的唯一根因。
- SystemUI 移除微信通知时发送定向广播；微信进程仅在通知 ID 的最新代次 token 匹配时清理显示历史，保留会话身份。回复及图片重建保留 token，旧移除事件不能清掉新通知。
- 当前移除原因仍包括点击、划除、清除全部以及 listener 删除。仅保留标准 SystemUI 原因及其他冗余清理属于 `perf-cleanup-plan.md` 中的后续计划，本次未实施。
- 提交前执行 `-PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest --offline --console=plain` 成功；Gradle 复用未变化任务的增量结果。单元测试报告共 76 项，0 失败、0 错误。
- Debug APK 经 `apksigner verify` 验证，证书与现有 release 证书一致。APK SHA-256：`934b06d1c59e0366439235c4fcfe7de79db1d589c44727d1c832b0e43c41f540`。
- 仪器测试 APK 仅完成构建；本轮没有安装或执行设备测试，通知移除广播及连续收发显示效果仍待真机验收。
