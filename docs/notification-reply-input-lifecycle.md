# 通知更新期间的回复输入与竞态记录

记录日期：2026-10-07。此次发布修复媒体通知重复追加回复动作，并补充动画表情回归用例；下述竞态仅记录，未修改处理逻辑。

## 常规路径

`MessagingBuilder.attachReplyAction()` 去掉旧的可投递自由文本回复 Action 后，添加一个当前代理回复。它不操作 SystemUI 编辑框，也不取消旧 PendingIntent。

同一通知 ID 的普通原生回复和媒体原生回复沿用 `proxyDirectReply()` 的 REPLY action、id URI、requestCode=0 和显式 package。`FLAG_UPDATE_CURRENT` 保留 PendingIntent token、更新转发目标及结果键；Android 12+ 的 `FLAG_MUTABLE` 允许 RemoteInput 填入结果。在同一通知身份、普通 REPLY 代理和稳定原生结果键的条件下，旧回复入口仍可派发。

清除 `nevo.actionsSerialized` 标记使 notify hook 重新构建动作。AOSP SystemUI 按 Intent 身份寻找匹配的自由文本回复，并尝试复用活动输入视图。单纯正在编辑时的通知更新不会因 `onNotificationUpdateOrReset()` 自动清空文字；发送进度状态下的更新才触发 reset。上述是源码核对，不是厂商 SystemUI 的实际验证。

## 已知竞态：结果键改变后用旧输入框立即发送

触发条件必须同时成立：

1. 同 ID 新通知的原生 RemoteInput resultKey 与旧通知不同。
2. `FLAG_UPDATE_CURRENT` 已把代理的 `EXTRA_RESULT_KEY` 更新为新键。
3. SystemUI 尚使用旧 RemoteInput，将正文放在旧键下并立即发送。

接收器执行 `results.getCharSequence(result_key)` 时可能得到 null，以 `native_drop reason=missing_input` 退出，不进行原生转发。这来自既有代理更新规则，本次没有引入或修复。尚无证据表明已适配的微信版本在该场景会改变结果键，不能将静态可达条件写成已复现故障。

通知 ID/tag/会话或回复入口类型改变、新通知失去匹配回复入口，以及原生 PendingIntent 失效，也可能分别导致编辑框关闭或发送失败。须与上述结果键竞态分别核对。

## 验证范围

- `animatedStickerMediaBranch` 通过真实 `WeChatDecorator.Local.apply()` 覆盖 `[动画表情]`、`[2条][动画表情]` 分类、动作去重、代理身份和标记失效。
- `animatedStickerReplyAfterUpdate` 使用第一条保存的旧代理 PendingIntent，在第二条动画表情同 ID 更新且原生目标改变后发送；预期最新目标恰好接收一次，中文/emoji 和最新通知正文保留。结果键固定为 native-key，latest 控制器为测试实现。
- 新增仪器用例已编译、注册，本轮未在 Android 设备运行。37 项注册数量不代表 37 项运行通过。
- 草稿、光标、焦点、IME 和厂商布局重建尚待真实 SystemUI 验收；现有派发测试不能代替输入框 UI 验证。
- 依用户要求，竞态风险仅在此记录；发送规则、键映射和回退路径保持原样。

## 参考源码

- [PendingIntent：FLAG_UPDATE_CURRENT](https://developer.android.com/reference/android/app/PendingIntent#FLAG_UPDATE_CURRENT)
- [AOSP RemoteInputViewController：匹配回复入口](https://android.googlesource.com/platform/frameworks/base.git/+/14c1c515496e7adb7cd31390f4d4651c2c536caf/packages/SystemUI/src/com/android/systemui/statusbar/policy/RemoteInputViewController.kt)
- [AOSP NotificationContentView：复用活动输入视图](https://android.googlesource.com/platform/frameworks/base.git/+/c2230a94b06de75b8a41e3889f9ab28bd84edf26/packages/SystemUI/src/com/android/systemui/statusbar/notification/row/NotificationContentView.java)
- [AOSP RemoteInputView：reset 条件](https://android.googlesource.com/platform/frameworks/base/+/a7a36d968e6fceb0d1fcd83c5f8bfad0f8d4f476/packages/SystemUI/src/com/android/systemui/statusbar/policy/RemoteInputView.java)
