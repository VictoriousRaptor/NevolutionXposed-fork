# WeChat notification reply compatibility

## Supported targets

- WeChat 8.0.72 from Google Play: under investigation on the connected test device.
- WeChat 8.0.76: preserve the existing notification reply path.

## Local toolchain

- JDK 17
- Android SDK platform 34 and Build Tools 34.0.0
- Android Platform Tools (`adb`)
- JADX 1.5.6 or newer

Do not commit device APKs, LSPosed logs, logcat captures, or decompiled sources.
They belong under `.debug-artifacts/`, which is ignored by Git.

## Reply stages

The reply path is diagnosed in this order:

1. `MainHook` loads in the `com.tencent.mm` main process.
2. The notification is decorated and retains a free-form `RemoteInput` action.
3. The module receiver obtains the result key and input length.
4. The native `PendingIntent` path or synthetic receiver path is selected.
5. WeChat's receiver and RemoteInput helper are invoked without an exception.
6. The message is visible in the conversation.

Diagnostic logs must contain stage names, class and method signatures, notification
IDs, result keys, and input lengths only. They must not contain message text,
contact names, account IDs, or other notification contents.

## Device collection

Record the exact Android build, ABI, WeChat version name/code, installer, LSPosed
version, module scope, and every path returned by `pm path com.tencent.mm` before
changing the module. Pull base/split APKs read-only, calculate SHA-256 hashes, and
decompile only the local copies.

Search the installed WeChat APK for these stable semantic anchors before adding a
version profile:

- `MM_AUTO_REPLY_MESSAGE`
- `key_voice_reply_text`
- `RemoteInput.getResultsFromIntent`
- `android.car.EXTENSIONS`
- `MMAutoMessageReplyReceiver`

Any obfuscated class or method included in a compatibility profile must be
validated by its full parameter and return types at runtime. An unknown profile
must never expose a synthetic reply action that cannot dispatch successfully.
