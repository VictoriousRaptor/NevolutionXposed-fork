# NevolutionXposed 3.4.0

日期：2026-10-24。Android `versionCode`：17（上一版本 3.3.1 为 16）。设置 schema：8。

## 变更

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

3.4.0 / code 17 已使用 `-PrequireReleaseSigning=true` 离线重新构建：

- 167 项主机单元测试全部通过，失败、错误、跳过均为 0。
- Debug 和 androidTest APK 构建通过；实际 Debug 包内版本确认为 3.4.0 / 17。
- 两包签名证书均与现有 release 密钥一致，签名和 zipalign 校验通过。
- 实际 Debug APK 的 DEX 和资源中未检出独立媒体实现、Palette 类及专用布局。
- 本次仅修改版本与文档，没有重跑 Lint；升级前同一功能源码的应用源码 Lint 为 0 errors / 31 warnings，两个测试源码 Lint 分析任务未运行。

本地 Debug APK 为 4,686,626 字节，SHA-256 为 `e8b795ea7bfe54af472ebca9f3cb06589073698e1d4e9a50bdfe86790bd8c9bd`。构建、签名和产物检查记录保存在忽略目录 `.debug-artifacts/version-3.4.0-20261024/`，旧版证据继续保留。

没有安装 APK、运行设备仪器测试、重启微信/SystemUI 或发布 GitHub Release。此前设备记录不作为本版本的新验收证据。
