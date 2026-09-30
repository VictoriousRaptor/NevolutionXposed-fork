package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;
import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;
import com.oasisfeng.nevo.xposed.compat.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Adds exact notification IDs and observes recall metadata, without reading message bodies. */
final class WeChatRecallEvents {
    interface Listener { void onRecalled(String talker, long serverId); }
    static final String VERIFIED_KEY = "nevo.wechat.verifiedNotificationKey";
    private final RecallIndex index = new RecallIndex();

    static String messageClass(String name, long code) {
        if ("8.0.72".equals(name) && code == 3085) return "com.tencent.mm.storage.f9";
        if ("8.0.78".equals(name) && code == 3180) return "com.tencent.mm.storage.e9";
        return null;
    }

    void install(Context context, ClassLoader loader, Listener listener) {
        String stage = "version";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(WeChatDecorator.WECHAT_PACKAGE, 0);
            String messageName = messageClass(info.versionName, info.versionCode);
            if (messageName == null) { log("unavailable", "reason=unsupported_version"); return; }
            stage = "signature";
            Class<?> item = Class.forName("com.tencent.mm.booter.notification.NotificationItem", false, loader);
            Field notification = field(item, "f", Notification.class);
            Field itemTalker = field(item, "h", String.class);
            // Despite the msgId label in toString(), NotificationTools receives the server ID.
            Field itemServerId = field(item, "i", long.class);
            Method publishNotification = method(item, "a", void.class, Context.class);
            Class<?> event = Class.forName("com.tencent.mm.autogen.events.RevokeMsgEvent", false, loader);
            Class<?> payload = Class.forName("8.0.72".equals(info.versionName) ? "pm.ds" : "fm.ks", false, loader);
            Field eventData = field(event, "g", payload);
            Class<?> message = Class.forName(messageName, false, loader);
            Field recalledMessage = field(payload, "c", message);
            // Read the very same accessor passed to NotificationTools, not a guessed field.
            Method serverId = method(message, "8.0.72".equals(info.versionName) ? "I0" : "F0", long.class);
            Method talker = method(message, "8.0.72".equals(info.versionName) ? "O0" : "N0", String.class);
            Class<?> eventBase = Class.forName("com.tencent.mm.sdk.event.IEvent", false, loader);
            if (event.getSuperclass() != eventBase) throw new IllegalStateException("Unexpected event base");
            Method publishEvent = method(eventBase, "e", boolean.class);
            stage = "hook";
            XposedBridge.hookMethod(publishEvent, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!event.isInstance(param.thisObject)) return;
                    try {
                        Object value = recalledMessage.get(eventData.get(param.thisObject));
                        if (value == null) return;
                        long id = ((Number) serverId.invoke(value)).longValue();
                        String key = (String) talker.invoke(value);
                        if (id > 0 && key != null && !key.isEmpty()) {
                            index.add(key, id);
                            listener.onRecalled(key, id);
                        }
                    } catch (Exception failure) { log("event_error", "type=" + failure.getClass().getSimpleName()); }
                }
            });
            XposedBridge.hookMethod(publishNotification, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Notification n = (Notification) notification.get(param.thisObject);
                        String key = (String) itemTalker.get(param.thisObject);
                        long id = itemServerId.getLong(param.thisObject);
                        if (n == null || key == null || key.isEmpty() || id <= 0) return;
                        n.extras.putString(NotificationMessages.KEY, key);
                        n.extras.putBoolean(VERIFIED_KEY, true);
                        n.extras.putLong(NotificationMessages.SERVER_ID, id);
                        long[] recalled = index.ids(key);
                        n.extras.putLongArray(NotificationMessages.RECALLED_IDS, recalled);
                        RecallHistory history = new RecallHistory(); history.addAll(recalled);
                        n.extras.putBoolean(NotificationMessages.RECALL_PROMPT, history.contains(id));
                        if (!history.contains(id)) n.extras.remove(WeChatDecorator.EXTRA_SUPPRESSED_RECALL);
                    } catch (Exception failure) { log("identity_error", "type=" + failure.getClass().getSimpleName()); }
                }
            });
            log("ready", "version=" + info.versionName + " code=" + info.versionCode);
        } catch (Throwable failure) {
            XposedBridge.rethrowFrameworkError(failure);
            log("unavailable", "stage=" + stage + " type=" + failure.getClass().getSimpleName());
        }
    }

    private static Field field(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (field.getType() != type) throw new IllegalStateException("Unexpected field signature");
                field.setAccessible(true); return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... arguments) {
        Method method = XposedHelpers.findMethodExact(owner, name, arguments);
        if (method.getReturnType() != result) throw new IllegalStateException("Unexpected method signature");
        return method;
    }
    private static void log(String stage, String details) {
        if (BuildConfig.DEBUG) Log.d("WeChat.Identity", "source=recall_" + stage + " " + details);
    }
}
