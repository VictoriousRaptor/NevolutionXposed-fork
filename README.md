# NevolutionXposed - 微信通知增强

基于现代 libxposed API 102 的 Android Xposed 模块，为微信和 System UI 提供通知栏直接回复、MIUI 推送图标修复与媒体通知增强。

## 功能

- 微信通知栏直接回复（内置微信 8.0.72 / 8.0.76 适配描述）
- 微信 Car Mode 检查绕过与 RemoteInput 回复结果转发
- MIUI 推送通知图标修复
- 媒体通知样式增强
- 通话通知识别与排除
- 可在模块应用内分别启用或停用上述装饰器

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
3. 从桌面启动 NevolutionXposed，可配置微信、MIUI 与媒体通知功能。设置会通过 libxposed service 同步到被注入进程。
4. 修改设置或模块状态后，重启对应目标进程使配置生效。

## 构建

构建环境：

- JDK 17
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0
- Android Gradle Plugin 9.1.1
- Gradle 9.3.1（Wrapper 自动获取）

PowerShell：

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\<you>\.gradle'
.\gradlew.bat clean assembleDebug testDebugUnitTest
```

调试 APK 输出到：

```text
build/outputs/apk/debug/NevolutionXposed-debug.apk
```

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
├── decorators/            # 微信、MIUI、媒体通知逻辑
├── sdk/                   # 通知装饰器抽象
└── xposed/
    ├── MainHook.java      # API 102 模块入口
    ├── ModuleApplication.java
    ├── RemotePreferenceStore.java
    └── compat/            # 旧回调语义到新拦截链的内部适配
```

## 版本记录

### v3.0.1

- 增加桌面、LSPosed 模块菜单和标准系统首选项三种设置入口。
- 微信通知增强默认开启；MIUI 与媒体通知增强默认关闭。
- 删除未被代码使用的微信 MIUI 修复开关。

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
