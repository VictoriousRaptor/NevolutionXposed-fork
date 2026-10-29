package com.oasisfeng.nevo.decorators.wechat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;
import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;
import com.oasisfeng.nevo.xposed.compat.XposedHelpers;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/** Observes confirmed outgoing messages; never reads their body. */
final class WeChatAppReplyEvents {
    interface Listener { void onSent(String talker, long messageId, long created); }
    interface NameResolver { Class<?> find(String name) throws ClassNotFoundException; }
    interface Hooker { Runnable hook(Method method, XC_MethodHook callback) throws Throwable; }

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

        private static final Profile WX79 = new Profile("8.0.79/3182", "com.tencent.mm.storage.e9",
                "r1", "C0", "M0", "N0", "a3", "M2", "F2");
        private static final Profile WX79_3183 = new Profile("8.0.79/3183", "com.tencent.mm.storage.e9",
                "r1", "C0", "M0", "N0", "a3", "M2", "F2");
        private static final Profile WX79_3200 = new Profile("8.0.79/3200", "com.tencent.mm.storage.e9",
                "s1", "z0", "M0", "N0", "a3", "M2", "F2");
        private static final Profile WX72 = new Profile("8.0.72/3085", "com.tencent.mm.storage.f9",
                "q1", "C0", "M0", "O0", "b3", "N2", "F2");
        private static final Profile WX78 = new Profile("8.0.78/3180", "com.tencent.mm.storage.e9",
                "t1", "z0", "M0", "N0", "Z2", "L2", "E2");
        private static final Profile WX77_3160 = new Profile("8.0.77/3160", "com.tencent.mm.storage.e9",
                "u1", "A0", "P0", "Q0", "Z2", "L2", "E2");
        private static final Profile WX77_3141 = new Profile("8.0.77/3141", "com.tencent.mm.storage.e9",
                "r1", "C0", "M0", "N0", "a3", "M2", "F2");

        static final List<WeChatProfileSelector.Candidate<Profile>> CANDIDATES = Arrays.asList(
                candidate(WX79, "8.0.79", 3182), candidate(WX79_3183, "8.0.79", 3183),
                candidate(WX79_3200, "8.0.79", 3200), candidate(WX72, "8.0.72", 3085),
                candidate(WX78, "8.0.78", 3180), candidate(WX77_3160, "8.0.77", 3160),
                candidate(WX77_3141, "8.0.77", 3141));

        private static WeChatProfileSelector.Candidate<Profile> candidate(Profile profile, String name, long code) {
            String id = "app-reply-" + profile.label;
            return new WeChatProfileSelector.Candidate<>(id, name, code, profile,
                    code == 3183 ? "app-reply-8.0.79/3182" : id);
        }

