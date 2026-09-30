# NevolutionXposed 性能与冗余清理：结论与遗留项

- 状态（2026-09-14）：清理工作已完成，并通过 PR #1（merge commit `70dca47`）进入 master；master 随后推进到 `0077b3d`（WeChat 8.0.78 回复修复 + `3.1.1` 发布）。
- 本文档用途：归档“改了什么、验证到什么程度、还剩什么”，供后续维护与发版查阅。逐轮执行日志（进程号、每轮调试 APK 哈希等）不在此保留。

## 一、结论：改动一览

### P0 · 调试开销与回复链路加固（commit `ebe85bc`）

- 删除微信进程启动时的类/方法枚举扫描与回复接收器逐方法追踪；日志统一受 `BuildConfig.DEBUG` 门控，并移除消息正文、联系人名、wxid、头像路径。
- 量化：未门控的 `Log.d/i/v` 由 84 处降到 2 处；`XposedBridge.log` 调用点由 45 处降到 15 处（余下均为一次性安装失败告警与 `NX_REPLY` 诊断）。
- MediaDecorator 仅在开关开启且 API ≥ 28 时安装 hook，不再向 SystemUI 注入全局反射拦截（commit `eec5b34`）。
- 回复画像只在签名校验通过后缓存、失败按 5 秒间隔重试；合成回复文本单次有效、30 秒过期，且只对模块自己派发的 `MM_AUTO_REPLY_MESSAGE` Intent 生效。
- 修复未读计数前缀解析的 `charAt` 越界与 ticker 为 null 的 NPE。

### P1 · 每条通知的固定开销（commit `eec5b34`）

- 微信 targetSdk 查询改为进程内一次；归档通知列表每次 `apply()` 只取一次并返回只读视图。
- 缓存 `Notification.actions` 反射字段；附加字段存储改为 16 路分槽锁；通知缓存改为按通知条数计费（总量上限 120 条）。
- 删除未调用的 helper、未使用资源与全部残留 import。

### P2 · 可维护性

- 合并 `MessagingBuilder` 三处重复的消息重建逻辑，净减 74 行（commit `b915991`）。
- 重建标记 + 缓存替换：`recoverBuilder` 产出的通知标记为 actions 已序列化，并替换缓存实例（commit `2dd60d5`）。
- 移除 MIUI 推送图标修复功能（含图标处理与缓存代码），偏好 schema 升到 3 并清理历史键（commit `a46426f`）。
- 设置界面迁移到 AndroidX Preference，保留 device-protected 存储（commit `1a1c174`）。
- 修复 `VoiceCall` 使用宿主 Resources 读取模块资源而导致的装饰中断（commit `ebedb24`）。
- 媒体/表情/文件/链接类通知保留微信原始布局的同时补上回复输入框（commit `ba97b34`）。
- 版本：`versionCode 7 → 8`、`versionName 3.0.1 → 3.1.0`（commit `a62a67d`）。

## 二、验证证据

- 构建与测试：`assembleDebug testDebugUnitTest` 全绿（离线构建同样通过）。
- CI：特性分支 `build` job 全绿；`release` job 只在 master 推送/手动触发时执行。
- 真机（微信 8.0.72 / versionCode 3085，LSPosed 2.2.0）：
  - 图片通知显示大图并带回复框，链路 `NX_IMAGE event → queued → resolved → decoded → published → reattached`；
  - 通知内回复走原生路径，`NX_REPLY` 阶段齐全、`resultCode=0`，消息**真实送达**（用户确认）；
  - `[文件]` 通知修复前 `actions=null`（无回复框），修复后出现 `action_media_reply_attached`，用户确认回复框可见；
  - 设置界面为 AndroidX 版（`PreferenceFragmentCompat` + `AppCompatActivity`），MIUI 开关已消失；
  - 重建次数：同一条图片消息 5 次 notify → 2 次重建（改动前每次 notify 都会重建）。

## 三、遗留项

