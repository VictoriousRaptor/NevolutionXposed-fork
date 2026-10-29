package com.oasisfeng.nevo.decorators.wechat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.util.Log;

import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import io.github.libxposed.api.XposedInterface;

/** Keep Unicode emoji before WeChat replaces them with localized names or a generic marker. */
public final class WeChatEmojiHooks {
    private static final String TAG = "WeChat.Emoji";
    private static boolean installed;

    private WeChatEmojiHooks() {}

    static boolean supports(String versionName, long versionCode) {
        return "8.0.79".equals(versionName)
                && (versionCode == 3182 || versionCode == 3183 || versionCode == 3200);
    }

    private static final WeChatProfileSelector.Candidate<String> PROFILE_3182 =
            new WeChatProfileSelector.Candidate<>("emoji-8.0.79/3182", "8.0.79", 3182, "8.0.79/3182");
    private static final WeChatProfileSelector.Candidate<String> PROFILE_3183 =
            new WeChatProfileSelector.Candidate<>("emoji-8.0.79/3183", "8.0.79", 3183, "8.0.79/3183");
    private static final WeChatProfileSelector.Candidate<String> PROFILE_3200 =
            new WeChatProfileSelector.Candidate<>("emoji-8.0.79/3200", "8.0.79", 3200, "8.0.79/3200");

    /** The candidate is accepted only after both methods have been checked in full. */
    static boolean selectCandidate(WeChatProfileSelector.BuildId build, WeChatProfileSelector selector,
                                   WeChatProfileSelector.Cache cache, Class<?> emoji, Class<?> notifications,
                                   Class<?> message) {
        // The three APKs share this complete descriptor. Probe it once for a later unknown code.
        WeChatProfileSelector.Candidate<String> candidate = build.versionCode == 3183 ? PROFILE_3183
                : build.versionCode == 3200 ? PROFILE_3200 : PROFILE_3182;
        return selector.select("emoji", build, Collections.singletonList(candidate), profile -> {
            resolve(emoji, notifications, message);
            return true;
        }, cache).profile != null;
    }

    private static Method[] resolve(Class<?> emoji, Class<?> notifications, Class<?> message)
            throws NoSuchMethodException {
        return new Method[] { instanceMethod(emoji, "d", String.class, String.class),
                instanceMethod(notifications, "a", void.class, message, long.class,
                        boolean.class, boolean.class, String.class, int.class, String.class, String.class,
                        Intent.class, boolean.class) };
    }

    public static synchronized void install(Context context, ClassLoader loader, BooleanSupplier enabled,
                                            Predicate<Executable> deoptimize, WeChatProfileSelector selector,
                                            WeChatProfileSelector.Cache cache) {
        if (installed) return;
        XposedInterface.HookHandle conversionHook = null;
        XposedInterface.HookHandle notificationHook = null;
        NotificationScope scope = new NotificationScope();
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(WeChatDecorator.WECHAT_PACKAGE, 0);
            WeChatProfileSelector.BuildId build = WeChatProfileSelector.BuildId.fromPackageInfo(info);
            if (!"8.0.79".equals(build.versionName)) {
                Log.i(TAG, "source=emoji_hook_unavailable reason=unsupported_version");
                return;
            }
            Class<?> emoji = Class.forName("com.tencent.mm.smiley.g", false, loader);
            Class<?> notifications = Class.forName("com.tencent.mm.booter.notification.m0", false, loader);
            Class<?> message = Class.forName("com.tencent.mm.storage.e9", false, loader);
            if (!selectCandidate(build, selector, cache, emoji, notifications, message)) {
                Log.i(TAG, "source=emoji_hook_unavailable reason=signature_no_match version="
                        + build.versionName + "/" + build.versionCode);
                return;
            }
            Method[] methods = resolve(emoji, notifications, message);
            Method conversion = methods[0], notificationBuilder = methods[1];
            // The verified direct caller can otherwise retain an inlined copy of the conversion.
            if (!deoptimize.test(notificationBuilder)) throw new IllegalStateException("Notification deoptimization failed");
            conversionHook = XposedBridge.hookMethod(conversion, new PreserveEmoji(scope, enabled));
            notificationHook = XposedBridge.hookMethod(notificationBuilder, scope);
            scope.ready = true;
            installed = true;
            Log.i(TAG, "source=emoji_hook_ready version=" + build.versionName + " code=" + build.versionCode
                    + " match=" + (supports(build.versionName, build.versionCode) ? "exact" : "experimental"));
        } catch (Throwable failure) {
            scope.ready = false;
            installed = false;
            // Any partially installed callbacks stay inert even if the framework rejects removal.
            unhook(notificationHook);
            unhook(conversionHook);
            XposedBridge.rethrowFrameworkError(failure);
            Log.w(TAG, "source=emoji_hook_unavailable type=" + failure.getClass().getSimpleName());
        }
    }

    static Method instanceMethod(Class<?> owner, String name, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        int modifiers = method.getModifiers();
        if (method.getReturnType() != result || Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers))
            throw new NoSuchMethodException("Unexpected emoji notification signature");
        method.setAccessible(true);
        return method;
    }

    private static void unhook(XposedInterface.HookHandle handle) {
        if (handle == null) return;
        try {
            handle.unhook();
        } catch (Throwable failure) {
            XposedBridge.rethrowFrameworkError(failure);
            Log.w(TAG, "source=emoji_unhook_failed type=" + failure.getClass().getSimpleName());
        }
    }

    /** The bridge runs AFTER even when the original builder throws. Nested calls keep their scope. */
    static final class NotificationScope extends XC_MethodHook {
        private final ThreadLocal<Integer> depth = new ThreadLocal<>();
        volatile boolean ready;

        @Override protected void beforeHookedMethod(MethodHookParam param) {
            Integer current = depth.get();
            depth.set(current == null ? 1 : current + 1);
        }

        @Override protected void afterHookedMethod(MethodHookParam param) {
            Integer current = depth.get();
            if (current == null || current <= 1) depth.remove();
            else depth.set(current - 1);
        }

        boolean active() {
            Integer current = depth.get();
            return ready && current != null && current > 0;
        }
    }

    static final class PreserveEmoji extends XC_MethodHook {
        private final NotificationScope scope;
        private final BooleanSupplier enabled;

        PreserveEmoji(NotificationScope scope, BooleanSupplier enabled) {
            this.scope = scope;
            this.enabled = enabled;
        }

        @Override protected void beforeHookedMethod(MethodHookParam param) {
            if (scope.active() && enabled.getAsBoolean()) param.setResult(param.args[0]);
        }
    }
}
