# WeChat 8.0.78 reply findings (2026-09-13)

## Device and app facts

| Item | Value |
| --- | --- |
| Device | OnePlus 7 Pro (GM1910), Android 16, arm64-v8a, `BP4A.251205.006 release-keys` |
| WeChat | `versionName=8.0.78`, `versionCode=3180`, `minSdk=24`, `targetSdk=34` |
| Installer | `com.android.packageinstaller` (China build, side-loaded 2026-09-12) |
| Base APK | 280,302,239 B, SHA-256 `ff507d8ca93d735342a5e29d374beadce166e1c3cba8302f655f0c194331fb3b` |
| Root framework | ReSukiSU + Zygisk LSPosed v2.2.0 (7854); no LSPosed manager app installed |
| Module before the fix | 3.1.0 (versionCode 8), release-signed `7a707a90…` |

Decompilation: JADX 1.5.6, `classes11.dex`. Device artifacts live under
`.debug-artifacts/device/20260913-wechat-8078-3180/` and are never committed.

## Symptom

WeChat 8.0.78 notifications had no reply action. The module refused to expose
one because its version profile could not be validated:

```text
NX_REPLY stage=profile_rejected version=8.0.78/3180 profile=wechat-legacy
  receiver=com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver
  helper=z2.s1 gates=none usable=false
NX_REPLY stage=car_mode_bypass_skipped reason=profile_unusable
```

## Verified 8.0.78 reply mapping

`com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver.onReceive`:

```java
String user = f2.l(intent, "key_username");            // key_username
Bundle results = z2.s1.b(intent);                       // RemoteInput.getResultsFromIntent
CharSequence text = results.getCharSequence("key_voice_reply_text");
if (bs1.a.c()) {                                        // auto-mode config flag
    if (!bs1.a.g()) { log "not open car mode"; }
    else {
        if (!bs1.a.b()) { log "not install auto app"; return; }
        u1.a().mj(user, text.toString(), d2.C(user), 0);   // send
    }
}
```

| Descriptor | 8.0.78 |
| --- | --- |
| Receiver | `com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver` (`classes11.dex`) |
| Username extra | `key_username` |
| Reply result key | `key_voice_reply_text` |
| RemoteInput helper | `z2.s1.b(android.content.Intent) : android.os.Bundle` |
| Car-mode gates | `bs1.a.c()` config flag, `bs1.a.g()` car UI mode + AOAP USB, `bs1.a.b()` Android Auto package MD5 — all `public static boolean` |
| Car notification builder | `bs1.a.a(String, String)` attaches the `key_voice_reply_text` RemoteInput and the `MM_AUTO_REPLY_MESSAGE` / `MM_AUTO_HEARD_MESSAGE` PendingIntents |
| Send call | `rn3.u1.a().mj(String, String, String, int)` |

## Root cause

`WeChatReplyProfile.forPackage()` knew only two layouts: 8.0.72
(`dn1.a` with `f`/`h`/`c`) and the legacy 8.0.76 layout (`rn1.a` or
`com.tencent.mm.booter.auto.AutoLogic` with `f`/`g`/`c`). 8.0.78 fell into the
legacy profile, whose gate class no longer carries those methods, so validation
returned `gates=none`. Per design, an unvalidated profile must not offer a reply
action that cannot dispatch, so the reply button was hidden and the car-mode
bypass was skipped. 8.0.78 moved the gates to `bs1.a` with `c`, `g`, `b`.

## Fix in this working tree

`WeChatReplyProfile` gained a verified `wechat-8.0.78` profile (gates `bs1.a`
`[c, g, b]`, helper `z2.s1`, unchanged receiver) and `forPackage()` maps
`8.0.78` / versionCode `3180` to it. The receiver, helper and gate signatures
are still validated at runtime, so an unvalidated build keeps withholding the
action instead of dispatching into a dead receiver.

## Verification (2026-09-13 23:34)

```text
NX_REPLY stage=profile_ready version=8.0.78/3180 profile=wechat-8.0.78
  receiver=com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver
  helper=z2.s1 gates=bs1.a[c, g, b] usable=true
```

- No `hookAutoLogicMethods` / `hookCarModeBypass` failure lines after the restart.
- The APK pulled back from `/data/app` hashes identically to the local build
  (SHA-256 `179f4cf8ba4e0ead0ff1beb1bc5f58f93de3a675714aaff59e46a789e3c42410`).
- The module was updated in place with the release key (`adb install -r`), so
  the LSPosed activation and scope were preserved.
- `testDebugUnitTest` passes.

The first notification posted after the update carried the reply action and the
car conversation, and a reply typed into the shade ran the whole native chain:

```text
actions={ [0] "回复" -> PendingIntent{… com.tencent.mm broadcastIntent …} }
extras: android.car.EXTENSIONS=Bundle
NX_REPLY stage=native_receiver notificationId=-1149184867 resultKey=key_voice_reply_text inputLength=2
NX_REPLY stage=native_remote_input_attached resultKey=key_voice_reply_text inputLength=2
NX_REPLY stage=helper_result keys=[key_voice_reply_text]
NX_REPLY stage=native_pending_intent_callback notificationId=-1149184867 resultCode=0
```

## Open items

- Delivery is confirmed: the tester verified that the text sent from the
  notification shade arrived in the conversation, and the module side completed
  the whole native chain with the pending-intent callback returning 0.
- `WeChatImageEvents` still gates on 8.0.72/3085, so the image-preview feature
  stays off on 8.0.78. Unrelated to notification replies.

## Next version

The repeatable, one-pass procedure for the next WeChat release (device
collection, profile probe, install, verification and recording) lives in
`docs/wechat-version-adaptation-playbook.md`.
