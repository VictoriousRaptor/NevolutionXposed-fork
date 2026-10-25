package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;

import java.lang.reflect.Method;
import java.lang.reflect.Executable;
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
            Class<?> style = Class.forName("android.app.Notification$MessagingStyle", false, loader);
            // Non-SDK access is intentional inside LSPosed-injected processes, with signature checks
            // and an unavailable fallback below. This lookup never runs in the companion app.
            NotificationMarkdownHookProfile profile = NotificationMarkdownHookProfile.resolve(
                    type, style, Bundle.class, RemoteViews.class);
            // Boot-image callers can contain inlined cleanup code and bypass the private hook.
            for (Method caller : profile.callers) {
                if (!deoptimize.test(caller)) throw new IllegalStateException("Cleanup caller deoptimization failed");
            }
            AtomicBoolean reportedRestoration = new AtomicBoolean();
            XposedBridge.hookMethod(profile.cleanup, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    try {
                        if (!enabled.getAsBoolean()) return;
                        Notification.MessagingStyle.Message message = (Notification.MessagingStyle.Message) param.thisObject;
                        CharSequence cleaned = profile.textField == null
                                ? (CharSequence) param.getResult() : message.getText();
                        CharSequence restored = NotificationMarkdown.restoreCodeStyles(cleaned, message.getExtras());
                        if (restored == cleaned) return;
                        if (profile.textField == null) param.setResult(restored);
                        else profile.textField.set(message, restored);
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
            Log.i(TAG, "source=code_styles_hook_ready process=" + process
                    + " layout=" + (profile.textField == null ? "return_value" : "message_field"));
        } catch (Throwable failure) {
            XposedBridge.rethrowFrameworkError(failure);
            Log.w(TAG, "source=code_styles_hook_unavailable process=" + process + " type=" + failure.getClass().getSimpleName());
        }
    }
}
