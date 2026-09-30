# 项目默认要求

## Debug 构建与签名

- 本项目编译、交付供用户使用的 Debug APK，默认必须使用现有 release 密钥签名，以便与 release 版本相互覆盖切换。除非用户明确要求，否则不得改用 Android 默认 debug 密钥。
- 构建时传入 `-PrequireReleaseSigning=true`，确保缺少 release 签名配置时直接失败，不静默回退到 debug 签名。
- 本机 release 密钥路径：`C:\Users\Burning\Documents\ChatGPT\NevolutionXposed\.debug-artifacts\signing\nevolutionxposed-release.jks`。
- 使用现有本地签名配置，通过 Gradle 属性或进程环境变量提供 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。不得将密码、私钥或密钥文件内容写入项目文档、代码、日志或提交到 Git。
- 交付前验证 APK 签名证书与现有 release 证书一致，并说明验证结果。签名配置不可用时明确报告阻塞，不用不同签名的 APK 替代交付。
- 默认构建命令：`.\gradlew.bat -PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest --console=plain`。离线依赖齐全时可追加 `--offline`。
- 构建和签名不代表授权安装、推送或发布；这些操作按用户当次指令执行。

## Hook、版本限定与适配文档同步

- 新增、修改或移除 hook 目标、完整签名、回调时机、作用进程、功能开关，或调整版本映射、门禁和兼容回退时，必须在同一次变更中同步更新 `docs/wechat-version-adaptation-playbook.md`。该要求也适用于 Android/SystemUI hook 及其与微信通知链路的交互。
- 指南应记录受影响版本、实际匹配语义（版本名与版本号是同时匹配还是任一匹配）、运行时签名校验、启用条件、失败时行为、诊断日志和必要的回归场景。回复、图片、应用内回复和撤回等功能的门禁须分别说明，不得推断一个功能适配即代表其他功能适配。
- 涉及微信混淆布局时，同时更新对应 `docs/wechat-<版本>-findings.md`；已有专题文档也应保持一致。记录 APK 来源与 SHA-256、反编译位置、运行时原始类/字段/方法名及完整类型，不能把反编译器生成的别名当作 hook 名称。
- 明确区分离线源码核对、自动化测试/构建、运行时 hook 安装和真实设备行为验证。构建通过或出现 ready 日志不能写成真机回归通过；未验证项和保守回退必须保留。
- 本原则及项目代码事实保存在本项目的规则和文档中，后续修改前查阅并继续维护。
