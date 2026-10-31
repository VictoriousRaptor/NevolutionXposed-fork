package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Instrumentation;
import android.app.Person;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import com.oasisfeng.nevo.sdk.Decorating;

import java.util.concurrent.atomic.AtomicReference;

/** Synthetic framework checks only: never post a notification or contact a device app. */
final class NotificationAppearanceInstrumentation {
    static void collapsedAppNameOnly(Instrumentation instrumentation) {
        Context context = instrumentation.getTargetContext();
        Notification notice = builder(context).setStyle(style()).build();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                RemoteViews compact = Notification.Builder.recoverBuilder(context, notice).createContentView();
                RemoteViews hidden = WeChatNotificationAppearance.hideCollapsedAppName(compact, context.getResources());
                check(hidden != compact, "collapsed template was mutated in place");
                View root = hidden.apply(context, new FrameLayout(context));
                int appName = context.getResources().getIdentifier("app_name_text", "id", "android");
                View label = root.findViewById(appName);
                if (label != null) check(label.getVisibility() == View.GONE, "collapsed application label remained visible");
                compact.apply(context, new FrameLayout(context));
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Collapsed notification rendering failed", failure.get());
    }

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

        RemoteViews nativeCard = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
        notice.contentView = nativeCard;
        notice.bigContentView = nativeCard;
        notice.headsUpContentView = nativeCard;
        WeChatNotificationAppearance.clearNativeContentViews(notice);
        check(notice.contentView == null && notice.bigContentView == null && notice.headsUpContentView == null,
                "native card views blocked the conversation template");