1. **待验证（属验证，不属代码）**：合成回复路径（仅当微信 CarExtender 缺 RemoteInput 时触发）与“两个不同会话连续回复不串号”场景；原生路径已验证并确认送达。
2. **框架侧观察：微信主进程偶发未被注入（LSPosed 2.2.0）**
   - 现象：安装/更新模块 APK 后启动微信，主进程常常没有 `onModuleLoaded` 标记，而同一轮稍后 fork 的子进程（`:appbrand0`、`:appbrand1`、`:support`）有标记；重复重启若干次后恢复正常。
   - 代码侧没有、也无法修改框架的注入行为。唯一相关改动是 `1325067`：在 `onModuleLoaded` 增加一行 DEBUG 标记，用于区分“框架未注入”与“已注入但暂无事件”。
   - 现有证据不支持“微信没有真正重启”：失败轮次里主进程 pid 每次都变化（确为新进程），且同一轮子进程能被正常注入。
   - 缓解办法（均为框架侧操作，无需改代码）：更新模块 APK 后先等十几秒再启动微信，或直接重启手机；在 LSPosed 管理器中关闭再启用该模块以刷新状态；`am force-stop` 后等 2–3 秒再启动，若仍无标记则重试一次。

## 四、后续清理计划：仅支持标准 SystemUI（2026-09-21）

2026-09-30：本节列出的代码清理已实现。共同验证结果及仍待执行的真机验收见[项目全局修改计划](project-modification-plan.md)。以下保留原范围与验收依据；`input_history`、重复 ClipData 转发和 listener 移除原因已不再存在于当前实现。

当前使用显式广播在通知被点击、单条划除或“清除全部”后重置微信通知轮次。用户不使用 HyperIsland 或其他第三方通知面板；后续清理以标准 SystemUI 为唯一交互入口，目标是删除无消费者的数据和重复的 RemoteInput 转发，同时不削弱微信原生通知回复。

### 可直接清理

1. **收紧通知移除原因**
   - `WeChatNotificationRemoval.shouldResetRound()` 只保留 `REASON_CLICK`、`REASON_CANCEL`、`REASON_CANCEL_ALL`。
   - 删除 `REASON_LISTENER_CANCEL`、`REASON_LISTENER_CANCEL_ALL` 及对应单元测试；这些原因用于其他 NotificationListener 发起的删除，不属于标准 SystemUI 交互。
2. **删除回复代理中的无消费者状态**
   - 移除 `proxyDirectReply()` 未使用的 `Notification notification` 参数及全部调用实参。
   - 移除 `input_history` 参数和写入代理 Intent 的 `EXTRA_REMOTE_INPUT_HISTORY`；当前回复接收器不再读取该值，回复历史由 `NotificationMessages.recordReply()` 从成功回调统一维护。
   - 删除接收器中恒为真的二次 `results != null` 判断；入口已在结果为空时返回。
3. **合并 RemoteInput 结果转发**
   - 构造发往微信原始回复 `PendingIntent` 的 Intent 时，只保留一次 `RemoteInput.addResultsToIntent()`，删除与其重复的原始 `ClipData` 整体复制。
   - 保留 mention 前缀写回结果 Bundle 的步骤，再由同一条转发路径编码到目标 Intent。
   - 将 HyperIsland 专属注释改写为标准 RemoteInput 转发语义。
4. **删除已确认无引用的成员**
   - 删除 `WeChatDecorator` 中未使用的 `EXTRA_PICTURE`、`STORAGE_PREFIX` 和 `now()`。

### 保留及补齐

- 保留 `proxyDirectReply()`、`attachReplyAction()`、CarExtender fallback、合成回复和 actions 序列化；这些均服务于标准 SystemUI 的通知内回复，并非 HyperIsland 专属实现。
- 保留轮次重置广播的通知代次 token、导出动态接收器、定向包名、最新 token 校验及图片样式重建时的 token 复制；它们分别用于跨进程投递、防止旧移除事件误清新轮次和保持图片通知可重置。
- `MessagingBuilder.close()` 当前没有调用方，不直接删除；在 `WeChatDecorator.Local.onDestroy()` 中调用它，使已有回复/缩放接收器与轮次重置接收器统一注销。

### 验收条件

- `testDebugUnitTest`、`assembleDebugAndroidTest`、`assembleDebug` 全部通过，`git diff --check` 无错误。
- 单元测试确认只有点击、SystemUI 单条划除和清除全部会重置；listener/app cancel、超时、休眠和渠道变化不会重置。
- instrumentation 覆盖原生回复结果、mention 前缀、连续回复历史、图片样式 token 保留和移除后新轮次。
- 真机确认标准通知栏的点击、划除、清除全部、文字回复及媒体/文件通知回复正常；不再把 HyperIsland 行为列入验收范围。
