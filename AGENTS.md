# 项目默认要求

## Debug 构建与签名

- 本项目编译、交付供用户使用的 Debug APK，默认必须使用现有 release 密钥签名，以便与 release 版本相互覆盖切换。除非用户明确要求，否则不得改用 Android 默认 debug 密钥。
- 构建时传入 `-PrequireReleaseSigning=true`，确保缺少 release 签名配置时直接失败，不静默回退到 debug 签名。
- 本机 release 密钥路径：`C:\Users\Burning\Documents\ChatGPT\NevolutionXposed\.debug-artifacts\signing\nevolutionxposed-release.jks`。
- 使用现有本地签名配置，通过 Gradle 属性或进程环境变量提供 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。不得将密码、私钥或密钥文件内容写入项目文档、代码、日志或提交到 Git。
- 交付前验证 APK 签名证书与现有 release 证书一致，并说明验证结果。签名配置不可用时明确报告阻塞，不用不同签名的 APK 替代交付。
- 默认构建命令：`.\gradlew.bat -PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest --console=plain`。离线依赖齐全时可追加 `--offline`。
- 构建和签名不代表授权安装、推送或发布；这些操作按用户当次指令执行。
