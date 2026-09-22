# Lint 修复处理计划

- 日期：2026-09-22
- 基线提交：`6dfbb0a fix: 修复通知缓存计数导致回复按钮消失`
- Lint 报告：`build/reports/lint-results-debug.xml`
- 报告时间：2026-09-22 21:01:01
- 当前结果：2 个 error，55 个 warning

本计划只处理此前判定为“必须处理”和“建议处理”的项目。`ObsoleteSdkInt`、
`UnusedAttribute`、旧 `UnusedResources` 等纯清理项暂不混入本阶段。

## 目标

1. 消除阻止 `lintDebug` 成功的两个 error。
2. 修复会影响多用户、服务生命周期、本地化和无障碍体验的真实风险。
3. 对 Xposed 宿主环境造成的误报采用局部、可解释的抑制，不修改模块 Manifest
   伪造宿主权限。
4. 每一项都保留可独立验证、可独立回退的边界。

## 分类

| 优先级 | Lint 项 | 数量 | 处理结论 |
|---|---:|---:|---|
| P0 | `RestrictedApi` | 1 | 使用 AndroidX 公共 API 重建 `Person` |
| P0 | `MissingPermission` | 1 | 确认宿主机权限来源后做局部抑制 |
| P1 | `SdCardPath` | 1 | 改为宿主包上下文的 `getDataDir()` |
| P1 | `StaticFieldLeak` | 2 | Application Context 定向抑制；NLS 改为弱引用 |
| P1 | `ContentDescription` | 14 | 动态操作描述加装饰元素无无障碍语义 |
| P1 | 操作字符串资源 | 3 | 回复/缩放接回资源；预览资源确认废弃后删除 |

## 互联网基准

检索日期：2026-09-22。优先采用 Android Developers、AOSP、AndroidX 源码，
不以搜索结果摘要单独作为依据。

### RestrictedApi

来源：

- AndroidX `Person` 源码：
  <https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/app/Person.java>
- AndroidX `IconCompat` 源码：
  <https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/graphics/drawable/IconCompat.java>
- AndroidX `Person` API：
  <https://developer.android.com/reference/androidx/core/app/Person>

基准结论：

- `Person.fromAndroidPerson()` 明确标注 `@RestrictTo(LIBRARY_GROUP_PREFIX)`，
  不是外部模块的稳定公共 API。
- AndroidX 内部实现本身就是读取平台 `Person` 的公开 getter，再把平台 `Icon`
  转成 `IconCompat`，最后通过 `Person.Builder` 构造对象。
- `IconCompat.createFromIcon(Context, Icon)` 是公开重载，适合处理资源型、
  Bitmap 型和 URI 型图标。

本项目决策：

- 不使用 `@SuppressLint("RestrictedApi")` 掩盖该 error。
- 在 `NotificationMessages.read()` 中使用公共 getter 和 `Person.Builder`
  重建 `Person`。
- 图标使用 `IconCompat.createFromIcon(context, nativeIcon)`；上下文不可用时
  只丢弃图标，不从私有限制 API 取回。
- 仪器测试需设置可用的应用上下文，并继续断言 key、URI、图标和 flags 不丢失。

### MissingPermission

来源：

- AOSP SystemUI 权限声明：
  <https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/SystemUI/AndroidManifest.xml#L110>
- AOSP SystemUI `BroadcastSender`：
  <https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/SystemUI/src/com/android/systemui/broadcast/BroadcastSender.kt#L53>
- AOSP SystemUI 定向抑制案例：
  <https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/SystemUI/src/com/android/systemui/qs/tiles/impl/custom/domain/CustomTileMapper.kt#L105>

基准结论：

- AOSP SystemUI 自己使用 `Context.sendBroadcastAsUser()`，并在 SystemUI
  Manifest 中声明 `INTERACT_ACROSS_USERS_FULL`。
- AOSP SystemUI 对静态检查看不到宿主 Manifest 权限的场景，会使用
  `@SuppressLint("MissingPermission")` 并注明实际权限来源。

本项目决策：

- `notifyWeChatRoundRemoved()` 只由 SystemUI 的 Xposed hook 调用，模块 Manifest
  不是实际运行权限来源。
