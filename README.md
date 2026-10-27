# NevolutionXposed - 微信通知增强

基于现代 libxposed API 102 的 Android Xposed 模块，为微信提供通知栏直接回复与通知展示增强。

## 功能

- 微信通知栏直接回复（内置微信 8.0.72 / 8.0.76 / 8.0.77 / 8.0.78 / 8.0.79 版本适配）
- 微信 8.0.79/3182 新增回复、图片/普通大图、应用内回复轮次和撤回的独立精确画像；模块 3.2.1 同步升级 AndroidX 依赖。源码、设备检查与真实消息验证边界见 [8.0.79 记录](docs/wechat-8.0.79-findings.md)。当前模块版本为 **3.4.0 / versionCode 17**。
- 微信 8.0.77/3141、3160 图片/大图、应用内回复轮次与撤回画像已补齐；3141 已安装并通过签名及 hook 检查，真实消息行为待验收，见 [适配记录](docs/wechat-8.0.77-findings.md)。
- 微信 Car Mode 检查绕过与 RemoteInput 回复结果转发
- 通话通知识别与排除
- 可在模块应用内配置微信通知增强及各项可选功能

## 运行要求

- Android 8.0（API 26）或更高版本
- 支持现代 libxposed API 102 的 LSPosed
- 已 Root 的设备

本版本仅提供现代 API 102 入口，不再兼容 legacy Xposed API 或 EdXposed。

## 安装与配置

1. 安装 APK，并在 LSPosed 中启用 NevolutionXposed。
2. 模块使用固定作用域：
   - `com.android.systemui`
   - `com.tencent.mm`
3. 从桌面启动 NevolutionXposed，可配置微信通知功能。设置会通过 libxposed service 同步到被注入进程。
4. 修改设置或模块状态后，重启对应目标进程使配置生效。

## 构建

构建环境：

- JDK 17
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0
- Android Gradle Plugin 9.1.1
- Gradle 9.3.1（Wrapper 自动获取）

PowerShell：

先通过 Gradle 属性或 `ORG_GRADLE_PROJECT_RELEASE_*` 进程环境变量提供既有 release 签名配置；不要把密码或密钥写入仓库。以下命令缺少配置会直接失败，Debug 与 release 使用同一证书，便于覆盖安装。构建不会安装到设备。

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle'
.\gradlew.bat -PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest --console=plain
```

调试 APK 输出到：

```text
build/outputs/apk/debug/NevolutionXposed-debug.apk
```

长期图片文件探针默认关闭。需要诊断时，可为 Debug 构建追加 `-PimageDiagnostics=true`，显式开启最多 120 秒的文件状态观察；Release 始终关闭。该选项不改变图片预览、下载或发布的版本门禁和超时策略。

## API 102 架构

- Hook 入口：`com.oasisfeng.nevo.xposed.MainHook`
- 模块元数据：`src/main/resources/META-INF/xposed/`
- 固定作用域：`META-INF/xposed/scope.list`
- 模块配置同步：`XposedService` + `RemotePreferences`
- Hook 方式：`XposedModule.hook(...).intercept(...)`

项目保留了一层内部兼容适配器，把原有 before/after 回调转换成 API 102 的拦截链，因此原有微信通知处理逻辑无需整体重写。例如：

```java
hook(method)
        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
        .intercept(chain -> {
            Object[] args = chain.getArgs().toArray();
            // before callback
            Object result = chain.proceed(args);
            // after callback
            return result;
        });
```

## 项目结构

```text
src/main/java/com/oasisfeng/nevo/
├── decorators/            # 微信通知逻辑
├── sdk/                   # 通知装饰器抽象
└── xposed/
    ├── MainHook.java      # API 102 模块入口
    ├── ModuleApplication.java
    ├── RemotePreferenceStore.java
    └── compat/            # 旧回调语义到新拦截链的内部适配
