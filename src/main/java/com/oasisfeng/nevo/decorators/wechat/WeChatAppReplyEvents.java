package com.oasisfeng.nevo.decorators.wechat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;
import com.oasisfeng.nevo.xposed.compat.XposedHelpers;

import java.lang.reflect.Method;

/** Observes confirmed outgoing messages; never reads their body. */
final class WeChatAppReplyEvents {
    interface Listener { void onSent(String talker, long messageId, long created); }

    static final class Profile {
        final String label, messageClass, setStatus, isSend, status, talker;
        final String[] excluded;

        private Profile(String label, String messageClass, String setStatus, String isSend,
                        String status, String talker, String... excluded) {
            this.label = label;
            this.messageClass = messageClass;
            this.setStatus = setStatus;
            this.isSend = isSend;
            this.status = status;
            this.talker = talker;
            this.excluded = excluded;
        }

        static Profile forVersion(String name, long code) {
            if ("8.0.72".equals(name) && code == 3085)
                return new Profile("8.0.72/3085", "com.tencent.mm.storage.f9", "q1", "C0", "M0", "O0",
                        "b3", "N2", "F2");
            if ("8.0.78".equals(name) && code == 3180)
                return new Profile("8.0.78/3180", "com.tencent.mm.storage.e9", "t1", "z0", "M0", "N0",
                        "Z2", "L2", "E2");
            if ("8.0.77".equals(name) && code == 3160)
                return new Profile("8.0.77/3160", "com.tencent.mm.storage.e9", "u1", "A0", "P0", "Q0",
                        "Z2", "L2", "E2");
            if ("8.0.77".equals(name) && code == 3141)
                return new Profile("8.0.77/3141", "com.tencent.mm.storage.e9", "r1", "C0", "M0", "N0",
                        "a3", "M2", "F2");
            return null;
        }
    }

    void install(Context context, ClassLoader loader, Listener listener) {
        String stage = "version";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(WeChatDecorator.WECHAT_PACKAGE, 0);
            Profile profile = Profile.forVersion(info.versionName, info.versionCode);
            if (profile == null) {
                log("app_reply_unavailable", "reason=unsupported_version");
                return;
            }
            stage = "signature";
            Class<?> message = Class.forName(profile.messageClass, false, loader);
            Method setStatus = method(message, profile.setStatus, void.class, int.class);
            if (setStatus.getDeclaringClass() != message) throw new IllegalStateException("Missing status override");
            Method isSend = method(message, profile.isSend, int.class);
            Method status = method(message, profile.status, int.class);
            Method talker = method(message, profile.talker, String.class);
            Method id = method(message, "getMsgId", long.class);
            Method created = method(message, "getCreateTime", long.class);
            Method[] excluded = new Method[profile.excluded.length];
            for (int i = 0; i < excluded.length; i++)
                excluded[i] = method(message, profile.excluded[i], boolean.class);
            stage = "hook";
            XposedBridge.hookMethod(setStatus, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || !(param.args[0] instanceof Integer) || (int) param.args[0] != 2) return;
                    try {
                        Object value = param.thisObject;
                        if (((Number) isSend.invoke(value)).intValue() != 1
                                || ((Number) status.invoke(value)).intValue() != 2) return;
                        for (Method skip : excluded) if ((Boolean) skip.invoke(value)) return;
                        long messageId = ((Number) id.invoke(value)).longValue();
                        long time = ((Number) created.invoke(value)).longValue();
                        String key = (String) talker.invoke(value);
                        if (messageId <= 0 || time <= 0 || key == null || key.isEmpty()) return;
                        listener.onSent(key, messageId, time);
                    } catch (Exception failure) {
                        log("app_reply_event_error", "type=" + failure.getClass().getSimpleName());
                    }
                }
            });
            log("app_reply_ready", "profile=" + profile.label);
        } catch (Throwable failure) {
            XposedBridge.rethrowFrameworkError(failure);
            log("app_reply_unavailable", "stage=" + stage + " type=" + failure.getClass().getSimpleName());
        }
    }

    private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... arguments) {
        Method method = XposedHelpers.findMethodExact(owner, name, arguments);
        if (method.getReturnType() != result) throw new IllegalStateException("Unexpected signature: " + name);
        return method;
    }

    private static void log(String stage, String details) {
        if (BuildConfig.DEBUG) Log.i("WeChat.Identity", "source=" + stage + " " + details);
    }
}
