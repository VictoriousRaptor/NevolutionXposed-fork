package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.util.Log;
import android.widget.RemoteViews;

import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Framework-only appearance hooks in the existing WeChat/SystemUI scopes. */
public final class NotificationAppearanceHooks {
    private static final String TAG = "WeChat.Appearance";
    private static boolean avatarInstalled, collapsedInstalled, compactHeadsUpInstalled, headerInstalled;

    public static synchronized void install(BooleanSupplier enabled, String process, Predicate<Executable> deoptimize) {
        if (!WeChatDecorator.WECHAT_PACKAGE.equals(process) && !"com.android.systemui".equals(process)) return;
        if (!avatarInstalled) try {
            NotificationAppearanceHookProfile.Avatar profile = NotificationAppearanceHookProfile.avatar(
                    Notification.MessagingStyle.class, Notification.Style.class, Notification.Builder.class,
                    Context.class, Notification.class, Icon.class, RemoteViews.class);
            deoptimizeViewCallers(Notification.MessagingStyle.class, deoptimize);
            XposedBridge.hookMethod(profile.render, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!enabled.getAsBoolean()) return;
                        Object builder = profile.builder.get(param.thisObject);
                        if (builder == null) return;
                        Context context = (Context) profile.context.get(builder);
                        Notification notification = (Notification) profile.notification.get(builder);
                        if (context != null && notification != null
                                && WeChatNotificationAppearance.owned(context.getPackageName(), notification))
                            WeChatNotificationAppearance.useNativeAvatar(profile, param.thisObject, notification);
                    } catch (Throwable failure) { fallback("avatar", failure); }
                }
            });
            avatarInstalled = true;
            Log.i(TAG, "source=avatar_hook_ready process=" + process);
        } catch (Throwable failure) { fallback("avatar_hook_unavailable", failure); }

        if (!collapsedInstalled) try {
            NotificationAppearanceHookProfile.Header profile = NotificationAppearanceHookProfile.collapsed(
                    Notification.Builder.class, Context.class, Notification.class, RemoteViews.class);
            deoptimizeViewCallers(Notification.Builder.class, deoptimize);
            XposedBridge.hookMethod(profile.render, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || !(param.getResult() instanceof RemoteViews)) return;
                    try {
                        if (!enabled.getAsBoolean()) return;
                        Context context = (Context) profile.context.get(param.thisObject);
                        Notification notification = (Notification) profile.notification.get(param.thisObject);
                        if (context == null || notification == null
                                || !WeChatNotificationAppearance.owned(context.getPackageName(), notification)) return;
                        param.setResult(WeChatNotificationAppearance.hideCollapsedAppName(
                                (RemoteViews) param.getResult(), context.getResources()));
                    } catch (Throwable failure) { fallback("collapsed_header", failure); }
                }
            });
            collapsedInstalled = true;
            Log.i(TAG, "source=collapsed_hook_ready process=" + process);
        } catch (Throwable failure) { fallback("collapsed_hook_unavailable", failure); }

        if (!compactHeadsUpInstalled) try {
            NotificationAppearanceHookProfile.Header profile = NotificationAppearanceHookProfile.compactHeadsUp(
                    Notification.Builder.class, Context.class, Notification.class, RemoteViews.class);
            deoptimizeViewCallers(Notification.Builder.class, deoptimize);
            XposedBridge.hookMethod(profile.render, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || !(param.getResult() instanceof RemoteViews)) return;
                    try {
                        if (!enabled.getAsBoolean()) return;
                        Context context = (Context) profile.context.get(param.thisObject);
                        Notification notification = (Notification) profile.notification.get(param.thisObject);
                        if (context == null || notification == null
                                || !WeChatNotificationAppearance.owned(context.getPackageName(), notification)) return;
                        RemoteViews original = (RemoteViews) param.getResult();
                        RemoteViews result = WeChatNotificationAppearance.privateCompactHeadsUp(
                                original, context.getResources(), notification);
                        param.setResult(result);
                        if (BuildConfig.DEBUG) Log.i(TAG, "source=compact_render process=" + process
                                + " changed=" + (result != original) + " layoutId=" + original.getLayoutId()
                                + " messaging=" + WeChatNotificationAppearance.isMessaging(notification)
                                + " stored=" + notification.extras.getBoolean(NotificationMessages.STORED)
                                + " group=" + notification.extras.getBoolean("android.isGroupConversation")
                                + " type=" + notification.extras.getInt(ConversationClassification.TYPE)
                                + " peerTitle=" + (notification.extras.getCharSequence(NotificationMessages.TITLE) != null));
                    } catch (Throwable failure) { fallback("compact_render", failure); }
                }
            });
            compactHeadsUpInstalled = true;
            Log.i(TAG, "source=compact_hook_ready process=" + process);
        } catch (Throwable failure) { fallback("compact_hook_unavailable", failure); }

        if (!headerInstalled) try {
            NotificationAppearanceHookProfile.Header profile = NotificationAppearanceHookProfile.header(
                    Notification.Builder.class, Context.class, Notification.class, RemoteViews.class);
            deoptimizeViewCallers(Notification.Builder.class, deoptimize);
            XposedBridge.hookMethod(profile.render, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || !(param.getResult() instanceof RemoteViews)) return;
                    try {
                        if (!enabled.getAsBoolean()) return;
                        Context context = (Context) profile.context.get(param.thisObject);
                        Notification notification = (Notification) profile.notification.get(param.thisObject);
                        boolean preview = BuildConfig.DEBUG && notification != null && ImagePreviewLoader.isPreview(notification);
                        if (preview) Log.i(TAG, "source=image_render_entry process=" + process
                                + " contextReady=" + (context != null) + " owned="
                                + (context != null && WeChatNotificationAppearance.owned(context.getPackageName(), notification))
                                + " bitmap=" + (notification.extras.getParcelable(Notification.EXTRA_PICTURE) != null)
                                + " pictureIcon=" + (notification.extras.getParcelable(Notification.EXTRA_PICTURE_ICON) != null));
                        if (context == null || notification == null
                                || !WeChatNotificationAppearance.owned(context.getPackageName(), notification)) return;
                        RemoteViews expanded = (RemoteViews) param.getResult();
                        if (WeChatNotificationAppearance.isMessaging(notification)) try {
                            RemoteViews withImage = WeChatNotificationAppearance.withImagePreview(expanded, context.getResources(),
                                    XposedBridge.getModuleApplicationInfo(), notification);
                            if (preview) Log.i(TAG, "source=image_render_result process=" + process
                                    + " viewChanged=" + (withImage != expanded));
                            expanded = withImage;
                        } catch (Throwable failure) {
                            // Keep the original MessagingStyle text ([图片]); do not try another rendering interface.
                            fallback("message_image", failure);
                        }
                        param.setResult(expanded);
                    } catch (Throwable failure) { fallback("header", failure); }
                }
            });
            headerInstalled = true;
            Log.i(TAG, "source=header_hook_ready process=" + process + " method=" + profile.render.getName());
        } catch (Throwable failure) { fallback("header_hook_unavailable", failure); }
    }

    private static void deoptimizeViewCallers(Class<?> type, Predicate<Executable> deoptimize) {
        for (Method method : type.getDeclaredMethods()) {
            String name = method.getName();
            if ((name.startsWith("make") || name.startsWith("create")) && name.endsWith("ContentView")
                    && method.getReturnType() == RemoteViews.class && !deoptimize.test(method))
                throw new IllegalStateException("Notification renderer deoptimization failed");
        }
    }

    private static void fallback(String stage, Throwable failure) {
        XposedBridge.rethrowFrameworkError(failure);
        Log.w(TAG, "source=" + stage + " type=" + failure.getClass().getSimpleName());
    }

    private NotificationAppearanceHooks() {}
}
