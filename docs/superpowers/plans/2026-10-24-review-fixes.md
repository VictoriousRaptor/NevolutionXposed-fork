# Project Review Fixes Implementation Plan

> **For agentic workers:** Execute task-by-task with `superpowers:executing-plans`; use one independent read-only review after implementation.

**Goal:** 修复 2026-10-24 全项目审查确认的问题，清理失去消费者的代码，保持现有通知身份与失败回退。

**Architecture:** 沿用单模块、独立版本画像和消息轮次模型。原生已读动作跟随当前回复目标；SystemUI 方法选择与安装去重集中在小型辅助类；会话元数据采用有界 LRU。

**Tech Stack:** Java 17、Android minSdk 26、libxposed API 102、JUnit 4、PowerShell 7。

**Spec:** 用户已要求逐项修复前一轮全项目审查；问题依据和边界见 `docs/perf-cleanup-plan.md` 及本地审查记录。

## Global Constraints

- 保留既有 `.gitignore` 修改、独立功能门禁、原生回复、通知代次、精确撤回和 SystemUI 诊断入口。
- 使用当前功能分支原地实施；初始阶段不提交。2026-10-25 已授权验证后本地提交及编译；不推送、不安装或操作设备。
- 保留当前下载器 ID 取值；不重新裁决既有版本的运行时 ID 语义。
- 不安装依赖；离线测试和构建使用现有 release 配置及 `-PrequireReleaseSigning=true`。
- Hook/启用条件/兼容回退变化同步适配指南；主机、构建与设备证据分别记录。

## Review Focus

- 同通知 ID 的当前已读动作缺失时不能继承旧动作；连续回复和迟到回调继续遵守代次边界。
- 同名不同版本码及错误名称/版本码组合不能借用生成的精确画像。
- WXGF 短文件、增长文件及异常读取不能在输入阶段突破预期大小。
- SystemUI 四参数缺失但类型存在时应尝试三参数；安装失败可重试，成功安装不重复。
- 会话缓存淘汰不得清理其他会话的归档或重新接纳旧实例回调。

## Task 1: 当前已读目标

- [x] 增加框架回归：同 ID 从有已读动作更新为无动作，保留已有的更换会话及迟到回复回归。
- [x] 将原生已读动作加入 `MessagingBuilder.ReplyTarget`，从当前归档通知取得；移除独立已读动作 map。
- [x] 调整框架 fixture 与控制器，保留连续回复、失败派发仍请求已读的语义。
- [x] 更新通知历史策略；运行单元测试并编译 framework 回归。设备执行保持未验证。

## Task 2: 精确画像生成

- [x] 提取生成片段的纯 PowerShell 函数，使用合成画像在本地执行生成代码验证匹配结果。
- [x] 观察错误版本组合被旧 OR 条件接受；改为 AND，常量和 label 包含版本码。
- [x] 修正碰撞检查并同步 Runbook；不更改现有历史画像门禁。

## Task 3: WXGF 有界读取

- [x] 增加正常、过短、过长文件的真实读取单元测试，先确认旧方法接受大小不匹配。空输入由 `decodeWxgf()` 入口拒绝；后续精简已删除要求私有读取方法重复校验零长度的测试。
- [x] 一次分配并精确读取 expectedSize，检查 EOF/多余字节；保留前后文件元数据验证。
- [x] 运行相关单元测试和完整主机测试。

## Task 4: SystemUI 回退与安装去重

- [x] 用本地测试类覆盖四参优先、三参回退、错误返回类型、无可用签名。
- [x] 提取完整签名方法选择和成功安装去重；验证重复安装、失败重试及异常传播。
- [x] 接入 MainHook，保留每次 NLS 实例捕获；同步适配指南。

## Task 5: 生命周期边界

- [x] 会话表改为最多 256 项 LRU；保留正常移除后的身份，验证淘汰及旧回调拒绝。
- [x] Debug 长期图片探针改为默认关闭的构建诊断选项；取消/替换/拒绝时停止旧观察。
- [x] 保留现有图片缓存、超时、取消和代次校验；更新文档。

## Task 6: 成组遗留清理

- [x] 删除无生产者的 Zoom 链路、旧撤回正则和无消费者 extras。
- [x] 展开低于 minSdk 的分支，删除核对后确无消费者的资源及辅助成员。
- [x] 修正 README 签名命令并删除自动安装的旧 build.sh；保留 CI 的 PR 构建用途。
- [x] 运行完整测试、签名 Debug/androidTest 构建和 diff 检查；2026-10-25 已在源码哈希一致的干净副本中完成包含两类测试源码分析的完整 Lint，结果与执行边界见 `docs/perf-cleanup-plan.md`。

## Task 7: 复核与交付

- [x] 独立只读复核最终修改，修复有依据的正确性问题。
- [x] 核对签名证书、变更范围和隐私；保留本地日志及回滚依据。
- [x] 汇总已修复项、未执行的设备/性能验证和构建产物。
