package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.widget.RemoteViews;


/** Synthetic framework checks only: never post a notification or contact a device app. */
final class NotificationAppearanceInstrumentation {
    static void nativeAvatarAndGroupScope(Context context) {
        try {
            NotificationAppearanceHookProfile.Avatar profile = NotificationAppearanceHookProfile.avatar(
                    Notification.MessagingStyle.class, Notification.Style.class, Notification.Builder.class,
                    Context.class, Notification.class, Icon.class, RemoteViews.class);
            Notification.MessagingStyle style = style();
            Icon nativeIcon = Icon.createWithBitmap(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888));
            Notification notice = builder(context).setStyle(style).setLargeIcon(nativeIcon).build();
            nativeIcon = notice.getLargeIcon();
            WeChatNotificationAppearance.useNativeAvatar(profile, style, notice);
            check(profile.shortcutIcon.get(style) == nativeIcon, "native contact icon lost");
            check(profile.conversationType.getInt(style) == 1, "conversation layout not selected");
            profile.conversationType.setInt(style, 2);
            WeChatNotificationAppearance.useNativeAvatar(profile, style, notice);
            check(profile.conversationType.getInt(style) == 2, "important conversation downgraded");

            profile.conversationType.setInt(style, 0);
            profile.shortcutIcon.set(style, null);
            notice.extras.putBoolean("android.isGroupConversation", true);
            WeChatNotificationAppearance.useNativeAvatar(profile, style, notice);
            check(profile.conversationType.getInt(style) == 1 && profile.shortcutIcon.get(style) == nativeIcon,
                    "native group icon and app badge layout not selected");
            profile.shortcutIcon.set(style, null);
            notice.extras.putBoolean("android.isGroupConversation", false);
            notice.extras.putInt(ConversationClassification.TYPE, ConversationTypePolicy.BOT);
            WeChatNotificationAppearance.useNativeAvatar(profile, style, notice);
            check(profile.shortcutIcon.get(style) == null, "bot received private avatar policy");

            Notification withoutIcon = builder(context).setStyle(style()).build();
            Notification.MessagingStyle empty = style();
            WeChatNotificationAppearance.useNativeAvatar(profile, empty, withoutIcon);
            check(profile.conversationType.getInt(empty) == 0, "missing native avatar did not fall back");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    static void ownershipAndPreviewMetadata(Context context) {
        Notification notice = builder(context).setStyle(style()).build();
        check(!WeChatNotificationAppearance.owned("com.tencent.mm", notice), "unmarked notification accepted");
        WeChatNotificationAppearance.mark(notice);
        check(WeChatNotificationAppearance.owned("com.tencent.mm", notice), "message marker missing");
        check(!WeChatNotificationAppearance.owned(context.getPackageName(), notice), "other package accepted");
        notice.flags |= Notification.FLAG_GROUP_SUMMARY;
        check(!WeChatNotificationAppearance.owned("com.tencent.mm", notice), "summary accepted");
        notice.flags &= ~Notification.FLAG_GROUP_SUMMARY;

        CharSequence originalTitle = notice.extras.getCharSequence(Notification.EXTRA_TITLE);
        Bitmap image = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888);
        Notification preview = ImagePreviewLoader.buildPreviewNotification(context, context.getApplicationInfo(), notice, image, 77);
        check(WeChatNotificationAppearance.owned("com.tencent.mm", preview), "preview lost appearance owner");
        check(ImagePreviewLoader.isPreview(preview), "preview state lost");
        check(android.text.TextUtils.equals(originalTitle, preview.extras.getCharSequence(Notification.EXTRA_TITLE)), "title changed");

        Notification group = builder(context).setStyle(style().setGroupConversation(true)).build();
        group.extras.putString(NotificationMessages.TITLE, "Synthetic chat");
        Notification groupPreview = ImagePreviewLoader.buildPreviewNotification(context, context.getApplicationInfo(), group, image, 78);
        check("android.app.Notification$MessagingStyle".equals(groupPreview.extras.getString(Notification.EXTRA_TEMPLATE)),
                "group image replaced the messaging template");
        check(groupPreview.bigContentView == null, "group image installed a custom title layout");
        check(groupPreview.extras.getBoolean("android.isGroupConversation"), "group flag lost");
        androidx.core.app.NotificationCompat.MessagingStyle restored =
                androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(groupPreview);
        check(restored != null && "Synthetic chat".contentEquals(restored.getConversationTitle()), "pure group title lost");
        check(restored.getMessages().size() == 1
                && "Synthetic peer".contentEquals(restored.getMessages().get(0).getPerson().getName()),
                "group sender identity changed");
    }

    private static Notification.Builder builder(Context context) {
        return new Notification.Builder(context, "synthetic-appearance")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Synthetic chat")
                .setContentText("Synthetic message").setTicker("Synthetic peer: Synthetic message");
    }

    private static Notification.MessagingStyle style() {
        return new Notification.MessagingStyle(new Person.Builder().setName("Synthetic self").build())
                .setConversationTitle("Synthetic chat").setGroupConversation(false)
                .addMessage("Synthetic message", 100, new Person.Builder().setName("Synthetic peer").build());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