        CharSequence originalTitle = notice.extras.getCharSequence(Notification.EXTRA_TITLE);
        Bitmap image = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888);
        Notification preview = ImagePreviewLoader.buildPreviewNotification(context, context.getApplicationInfo(), notice, image, 77);
        check(WeChatNotificationAppearance.owned("com.tencent.mm", preview), "preview lost appearance owner");
        check(ImagePreviewLoader.isPreview(preview), "preview state lost");
        check(android.text.TextUtils.equals(originalTitle, preview.extras.getCharSequence(Notification.EXTRA_TITLE)), "title changed");
        check("android.app.Notification$MessagingStyle".equals(preview.extras.getString(Notification.EXTRA_TEMPLATE))
                && preview.bigContentView == null, "private image left the conversation template");

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

    static void privateTextReplacesNativeViewsFromFirstMessage(Context context) {
        final int id = 983;
        WeChatDecorator.Local local = new WeChatDecorator.Local("WeChatDecorator");
        local.onCreate(context.getSharedPreferences("identity-test", Context.MODE_PRIVATE));
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            Icon avatar = Icon.createWithBitmap(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888));
            Notification first = privateText(context, "A", 100, avatar);
            RemoteViews nativeCard = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
            first.contentView = nativeCard;
            first.bigContentView = nativeCard;
            first.headsUpContentView = nativeCard;
            check(local.apply(manager, null, id, first) == Decorating.Processed, "first private message was not rebuilt");
            check("android.app.Notification$MessagingStyle".equals(first.extras.getString(Notification.EXTRA_TEMPLATE)),
                    "first private message lost MessagingStyle");
            check(!first.extras.getBoolean("android.isGroupConversation"), "first private message became a group");
            check(first.getLargeIcon() != null, "first private message lost its avatar");
            check(first.contentView == null && first.bigContentView == null && first.headsUpContentView == null,
                    "first private message kept WeChat's native card over MessagingStyle");
            RemoteViews firstSystemView = Notification.Builder.recoverBuilder(context, first).createContentView();
            check(firstSystemView != null && firstSystemView.getLayoutId() != nativeCard.getLayoutId(),
                    "first private message still selected WeChat's native card");
            checkPrivateTitle(context, first, "A");

            Notification second = privateText(context, "B", 200, avatar);
            second.contentView = nativeCard;
            second.bigContentView = nativeCard;
            second.headsUpContentView = nativeCard;
            check(local.apply(manager, null, id, second) == Decorating.Processed, "next private message was not rebuilt");
            check(second.contentView == null && second.bigContentView == null && second.headsUpContentView == null,
                    "next private message did not use the same system layout");
            check(!second.extras.getBoolean("android.isGroupConversation") && second.getLargeIcon() != null,
                    "next private message changed conversation presentation");
            RemoteViews secondSystemView = Notification.Builder.recoverBuilder(context, second).createContentView();
            check(secondSystemView != null && secondSystemView.getLayoutId() == firstSystemView.getLayoutId(),
                    "private message layout changed on its next update");
            checkPrivateTitle(context, second, "B");

            Notification rebuilt = Notification.Builder.recoverBuilder(context, second).build();
            check(local.apply(manager, null, id, rebuilt) == Decorating.Processed, "stored private message was not rebuilt");
            checkPrivateTitle(context, rebuilt, "B");
            Bitmap image = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888);
            Notification preview = ImagePreviewLoader.buildPreviewNotification(context, context.getApplicationInfo(), rebuilt, image, 79);
            checkPrivateTitle(context, preview, "B");

            Notification group = new Notification.Builder(context, "identity-test")
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Synthetic group")
                    .setContentText("Synthetic peer: Group message").setTicker("Synthetic peer: Group message")
                    .setLargeIcon(avatar).setWhen(300).build();
            group.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
            group.extras.putBoolean("android.isGroupConversation", true);
            check(local.apply(manager, null, id + 1, group) == Decorating.Processed, "group message was not rebuilt");
            Notification groupRebuilt = Notification.Builder.recoverBuilder(context, group).build();
            androidx.core.app.NotificationCompat.MessagingStyle groupStyle =
                    androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(groupRebuilt);
            check(groupStyle != null && groupStyle.isGroupConversation()
                    && "Synthetic group".contentEquals(groupStyle.getConversationTitle()), "group title was removed");
            check("Synthetic peer".contentEquals(groupStyle.getMessages().get(0).getPerson().getName()),
                    "group sender identity changed");

            Notification replyOnly = privateText(context, "Incoming", 400, avatar);
            replyOnly.extras.putLong(NotificationMessages.SERVER_ID, 3);
            check(local.apply(manager, null, id + 2, replyOnly) == Decorating.Processed, "reply fixture was not rebuilt");
            NotificationMessages.recordReply(replyOnly, "Synthetic reply", 410, "synthetic-title-reply");
            NotificationMessages.markRecall(replyOnly, 3);
            check(local.apply(manager, null, id + 2, replyOnly) == Decorating.Processed, "self-only private message was not rebuilt");
            Notification replyRebuilt = Notification.Builder.recoverBuilder(context, replyOnly).build();
            androidx.core.app.NotificationCompat.MessagingStyle replyStyle =
                    androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(replyRebuilt);
            check(replyStyle != null && !replyStyle.isGroupConversation()
                    && "Alice".contentEquals(replyStyle.getConversationTitle()), "self-only conversation lost its contact title");
            check(replyRebuilt.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("Alice"),
                    "self-only compact title lost the contact");
            check(replyStyle.getMessages().size() == 1 && replyStyle.getMessages().get(0).getPerson() == null
                    && "Synthetic reply".contentEquals(replyStyle.getMessages().get(0).getText()),
                    "self-only message gained a fake peer or restored the recalled message");
        } finally { local.onDestroy(); }
    }

    private static void checkPrivateTitle(Context context, Notification notification, String text) {
        Notification rebuilt = Notification.Builder.recoverBuilder(context, notification).build();
        check("Alice".contentEquals(rebuilt.extras.getCharSequence(Notification.EXTRA_TITLE)),
                "private compact title repeated the contact name");
        check(text.contentEquals(rebuilt.extras.getCharSequence(Notification.EXTRA_TEXT)),
                "private compact body changed");
        androidx.core.app.NotificationCompat.MessagingStyle style =
                androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(rebuilt);
        check(style != null && !style.isGroupConversation(), "private message became a group");
        for (androidx.core.app.NotificationCompat.MessagingStyle.Message message : style.getMessages())
            check(message.getPerson() != null && "Alice".contentEquals(message.getPerson().getName()),
                    "private sender identity was removed to hide its name");
    }

    private static Notification privateText(Context context, String text, long when, Icon avatar) {
        return new Notification.Builder(context, "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Alice").setContentText(text).setTicker("Alice: " + text)
                .setLargeIcon(avatar).setWhen(when).build();
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