- 保留现有 `RuntimeException` 保护。
- 在方法上添加 `@SuppressLint("MissingPermission")`，注释说明运行时使用
  SystemUI 宿主权限和 `INTERACT_ACROSS_USERS_FULL`。
- 不向模块 Manifest 添加 `INTERACT_ACROSS_USERS`。

### SdCardPath

来源：

- Android `Context.getDataDir()`：
  <https://developer.android.com/reference/android/content/Context#getDataDir()>
- AOSP `Context.java`：
  <https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/content/Context.java>
- Android 多用户应用：
  <https://source.android.com/docs/devices/admin/multiuser-apps>

基准结论：

- `getDataDir()` 返回当前调用上下文的私有数据根目录，并随用户或存储位置变化。
- 绝对路径不应被持久化；运行时通过 Context 获取才是可靠方式。

本项目决策：

- `MessagingBuilder.loadSelfIcon()` 使用 `wechatPkgCtx.getDataDir()`。
- 删除 `/data/data/com.tencent.mm` 硬编码 fallback。
- 无法取得数据目录时返回 `null`，不猜测路径。
- 该改动同时覆盖主用户、工作资料和 adopted storage 等路径差异。

### StaticFieldLeak

来源：

- Android `Context.getApplicationContext()`：
  <https://developer.android.com/reference/android/content/Context#getApplicationContext()>
- `NotificationListenerService`：
  <https://developer.android.com/reference/android/service/notification/NotificationListenerService>

基准结论：

- `getApplicationContext()` 返回当前进程唯一全局 Application 对应的 Context，
  生命周期与进程一致。进程级静态缓存 Application Context 不属于典型 Activity
  leak。
- `NotificationListenerService` 有连接、断开和重新绑定生命周期。静态强引用可以
  保留旧实例，使用弱引用并在每次构造或重绑定时更新更稳妥。

本项目决策：

- `appContext` 和 `packageContext` 保持进程级语义，对字段做局部
  `@SuppressLint("StaticFieldLeak")`，注释说明只保存 Application/package
  Context，不保存 Activity。
- `mNLS` 改为 `WeakReference<NotificationListenerService>`。
- `setNLS()` 每次包装并替换引用，`getNLS()` 返回 `ref.get()`。
- `MainHook` 移除只设置首个 NLS 的 `AtomicReference.compareAndSet()`，确保新实例
  可以刷新弱引用。
- `cancelNotification()` 先取局部强引用，再检查是否为空。

### ContentDescription

来源：

- Android 无障碍视图指南：
  <https://developer.android.com/guide/topics/ui/accessibility/views/apps-views>
- `RemoteViews.setContentDescription()`：
  <https://developer.android.com/reference/android/widget/RemoteViews#setContentDescription(int,%20java.lang.CharSequence)>
- AOSP Notification `bindProfileBadge()` 实例：
  <https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/Notification.java>

基准结论：

- 有交互用途的 UI 元素需要描述其目的，动态内容应由运行时绑定。
- 仅用于视觉装饰的图形可设为无无障碍语义。
- AOSP 通知标准模板使用 `RemoteViews.setContentDescription()` 动态描述图标。

本项目决策：

- `MediaDecorator.bindAction()` 对 `contentView` 和 `bigContentView` 的每个操作
  图标执行 `setContentDescription(id, action.title)`。
- 操作标题为空时回退到应用名称，避免暴露无名称按钮。
- `smallIcon`、`largeIcon`、`foregroundImage` 等装饰图片在 XML 中标为
  `android:contentDescription="@null"`，并设置
  `android:importantForAccessibility="no"`。
- 隐藏的操作槽位也先声明 `@null`；显示后由 `MediaDecorator` 覆盖为真实描述。
- 验收目标是 14 条 `ContentDescription` warning 全部消失。

### 操作字符串资源

来源：

- Android 字符串资源指南：
  <https://developer.android.com/guide/topics/resources/string-resource>
- Android 本地化指南：
  <https://developer.android.com/guide/topics/resources/localization>

基准结论：

- 面向用户的操作文案应来自 string resource，由系统按 locale 选择副本。

本项目决策：

- `MessagingBuilder` 使用模块 Context 读取 `R.string.action_reply` 和
  `R.string.action_zoom`，读取失败时保留现有中文 fallback。
