# NevolutionXposed - 微信通知栏回复增强

> 通过 Xposed 实现 Nevolution，支持微信 8.0.76 通知栏直接回复

## 📖 项目简介

NevolutionXposed 是一个 Android Xposed 模块，旨在为微信等应用提供更好的通知体验。本项目基于 [NevolutionXposed](https://github.com/notxx/NevolutionXposed) 原版代码，经过 AI 辅助更新，支持最新版微信 8.0.76 的通知栏直接回复功能。

### 灵感来源

- **[NevolutionXposed](https://github.com/notxx/NevolutionXposed)** — 原版 Nevolution Xposed 模块，提供微信通知增强功能
- **[galaxywatch-wechat](https://github.com/shentam/galaxywatch-wechat)** — Galaxy Watch 微信回复实现，启发了通知栏回复的思路

### 核心功能

- ✅ **微信通知栏直接回复** — 支持微信 8.0.76
- ✅ **MIUI 推送图标修复** — 小米推送通知图标替换
- ✅ **媒体通知美化** — Android O 样式媒体通知
- ✅ **Car Mode 绕过** — 绕过微信车载模式检查
- ✅ **RemoteInput 注入** — 注入回复文本到微信接收器

## 🚀 使用教程

### 环境要求

- Android 8.0+ (API 26+)
- 已 Root 的设备
- LSPosed / EdXposed 框架
- 微信 8.0.76 或更高版本

### 安装步骤

1. **下载 APK**
   ```bash
   # 从 GitHub Releases 下载
   # 或从源码编译
   ./gradlew assembleDebug
   ```

2. **安装模块**
   ```bash
   adb install NevolutionXposed-debug.apk
   ```

3. **配置 LSPosed**
   - 打开 LSPosed Manager
   - 启用 NevolutionXposed 模块
   - 设置作用域：
     - `com.android.systemui` — 通知拦截
     - `com.tencent.mm` — 微信通知增强
     - `com.oasisfeng.nevo` — Nevolution 引擎

4. **重启微信**
   ```bash
   adb shell am force-stop com.tencent.mm
   ```

5. **测试回复功能**
   - 让好友发送微信消息
   - 在通知栏展开通知
   - 点击「回复」按钮
   - 输入文字并发送

### 构建环境

- JDK 17+ (推荐 Microsoft OpenJDK)
- Android SDK (API 34)
- Gradle 8.14.5

```bash
# 设置环境变量
export JAVA_HOME="/path/to/jdk"
export ANDROID_HOME="/path/to/android-sdk"

# 构建
./gradlew assembleDebug

# 安装
adb install build/outputs/apk/debug/NevolutionXposed-debug.apk
```

## 🔧 技术实现

### 核心原理

1. **Hook 微信通知** — 拦截 `NotificationManager.notify()` 注入回复按钮
2. **Car Mode 绕过** — Hook `rn1.a` 类的 `f()/g()/c()` 方法
3. **RemoteInput 注入** — Hook `RemoteInput.getResultsFromIntent()` 注入回复文本
4. **自动回复** — 通过 `MMAutoMessageReplyReceiver` 发送消息

### 关键代码

```java
// Hook RemoteInput.getResultsFromIntent() 注入回复文本
XposedHelpers.findAndHookMethod(
    RemoteInput.class, "getResultsFromIntent", 
    Intent.class, new XC_MethodHook() {
    @Override
    protected void afterHookedMethod(MethodHookParam param) {
        if (param.getResult() == null && pendingReplyText != null) {
            Bundle result = new Bundle();
            result.putCharSequence("key_voice_reply_text", pendingReplyText);
            param.setResult(result);
        }
    }
});
```

### 项目结构

```
NevolutionXposed/
├── src/main/java/
│   ├── com/oasisfeng/nevo/
│   │   ├── decorators/
│   │   │   ├── wechat/          # 微信通知装饰器
│   │   │   ├── media/           # 媒体通知装饰器
│   │   │   └── MIUIDecorator.java
│   │   ├── sdk/                 # Nevolution SDK
│   │   └── xposed/              # Xposed Hook 入口
│   └── top/trumeet/common/      # 工具类
├── src/main/res/                 # 资源文件
└── build.gradle                  # 构建配置
```

## 📋 已知问题

| 问题 | 状态 | 说明 |
|------|------|------|
| 回复按钮不显示 | ✅ 已修复 | 注入 RemoteInput action |
| 消息发送失败 | ✅ 已修复 | Hook RemoteInput 结果 |
| Car mode 检查失败 | ✅ 已修复 | 绕过 rn1.a 检查 |
| Context 为 null | ✅ 已修复 | Application.onCreate hook |
| 回复按钮显示 r/t/b.xml | ✅ 已修复 | 硬编码默认值 |

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

Apache License 2.0

## 🙏 致谢

- [NevolutionXposed](https://github.com/notxx/NevolutionXposed) — 原版模块
- [galaxywatch-wechat](https://github.com/shentam/galaxywatch-wechat) — 灵感来源
- [LSPosed](https://github.com/LSPosed/LSPosed) — Xposed 框架
- [Claude AI](https://claude.ai) — AI 辅助开发