        static Profile forVersion(String name, long code) {
            for (WeChatProfileSelector.Candidate<Profile> candidate : CANDIDATES)
                if (candidate.versionName.equals(name) && candidate.verifiedCode == code) return candidate.profile;
            return null;
        }
    }

    static final class Resolved {
        final Profile profile;
        final Method setStatus, isSend, status, talker, id, created;
        final Method[] excluded;
        Resolved(Profile profile, Method setStatus, Method isSend, Method status, Method talker,
                 Method id, Method created, Method[] excluded) {
            this.profile = profile;
            this.setStatus = setStatus;
            this.isSend = isSend;
            this.status = status;
            this.talker = talker;
            this.id = id;
            this.created = created;
            this.excluded = excluded;
        }
    }

    private static final class HookState { volatile boolean ready; }
    private volatile HookState installed;
    private volatile String lastHookFailureType;

    static WeChatProfileSelector.Selection<Profile> select(WeChatProfileSelector.BuildId build,
            WeChatProfileSelector selector, WeChatProfileSelector.Cache cache, NameResolver resolver) {
        return selector.select("app_reply", build, Profile.CANDIDATES,
                profile -> resolve(profile, resolver) != null, cache);
    }

    static Resolved resolve(Profile profile, ClassLoader loader) throws ClassNotFoundException {
        return resolve(profile, name -> Class.forName(name, false, loader));
    }

    static Resolved resolve(Profile profile, NameResolver resolver) throws ClassNotFoundException {
        Class<?> message = resolver.find(profile.messageClass);
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
        return new Resolved(profile, setStatus, isSend, status, talker, id, created, excluded);
    }

    void install(Context context, ClassLoader loader, Listener listener, WeChatProfileSelector selector,
            WeChatProfileSelector.Cache cache) {
        if (ready()) return;
        String stage = "version";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(WeChatDecorator.WECHAT_PACKAGE, 0);
            WeChatProfileSelector.BuildId build = WeChatProfileSelector.BuildId.fromPackageInfo(info);
            WeChatProfileSelector.Selection<Profile> choice =
                    select(build, selector, cache, name -> Class.forName(name, false, loader));
            if (choice.profile == null) {
                log("app_reply_unavailable", "reason=" + choice.kind.name().toLowerCase());
                return;
            }
            stage = "signature";
            Resolved resolved = resolve(choice.profile, loader);
            stage = "hook";
            if (!installResolved(resolved, listener, (method, callback) -> {
                XposedInterface.HookHandle handle = XposedBridge.hookMethod(method, callback);
                return handle::unhook;
            })) {
                log("app_reply_unavailable", "stage=hook type=" + lastHookFailureType);
                return;
            }
            log("app_reply_ready", "profile=" + choice.profile.label + " match=" + choice.kind.name().toLowerCase());
        } catch (Throwable failure) {
            XposedBridge.rethrowFrameworkError(failure);
            log("app_reply_unavailable", "stage=" + stage + " type=" + failure.getClass().getSimpleName());
        }
    }

    boolean ready() { HookState state = installed; return state != null && state.ready; }

    boolean installResolved(Resolved resolved, Listener listener, Hooker hooker) {
        HookState state = new HookState();
        Runnable unhook = null;
        try {
            unhook = hooker.hook(resolved.setStatus, callback(resolved, listener, state));
            state.ready = true;
            installed = state;
            return true;
        } catch (Throwable failure) {
            state.ready = false;
            if (unhook != null) try { unhook.run(); } catch (Throwable ignored) { }
            XposedBridge.rethrowFrameworkError(failure);
            lastHookFailureType = failure.getClass().getSimpleName();
            return false;
        }
    }

    private static XC_MethodHook callback(Resolved resolved, Listener listener, HookState state) {
        return new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!state.ready || param.hasThrowable() || param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof Integer) || (int) param.args[0] != 2) return;
                try {
                    Object value = param.thisObject;
                    if (((Number) resolved.isSend.invoke(value)).intValue() != 1
                            || ((Number) resolved.status.invoke(value)).intValue() != 2) return;
                    for (Method skip : resolved.excluded) if ((Boolean) skip.invoke(value)) return;
                    long messageId = ((Number) resolved.id.invoke(value)).longValue();
                    long time = ((Number) resolved.created.invoke(value)).longValue();
                    String key = (String) resolved.talker.invoke(value);
                    if (messageId <= 0 || time <= 0 || key == null || key.isEmpty()) return;
                    listener.onSent(key, messageId, time);
                } catch (Exception failure) {
                    log("app_reply_event_error", "type=" + failure.getClass().getSimpleName());
                }
            }
        };
    }

    private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... arguments) {
        Method method = XposedHelpers.findMethodExact(owner, name, arguments);
        int modifiers = method.getModifiers();
        if (method.getReturnType() != result || Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers))
            throw new IllegalStateException("Unexpected signature: " + name);
        return method;
    }

    private static void log(String stage, String details) {
        if (BuildConfig.DEBUG) Log.i("WeChat.Identity", "source=" + stage + " " + details);
    }
}