- 复用 `WeChatDecorator.moduleString()` 的相同模式，避免扩大宿主资源访问范围。
- `action_preview_image` 当前没有代码引用。确认不存在待恢复功能后，同时删除
  `values/strings.xml` 和 `values-zh/strings.xml` 中的定义。
- 本阶段不顺便重构其他资源字符串。

## 实施阶段

### 阶段 0：建立基线

1. 确认工作区只有用户明确允许的未跟踪文件。
2. 保存当前 `lint-results-debug.xml` 的 item 数和 fingerprint。
3. 记录当前签名 APK 的证书 SHA-256。
4. 不以新增 lint baseline 的方式隐藏 error。

### 阶段 1：P0 与真实边界风险

建议提交主题：`fix: align lint with host APIs and service lifecycle`

1. 修复 `NotificationMessages.read()` 的 `RestrictedApi`。
2. 对 `notifyWeChatRoundRemoved()` 增加带依据的权限抑制。
3. `MessagingBuilder.loadSelfIcon()` 改用 `getDataDir()`。
4. `NevoDecoratorService` 的两个静态 Context 加局部抑制说明。
5. `mNLS` 改为弱引用，并同步调整 `MainHook` 的 NLS 更新逻辑。
6. 运行单元测试、仪器 APK 构建和 lint。

### 阶段 2：无障碍与本地化

建议提交主题：`fix: improve notification accessibility and action localization`

1. `MediaDecorator` 动态设置操作图标描述。
2. 两套媒体通知布局标记装饰图片无无障碍语义。
3. `MessagingBuilder` 接入回复和缩放字符串资源。
4. 删除确认废弃的 `action_preview_image`。
5. 再次运行 lint，确认 `ContentDescription` 和三项操作资源 warning 消失。

### 阶段 3：另行评估的清理

1. `ObsoleteSdkInt` 17 项，独立做机械清理。
2. `UnusedAttribute` 4 项，优先删除红色阴影残留；确有需求再建立 `layout-v28`。
3. 其余 14 项旧 `UnusedResources` 同时清理默认和中文资源。

## 验证

### 构建与测试

使用项目现有 release 签名配置：

```powershell
.\gradlew.bat -PrequireReleaseSigning=true testDebugUnitTest assembleDebug assembleDebugAndroidTest --console=plain --offline
```

### Lint

```powershell
.\gradlew.bat -PrequireReleaseSigning=true lintDebug -Dorg.gradle.jvmargs=-Xmx4g --offline --console=plain
```

### 签名

构建后使用 `apksigner verify --print-certs` 核对主 APK 和 androidTest APK，
证书 SHA-256 必须与现有 release keystore 一致。

### 回归重点

1. 微信通知仍保留 free-form RemoteInput 回复动作。
2. 回复重发、缓存清除、再次来信后回复动作仍存在。
3. 旧 token 不会删除新通知。
4. 工作资料或非主用户路径下可取得微信数据目录。
5. 操作图标在 TalkBack 下能读出操作名称；装饰图片不产生额外焦点。
6. 英文 locale 下回复和缩放文案来自英文资源。

## 风险与回退

- `Person` 重建涉及消息发送者身份，必须保留 key、URI、bot、important、icon
  的仪器测试。
- `WeakReference` 不能改变 SystemUI 是否持续持有活跃 NLS 的前提；若设备验证
  发现引用过早失效，应回退为“生命周期内可刷新”的 holder，而不是恢复首个实例
  强引用。
- 无障碍 XML 只处理装饰元素；操作槽位描述必须继续由运行时设置。
- 字符串资源读取失败必须保留 fallback，不能因模块 Context 不可用丢失回复按钮。
- 阶段 1 和阶段 2 分开提交，便于单独回退。

## 完成标准

1. `lintDebug` 成功。
2. 两个 error 均为实际修复或有明确依据的局部抑制。
3. `SdCardPath`、`StaticFieldLeak`、`ContentDescription` 和三处操作资源 warning
   消失或按计划删除。
4. 单元测试、Debug APK、androidTest APK 构建通过。
5. Debug APK 继续使用现有 release 证书签名。
6. 不把阶段 3 的机械清理混入前两阶段提交。
