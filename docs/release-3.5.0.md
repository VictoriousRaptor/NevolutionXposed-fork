# NevolutionXposed 3.5.0

日期：2026-11-14。Android `versionCode`：18（上一版本 3.4.0 为 17）。设置 schema：12。沿用既有 release 签名证书。

## 变更

- 通知栏收到有效回复提交时立即结束捕获的旧轮，随后独立尝试原生已读与实际发送；已读或派发失败不恢复旧轮。系统当前卡片可保留，下一条新来信开始新轮；捕获之后到达的新消息不会被旧回调清除。
- 微信原生已读观察仅在 **versionName=8.0.79 且 versionCode 为 3182 或 3183** 时启用，完整签名为 `public boolean com.tencent.mm.storage.l4.b0(java.lang.String)`。BEFORE 捕获会话与代次，AFTER 仅在原调用无异常且返回 true 时提交结束；未知版本或签名/安装失败仅关闭此能力。
- 移除应用内发送状态、入库/重发及本人正文观察，移除“显示你最后回复”设置；schema 12 清理本地与远端旧键。回复、图片与撤回继续分别按自身版本画像和完整签名校验，不借用已读观察的适配结果。
- 通知栏回复处理结束后静默更新仍匹配的活动卡片，清除 RemoteInput history、spinner 与隐藏操作标记。本地派发成功时追加本人回复行，明确派发失败只复位 UI；回显不表示微信或网络送达。
- 发布检查修复回显路径对 API 28 `Builder.getStyle()` 的依赖，使用既有消息 Bundle 编码，保留 API 26 支持与最新 25 条消息上限。
- 连续回复保留当前卡片中的本人行，但不写回已退休归档。会话实例、peer ID、tag、通知代次及本地发布/取消状态共同阻止旧回调覆盖新来信或复活取消卡片。
- 同会话、同发送者的可信正值消息 ID 跨轮保留有界去重记录，旧 ID 重投不能开启新轮；不同 ID 的连续语音保持独立，不按正文或短时间窗口猜测重复。
- 首条头像最多等待 500 ms，超时正常发布后进行有限静默补取。群头像复用微信 8.0.79/3182、3183 的既有精确接口；头像与图片任务沿用会话、身份和代次校验，旧任务不能恢复已结束历史。

完整 hook、启用条件、失败行为和诊断见[微信版本适配手册](wechat-version-adaptation-playbook.md)，历史规则见[通知历史策略](notification-history-policy.md)。

## 验证范围

- 344 项主机测试通过，0 失败、0 错误、0 跳过；包含 API 26 回复回显、25 条上限、附件与消息 extras 保留，以及 API 28 的重复同文回复、失败派发和跨轮隔离回归。
- 使用 `-PrequireReleaseSigning=true` 完成 Debug、androidTest 和 Release 构建。三包签名证书均与本机既有 release 密钥一致，通过 16 KB zipalign；Release 确认为 3.5.0 / 18、minSdk 26、非 debuggable，未包含 instrumentation 入口。
- 完整 `lintDebug` 与 `lintRelease` 通过：均为 0 errors，分别有 37 和 12 项 warnings。原工作区分析会遍历大量本地诊断与缓存目录，因此改在源码及构建输入 SHA-256 一致的隔离副本完成检查，Debug 包括 androidTest 和 unitTest 分析。
- 工作区、待推送提交及其元数据、APK 和源码归档分别执行隐私检查；签名、构建、检查日志与交付产物仅保存于本地忽略目录 `.debug-artifacts/release-3.5.0-20261114/`。

此前同一功能实现于 2026-11-14 在 Android 17/API 37、微信 8.0.79/3183 上通过 109/109 项 framework 用例、0 跳过，以及 41 项已安装微信描述符检查；Debug 进程确认注入及精确已读/头像接口就绪。修复版 Release 已覆盖安装，回拉 APK 哈希一致、非 debuggable、无临时 runner，独立测试包已移除。用户实际从微信通知栏回复并展开，确认回复正文显示。原始材料保存在本地忽略目录 `.debug-artifacts/reply-echo-device-20261114/`。

上述设备记录对应升版前的修复包。本次 3.5.0 发布不重新安装或执行设备测试；framework、描述符与 hook ready 不能扩大解释为所有 OEM 布局、群聊、网络送达或多端已读同步均已验收。
