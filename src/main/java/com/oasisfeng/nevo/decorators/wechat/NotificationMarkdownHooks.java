package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;

import java.lang.reflect.Method;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Preserve module-owned code spans in the existing WeChat and SystemUI scopes. */
public final class NotificationMarkdownHooks {
    private static final String TAG = "WeChat.Markdown";
    private static boolean installed;

    private NotificationMarkdownHooks() {}

    public static synchronized void install(ClassLoader loader, BooleanSupplier enabled, String process,
                                            Predicate<Executable> deoptimize) {
        if (installed || (!WeChatDecorator.WECHAT_PACKAGE.equals(process) && !"com.android.systemui".equals(process))) return;
        try {
            Class<?> type = Class.forName("android.app.Notification$MessagingStyle$Message", false, loader);
            Method cleanup = type.getDeclaredMethod("stripNonStyleSpans", CharSequence.class);
            if (cleanup.getReturnType() != CharSequence.class || Modifier.isStatic(cleanup.getModifiers()))
                throw new NoSuchMethodException("Unsupported message cleanup signature");
            cleanup.setAccessible(true);
            Class<?> style = Class.forName("android.app.Notification$MessagingStyle", false, loader);
            // Non-SDK access is intentional inside LSPosed-injected processes, with signature checks
            // and an unavailable fallback below. This lookup never runs in the companion app.
            @SuppressLint("BlockedPrivateApi")
            Method messagingView = style.getDeclaredMethod("makeMessagingView", int.class);
            // Boot-image callers can contain inlined cleanup code and bypass the private hook.
            Method[] callers = {
                    type.getDeclaredMethod("ensureColorContrastOrStripStyling", int.class),
                    style.getDeclaredMethod("getBundleArrayForMessages", List.class, boolean.class, int.class),
                    style.getDeclaredMethod("addExtras", Bundle.class, boolean.class, int.class),
                    messagingView
            };
            Class<?>[] returns = { void.class, Bundle[].class, void.class, RemoteViews.class };
            for (int i = 0; i < callers.length; i++) {
                if (callers[i].getReturnType() != returns[i] || Modifier.isStatic(callers[i].getModifiers()) != (i == 1))
                    throw new NoSuchMethodException("Unsupported cleanup caller signature");
            }
            for (Method caller : callers) {
                caller.setAccessible(true);
                if (!deoptimize.test(caller)) throw new IllegalStateException("Cleanup caller deoptimization failed");
            }
            AtomicBoolean reportedRestoration = new AtomicBoolean();
            XposedBridge.hookMethod(cleanup, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    try {
                        if (!enabled.getAsBoolean()) return;
                        Notification.MessagingStyle.Message message = (Notification.MessagingStyle.Message) param.thisObject;
                        CharSequence cleaned = (CharSequence) param.getResult();
                        CharSequence restored = NotificationMarkdown.restoreCodeStyles(cleaned, message.getExtras());
                        if (restored == cleaned) return;
                        param.setResult(restored);
                        if (reportedRestoration.compareAndSet(false, true))
                            Log.i(TAG, "source=code_styles_restored process=" + process);
                        else if (BuildConfig.DEBUG) Log.d(TAG, "source=code_styles_restored process=" + process);
                    } catch (Throwable failure) {
                        XposedBridge.rethrowFrameworkError(failure);
                        Log.w(TAG, "source=code_styles_fallback type=" + failure.getClass().getSimpleName());
                    }
                }
            });
            installed = true;
            Log.i(TAG, "source=code_styles_hook_ready process=" + process);
        } catch (Throwable failure) {
            XposedBridge.rethrowFrameworkError(failure);
            Log.w(TAG, "source=code_styles_hook_unavailable process=" + process + " type=" + failure.getClass().getSimpleName());
        }
    }
}
