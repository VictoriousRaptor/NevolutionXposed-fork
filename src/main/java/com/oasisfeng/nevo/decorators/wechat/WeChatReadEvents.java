package com.oasisfeng.nevo.decorators.wechat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;
import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Observes independently verified conversation read operations. */
final class WeChatReadEvents {
    /** Captures a fixed conversation/generation; null means there is no matching active round. */
    interface Listener { Runnable capture(String talker); }
    interface NameResolver { Class<?> find(String name) throws ClassNotFoundException; }
    interface Hooker { Runnable hook(Method method, XC_MethodHook callback) throws Throwable; }


    private static final class HookState { volatile boolean ready; }
    private volatile HookState installed;
    private volatile String lastHookFailureType;

    static boolean supports(String versionName, long versionCode) {
        return ("8.0.80".equals(versionName) && versionCode == 3200) || "8.0.79".equals(versionName) && (versionCode == 3182 || versionCode == 3183);
    }

    static Method resolve(String versionName, NameResolver resolver) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> owner = resolver.find("8.0.80".equals(versionName) ? "com.tencent.mm.storage.m4" : "com.tencent.mm.storage.l4");
        Method method = owner.getDeclaredMethod("b0", String.class);
        validate(method);
        method.setAccessible(true);
        return method;
    }

    synchronized void install(Context context, ClassLoader loader, Listener listener) {
        if (ready()) return;
        String stage = "version";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(WeChatDecorator.WECHAT_PACKAGE, 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            if (!supports(info.versionName, code)) {
                log("read_hook_unavailable", "reason=unsupported_version");
                return;
            }
            stage = "signature";
            Method method = resolve(info.versionName, name -> Class.forName(name, false, loader));
            stage = "hook";
            if (!installResolved(method, listener, (target, callback) -> {
                XposedInterface.HookHandle handle = XposedBridge.hookMethod(target, callback);
                return handle == null ? null : handle::unhook;
            })) {
                log("read_hook_unavailable", "reason=hook type=" + lastHookFailureType);
                return;
            }
            log("read_hook_ready", "version=" + info.versionName + " code=" + code + " match=exact");
        } catch (Throwable failure) {
            XposedBridge.rethrowFrameworkError(failure);
            log("read_hook_unavailable", "reason=" + stage + " type=" + failure.getClass().getSimpleName());
        }
    }

    synchronized boolean installResolved(Method method, Listener listener, Hooker hooker) {
        if (ready()) return true;
        HookState state = new HookState();
        Runnable unhook = null;
        try {
            validate(method);
            if (listener == null) throw new IllegalArgumentException("Missing read listener");
            unhook = hooker.hook(method, callback(listener, state));
            if (unhook == null) throw new IllegalStateException("Missing read hook handle");
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

    boolean ready() { HookState state = installed; return state != null && state.ready; }

    private static XC_MethodHook callback(Listener listener, HookState state) {
        return new XC_MethodHook() {
            // The bridge supplies the same parameter object to BEFORE and AFTER, including
            // nested calls. Weak keys also avoid retaining a capture if the bridge aborts.
            private final Map<MethodHookParam, Runnable> captured =
                    Collections.synchronizedMap(new WeakHashMap<>());

            @Override protected void beforeHookedMethod(MethodHookParam param) {
                captured.remove(param);
                if (!state.ready || param.thisObject == null || param.args == null || param.args.length != 1
                        || !(param.args[0] instanceof String) || ((String) param.args[0]).isEmpty()) return;
                try {
                    Runnable commit = listener.capture((String) param.args[0]);
                    if (commit != null) {
                        captured.put(param, commit);
                        log("read_event_captured", "target=available");
                    }
                } catch (Throwable failure) {
                    XposedBridge.rethrowFrameworkError(failure);
                    log("read_event_error", "reason=capture type=" + failure.getClass().getSimpleName());
                }
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                Runnable commit = captured.remove(param);
                if (!state.ready || commit == null) return;
                if (param.hasThrowable() || !Boolean.TRUE.equals(param.getResult())) {
                    log("read_event_ignored", "reason=unsuccessful throwable=" + param.hasThrowable());
                    return;
                }
                try {
                    // b0 can succeed again for an already read/missing conversation. The
                    // fixed target owns idempotence and must never look up a newer round.
                    commit.run();
                    log("read_event_committed", "result=success");
                } catch (Throwable failure) {
                    XposedBridge.rethrowFrameworkError(failure);
                    log("read_event_error", "reason=commit type=" + failure.getClass().getSimpleName());
                }
            }
        };
    }

    private static void validate(Method method) throws NoSuchMethodException {
        int modifiers = method.getModifiers();
        if (!"b0".equals(method.getName()) || method.getReturnType() != boolean.class
                || !Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)
                || method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class)
            throw new NoSuchMethodException("Unexpected conversation read signature");
    }

    private static void log(String stage, String details) {
        try { if (BuildConfig.DEBUG) Log.i("WeChat.Read", "source=" + stage + " " + details); }
        catch (RuntimeException unavailable) { /* The host test runtime has no Android logger. */ }
    }
}
