package com.oasisfeng.nevo.decorators.wechat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.view.View;
import android.widget.RemoteViews;

import com.oasisfeng.nevo.xposed.R;

import static com.oasisfeng.nevo.sdk.NevoDecoratorService.TEMPLATE_MESSAGING;

/** Presentation metadata is separate from transcript identity and reply eligibility. */
final class WeChatNotificationAppearance {
    static final String OWNER = "nevo.wechat.appearance";

    static void mark(Notification notification) {
        if (WeChatMessage.isChat(notification.tickerText) || notification.extras.getBoolean(NotificationMessages.STORED))
            notification.extras.putString(OWNER, WeChatDecorator.WECHAT_PACKAGE);
    }

    static boolean owned(String packageName, Notification notification) {
        return owns(packageName, notification.extras.getString(OWNER))
                && (notification.flags & Notification.FLAG_GROUP_SUMMARY) == 0;
    }

    static boolean owns(String packageName, String owner) {
        return WeChatDecorator.WECHAT_PACKAGE.equals(packageName) && WeChatDecorator.WECHAT_PACKAGE.equals(owner);
    }

    @SuppressLint("DiscouragedApi") // Framework notification view IDs are private and vary across system layouts.
    static RemoteViews hideCollapsedAppName(RemoteViews original, Resources resources) {
        int appName = resources.getIdentifier("app_name_text", "id", "android");
        if (appName == 0) return original;
        RemoteViews result = original.clone();
        result.setViewVisibility(appName, View.GONE);
        for (String name : new String[]{"app_name_divider", "app_name_text_divider"}) {
            int divider = resources.getIdentifier(name, "id", "android");
            if (divider != 0) result.setViewVisibility(divider, View.GONE);
        }
        return result;
    }

    static void clearNativeContentViews(Notification notification) {
        notification.contentView = null;
        notification.bigContentView = null;
        notification.headsUpContentView = null;
    }

    static void useNativeAvatar(NotificationAppearanceHookProfile.Avatar profile, Object style, Notification notification)
            throws IllegalAccessException {
        if (!TEMPLATE_MESSAGING.equals(notification.extras.getString(Notification.EXTRA_TEMPLATE))
                || notification.extras.getInt(ConversationClassification.TYPE) == ConversationTypePolicy.BOT) return;
        Icon icon = notification.getLargeIcon();
        if (icon == null) return;
        // ConversationLayout supplies the app badge. Use WeChat's icon instead of a last-sender fallback.
        profile.shortcutIcon.set(style, icon);
        if (profile.conversationType.getInt(style) == 0) profile.conversationType.setInt(style, 1);
    }

    static boolean isMessaging(Notification notification) {
        return TEMPLATE_MESSAGING.equals(notification.extras.getString(Notification.EXTRA_TEMPLATE));
    }

    static boolean isGroupMessaging(Notification notification) {
        return TEMPLATE_MESSAGING.equals(notification.extras.getString(Notification.EXTRA_TEMPLATE))
                && notification.extras.getBoolean("android.isGroupConversation");
    }

    @SuppressLint({"BlockedPrivateApi", "DiscouragedApi"}) // Optional Xposed renderer uses private framework APIs and view IDs.
    static RemoteViews withImagePreview(RemoteViews original, Resources resources,
                                             ApplicationInfo moduleInfo, Notification notification)
            throws ReflectiveOperationException {
        if (!isMessaging(notification)) return original;
        int column = resources.getIdentifier("notification_main_column", "id", "android");
        if (column == 0) return original;
        RemoteViews result = original.clone();
        // SystemUI may reapply onto the previous view tree: remove our old image before adding one.
        java.lang.reflect.Method remove = RemoteViews.class.getDeclaredMethod("removeFromParent", int.class);
        if (remove.getReturnType() != void.class || java.lang.reflect.Modifier.isStatic(remove.getModifiers()))
            throw new NoSuchMethodException("Unsupported RemoteViews.removeFromParent signature");
        remove.setAccessible(true);
        remove.invoke(result, R.id.wechat_preview_image);
        Bitmap bitmap = ImagePreviewLoader.isPreview(notification)
                ? notification.extras.getParcelable(Notification.EXTRA_PICTURE) : null;
        if (bitmap == null) return result;
        RemoteViews image = ImagePreviewRemoteViews.create(moduleInfo, R.layout.wechat_group_image_preview);
        image.setImageViewBitmap(R.id.wechat_preview_image, bitmap);
        // Keep the system's conversation title, sender rows, avatars and actions; append only the picture.
        result.addView(column, image);
        return result;
    }

    private WeChatNotificationAppearance() {}
}
