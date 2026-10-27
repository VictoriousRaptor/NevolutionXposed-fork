package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.widget.RemoteViews;

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

    static void useNativeAvatar(NotificationAppearanceHookProfile.Avatar profile, Object style, Notification notification)
            throws IllegalAccessException {
        if (!TEMPLATE_MESSAGING.equals(notification.extras.getString(Notification.EXTRA_TEMPLATE))
                || notification.extras.getBoolean("android.isGroupConversation")
                || notification.extras.getInt(ConversationClassification.TYPE) == ConversationTypePolicy.BOT) return;
        Icon icon = notification.getLargeIcon();
        if (icon == null) return;
        // ConversationLayout supplies the app badge. Use WeChat's icon instead of a last-sender fallback.
        profile.shortcutIcon.set(style, icon);
        if (profile.conversationType.getInt(style) == 0) profile.conversationType.setInt(style, 1);
    }

    static RemoteViews hideAppName(RemoteViews original, Resources resources) {
        int appName = resources.getIdentifier("app_name_text", "id", "android");
        if (appName == 0) return original;
        // Never change an existing cached/custom RemoteViews: toggling off must recover its original actions.
        RemoteViews result = original.clone();
        result.setViewVisibility(appName, android.view.View.GONE);
        for (String name : new String[]{"app_name_divider", "app_name_text_divider"}) {
            int divider = resources.getIdentifier(name, "id", "android");
            if (divider != 0) result.setViewVisibility(divider, android.view.View.GONE);
        }
        return result;
    }

    private WeChatNotificationAppearance() {}
}