```

## 文档

- [3.4.0 版本说明](docs/release-3.4.0.md)：媒体功能移除、兼容修复、图片缓存及验证边界。
- [项目全局修改计划](docs/project-modification-plan.md)：当前后续工作的顺序、范围与验收条件。
- [微信版本适配手册](docs/wechat-version-adaptation-playbook.md)：微信新版本的一次性适配 Runbook、版本敏感点总表与验收矩阵。
- [微信通知回复调试说明](docs/wechat-reply-debug.md)：回复链路的诊断阶段、日志规范与隐私红线。
- [CI 与发布配置](docs/ci-release.md)：签名密钥与 Release 流程。

## 版本记录

独立媒体通知样式增强已于 2026-10-24 移除。下列历史版本中的媒体样式说明仅表示当时的实现，微信媒体消息的内联回复继续保留。

记录按同一版本号的维护阶段归纳；一个版本可能对应多个 `build` 标签，功能归属以实际构建版本为准。

### v3.4.0（2026-10-27 更新）

- 修复图片展开布局依赖微信进程查询模块包的问题；保留 fitStart 和回复动作，构造不可用时保留文字通知。
- 私聊和群聊采用微信原头像与系统会话角标，群聊图片沿用文本排版。取消应用名修改及其设置，schema 11 清理旧键，应用名和内容标题使用系统模板。
- 图片文件等待时间可配置为 100–30000 毫秒，默认 1500；恢复图片行 `id` 与消息 `msglocalid` 的独立分工。
- Markdown 代码样式恢复兼容 Android 17 的完整清理签名，签名不支持时保留系统回退。
- 私聊通知未展开时使用对方名字作为会话标题；保留私聊标记，群聊标题规则不变。
- 通知栏回复后保留本轮来信和回复，下一条新来信到达时再按“显示你最后回复的消息”开关裁剪历史；失败发送不记录成功回复。
- 修复通知生命周期兼容：SystemUI 移除回调兼容两种签名，原生已读动作绑定当前通知代次；限制会话缓存，收紧 WXGF 输入长度检查，默认关闭图片路径探针。
- 完整移除独立媒体通知增强及其设置、取色、布局、全局反射 hook 和 Palette 依赖；schema 8 清除旧媒体开关。微信媒体消息的回复功能保留。
- 修复只有车载扩展、没有会话时的空指针；不完整会话中的原生 `on_read` 仍可作为删除动作。
- 旧通知渠道迁移保留静音、灯光及振动设置，包括适用版本的振动 effect。
- WXGF 质量等待期间复用未变化文件的转换结果；加载器共用单槽、最多保留 20 MiB，取消或完成后释放。
- 保留 SystemUI 通知移除广播与诊断入口；自己的头像来源仅记录为“未确定”待办，不改变头像代码。
- 版本号升级为 3.4.0 / versionCode 17。详细范围与自动化、真机验证边界见[版本说明](docs/release-3.4.0.md)。

### v3.3.1

- 通知栏有效发送操作在派发前请求原生已读并清理对应历史；派发失败不写入成功回复。
- 成功回复记录与活动通知查询解耦，保留活动通知消失后的成功回复及去重信息。
- 新增微信 8.0.79/3182 的 Unicode emoji 保留开关，默认关闭；版本门禁与设备验证边界见[表情专题](docs/wechat-emoji-notifications.md)。

### v3.3.0

- 图片候选及普通大图下载的总等待窗口缩短为 1.5 秒，到期继续使用已有候选降级。
- 展开图片预览使用顶部左侧对齐的 `fitStart`，保持完整图片比例及回复动作。
- 点击、划除和清空按确认代次清理通知历史；新增默认关闭的“显示你最后回复的消息”。
- 修复通知栏回复后新来信被旧输入历史静音的问题，保留原有静默更新行为。
- 简化通知重建和回复门禁的重复工作，保留 SystemUI 同进程诊断访问。

### v3.2.1

- 为微信 8.0.79/3182 增加回复、图片/普通大图、应用内回复与撤回的独立精确画像。
- 更新 AndroidX Core、Annotation 和 AppCompat 依赖；源码、描述符检查及真实行为验证分别记录。

### v3.2.0

- 将版本升级为 3.2.0 / versionCode 13，沿用 3.1.4 维护阶段已加入的 Markdown、代码样式恢复及微信媒体回复去重。

### v3.1.4

- 增加微信 8.0.77 的回复、图片、应用内回复与撤回适配，并完善图片预览设置说明。
- 实现应用内回复轮次及按消息身份裁剪撤回历史；真实微信行为按各版本独立验收。
- 维护构建加入默认关闭的通知 Markdown 展示，支持有限的强调、删除线及行内/围栏代码，历史身份继续使用原文。
- 在微信和 SystemUI 的 framework 消息清理后恢复模块代码样式，保留签名校验、所有者标记和失败回退。
- 修复微信媒体通知更新及回复后重复累积回复动作的问题；相关修复已包含在 `v3.1.4-build.30/31` 标签中。

### v3.1.3

- 修复通知附加字段缓存的并发访问：每个分段使用独立的弱引用表和锁。
- 修复设置升级时媒体通知增强开关被重置的问题，保留已有用户选择。
- 增加附加字段并发访问与 schema 3 升级的回归测试。

### v3.1.2

- 微信通知回复框统一显示本地化的“回复”或“Reply”，不再以联系人姓名作为输入框 placeholder。
- 修复通知发送者识别与对话轮次重建，稳定私聊、群聊、连续回复及通知移除后的会话身份。
- 修复通知缓存计数导致回复按钮消失的问题，并改善媒体通知与操作按钮的可访问性和文案本地化。

### v3.1.1

- 修复微信 8.0.78（中国版 3180）通知栏回复：新增 `wechat-8.0.78` 版本画像（门禁 `bs1.a[c,g,b]`），真机验证回复可达。
- 新增 [微信版本适配手册](docs/wechat-version-adaptation-playbook.md)：历史提交适配记录、版本敏感点总表、一次性适配 Runbook 与验收矩阵。
- `tools/collect-device-info.ps1` 支持 `-ProbeProfile` / `-RunTests`：一条命令产出候选画像、可粘贴代码片段与测试结果。
- 新增 `WeChatReplyProfileTest`，锁定版本映射与 8.0.78 / 8.0.72 描述符。

### v3.1.0

- 微信通知：移除每次进程启动的方法枚举扫描与逐方法追踪，热路径日志改为仅调试构建输出，且不再写消息正文、联系人与账号信息。
- 通知栏回复加固：回复画像只在验证可用后缓存、失败可重试；合成回复文本单次有效并有超时，且只对模块自己派发的 Intent 生效。
- 媒体/表情/文件/链接类通知保留微信原始布局的同时补上「回复」输入框。
- 通知重建按需触发：由 `recoverBuilder` 产出的通知会被复用，实测同一条图片消息从每次通知都重建降到 5 次通知 2 次重建。
- 设置界面迁移到 AndroidX Preference（`PreferenceFragmentCompat` + `AppCompatActivity`），移除已废弃的 `android.preference`。
- 移除 MIUI 推送图标修复功能（含其图标处理与缓存代码），偏好迁移到 schema 3 并清理历史键。
- 修复语音通话识别使用宿主 Resources 读取模块资源而导致的装饰中断；通知缓存改为按条数计费（上限 120 条）。

### v3.0.1

- 增加桌面、LSPosed 模块菜单和标准系统首选项三种设置入口。
- 微信通知增强默认开启；当时的媒体通知增强默认关闭。

### v3.0.0

- 迁移到现代 libxposed API 102，移除 legacy Xposed 入口与依赖。
- 增加 `META-INF/xposed` 模块元数据和静态作用域。
- 恢复模块设置入口，并通过 RemotePreferences 向目标进程同步配置。
- 升级到 compileSdk 37、AGP 9.1.1、Gradle 9.3.1 和内置 Kotlin。

### v2.0.3

- 修复 Google Play 微信 8.0.72 通知栏回复，保留 8.0.76 版本映射。
- 改进通知内容显示和回复诊断。
- 添加分支 / PR 构建检查及 master 自动签名发布流程。

## 自动构建与发布

分支和 PR 自动构建；master 更新后自动构建签名 APK 并发布 GitHub Release。发布前需配置签名密钥，详见 [CI 与发布配置](docs/ci-release.md)。

## 许可证

Apache License 2.0

## 致谢

- [NevolutionXposed](https://github.com/notxx/NevolutionXposed)
- [galaxywatch-wechat](https://github.com/shentam/galaxywatch-wechat)
- [LSPosed](https://github.com/LSPosed/LSPosed)
