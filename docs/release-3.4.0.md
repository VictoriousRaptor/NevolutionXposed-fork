# NevolutionXposed 3.4.0

日期：2026-10-24，2026-10-25 更新。Android `versionCode`：17（上一版本 3.3.1 为 16）。设置 schema：8。

## 变更

- 私聊通知未展开时使用对方名字作为会话标题：`MessagingStyle` 保留现有会话名，同时继续标记为非群聊。群聊标题规则不变；跳过装饰的媒体或非聊天通知继续沿用原处理路径。实际 Android/OEM 折叠布局待真机确认。
- 通知栏回复 B 后保留本轮来信 A 和回复 B；下一条符合条件的原始来信 C 到达后，默认显示 C，开启“显示你最后回复的消息”时显示 B+C。发送前仍请求原生已读，失败不记录 B；用户明确移除仍清理对应通知代次，迟到回调不复活已移除通知。详见[通知历史规则](notification-history-policy.md)。
- SystemUI 移除回调兼容四参数与三参数签名，安装失败可重试；原生已读动作绑定当前会话实例和通知代次，不继承旧通知动作。会话身份缓存最多保留 256 个最近使用的 ID，活跃归档中的会话保留。
- WXGF 输入限定读取长度并检查 EOF，拒绝空、截断或多余数据；图片路径探针仅在 Debug 且显式开启诊断时运行。移除无当前生产者的旧 Zoom 动作及遗留分支，保留现代图片预览、动作身份和撤回清理。完整兼容边界见[适配手册](wechat-version-adaptation-playbook.md)。
- 完整移除独立媒体通知增强：实现、同步取色、专用布局、设置入口、全局反射注解 hook、Palette 依赖及功能测试；本地和远端设置迁移时清除旧媒体键。
- 保留微信图片、视频、文件、表情消息的原生布局与回复动作，以及 SystemUI 的通知移除广播、上下文捕获、NLS 诊断和 Markdown 链路。
- CarExtender 缺少会话时保留原删除动作；会话存在但 participants/messages 不完整时，仍允许使用已有原生 `on_read`，不扩大划除已读的条件。
- 旧渠道迁移原样保留静音、灯光及振动开关；API 35+ 的非空振动 effect 沿用原值，其余复制 pattern 后恢复显式振动开关。
- WXGF 质量等待期间复用未变化候选的转换结果。整个加载器共用单槽，最多保留 20 MiB；匹配请求身份、路径、大小和修改时间，候选仍须出现在当前路径列表。取消、替换、失败或生成最终 Bitmap 后关闭缓存写入，阻止迟到结果复活。

微信回复、图片、应用内回复与撤回的版本映射、完整签名校验和未知版本回退不变。详细 hook 及启用条件见[适配手册](wechat-version-adaptation-playbook.md)。

## 待办与兼容边界

- 自己的头像来源仅登记为[未确定待办](project-modification-plan.md)，没有修改头像选择、读取或缓存代码。
- 按用户要求保留已有本地下载器 ID 实现：SQL 不读 `msglocalid`，消息对和下载首参均使用基础行 `id`。主机测试已同步；本轮没有重新判断各微信版本的 ID 语义或真实下载行为。
- Android/OEM 实际通知表现、微信已读/回复/下载及新旧版本覆盖安装仍需独立真机验收。构建、签名和缓存契约测试不代表这些行为通过。

## 验证记录

### 2026-10-25 Release 构建

- 使用 `-PrequireReleaseSigning=true` 离线执行 `testDebugUnitTest assembleRelease`，构建成功。183 项主机测试结果无失败、错误或跳过；同一功能源码此前已执行，本次测试任务由 Gradle 复用。
- 实际 Release APK 包名为 `com.oasisfeng.nevo.xposed`，版本为 3.4.0 / 17，`debuggable=false`。签名证书与现有 release 密钥一致，签名和 zipalign 校验通过。
- Release APK 的 DEX 和资源中未检出独立媒体实现、Palette 类及专用布局。构建附带的 `lintVitalRelease` 通过，本次没有执行完整 Lint。
- APK 为 3,491,115 字节，SHA-256 为 `3c2e06d67ab572c875efe64130ed3ed1838484b8fb42e804668371a29130dac4`；产物及验证记录保存在本地忽略目录 `.debug-artifacts/release-3.4.0-20261025/`。
- 私聊标题调整未新增 hook、版本画像或功能开关。本次没有安装 APK、运行设备仪器测试或验证微信/OEM 实际通知布局。

### 2026-10-24 历史构建

当日 3.4.0 / code 17 已使用 `-PrequireReleaseSigning=true` 离线重新构建：

- 167 项主机单元测试全部通过，失败、错误、跳过均为 0。
- Debug 和 androidTest APK 构建通过；实际 Debug 包内版本确认为 3.4.0 / 17。
- 两包签名证书均与现有 release 密钥一致，签名和 zipalign 校验通过。
- 实际 Debug APK 的 DEX 和资源中未检出独立媒体实现、Palette 类及专用布局。
- 本次仅修改版本与文档，没有重跑 Lint；升级前同一功能源码的应用源码 Lint 为 0 errors / 31 warnings，两个测试源码 Lint 分析任务未运行。

本地 Debug APK 为 4,686,626 字节，SHA-256 为 `e8b795ea7bfe54af472ebca9f3cb06589073698e1d4e9a50bdfe86790bd8c9bd`。构建、签名和产物检查记录保存在忽略目录 `.debug-artifacts/version-3.4.0-20261024/`，旧版证据继续保留。

没有安装 APK、运行设备仪器测试、重启微信/SystemUI 或发布 GitHub Release。此前设备记录不作为本版本的新验收证据。
