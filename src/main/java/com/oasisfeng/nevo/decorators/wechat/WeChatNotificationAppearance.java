package com.oasisfeng.nevo.decorators.wechat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

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

    @SuppressLint("DiscouragedApi") // Only the two verified framework compact heads-up templates are supported.
    static RemoteViews privateCompactHeadsUp(RemoteViews original, Resources resources, Notification notification) {
        if (!isMessaging(notification) || !notification.extras.getBoolean(NotificationMessages.STORED)
                || notification.fullScreenIntent != null
                || notification.extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION)
                || notification.extras.getInt(ConversationClassification.TYPE) == ConversationTypePolicy.BOT)
            return original;
        int layout = original.getLayoutId();
        int messagingLayout = resources.getIdentifier("notification_template_material_messaging_compact_heads_up", "layout", "android");
        int baseLayout = resources.getIdentifier("notification_template_material_compact_heads_up_base", "layout", "android");
        if (layout == 0 || (layout != messagingLayout && layout != baseLayout)) return original;

        NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (style == null || style.isGroupConversation() || style.getMessages().isEmpty()) return original;
        // Match the framework's compact heads-up choice: last incoming, or last reply if no incoming remains.
        java.util.List<NotificationCompat.MessagingStyle.Message> messages = style.getMessages();
        NotificationCompat.MessagingStyle.Message message = messages.get(messages.size() - 1);
        for (int i = messages.size() - 1; i >= 0; i--) {
            NotificationCompat.MessagingStyle.Message candidate = messages.get(i);
            if (candidate.getPerson() != null && !TextUtils.isEmpty(candidate.getPerson().getName())) {
                message = candidate;
                break;
            }
        }
        CharSequence peer = notification.extras.getCharSequence(NotificationMessages.TITLE);
        if (TextUtils.isEmpty(peer) && message.getPerson() != null) peer = message.getPerson().getName();
        if (TextUtils.isEmpty(peer) || TextUtils.isEmpty(message.getText())) return original;
        int title = resources.getIdentifier("title", "id", "android");
        int body = resources.getIdentifier("header_text_secondary", "id", "android");
        int titleFormat = resources.getIdentifier("notification_messaging_title_template", "string", "android");
        if (title == 0 || body == 0 || titleFormat == 0) return original;

        if (com.oasisfeng.nevo.xposed.BuildConfig.DEBUG) android.util.Log.i("WeChat.Appearance",
                "source=compact_payload messages=" + messages.size() + " incoming=" + (message.getPerson() != null)
                        + " bodyStartsWithPeer=" + message.getText().toString().startsWith(peer.toString())
                        + " conversationTitle=" + !TextUtils.isEmpty(style.getConversationTitle()));

        RemoteViews result = original.clone();
        android.text.BidiFormatter bidi = android.text.BidiFormatter.getInstance();
        result.setTextViewText(title, resources.getString(titleFormat, bidi.unicodeWrap(peer), ""));
        result.setViewVisibility(title, View.VISIBLE);
        result.setTextViewText(body, message.getText());
        result.setViewVisibility(body, View.VISIBLE);
        // Compact templates put these header fields on the same line. Keep just the title and body.
        for (String name : new String[]{"app_name_text", "header_text", "app_name_divider",
                "header_text_divider", "header_text_secondary_divider"}) {
            int id = resources.getIdentifier(name, "id", "android");
            if (id != 0) result.setViewVisibility(id, View.GONE);
        }
        return result;
    }

    static void useNativeAvatar(NotificationAppearanceHookProfile.Avatar profile, Object style, Notification notification)
            throws IllegalAccessException {
        if (!TEMPLATE_MESSAGING.equals(notification.extras.getString(Notification.EXTRA_TEMPLATE))
                || notification.extras.getInt(ConversationClassification.TYPE) == ConversationTypePolicy.BOT) return;
        Icon icon = notification.getLargeIcon();
        boolean normalizedPrivate = notification.extras.getBoolean(NotificationMessages.STORED)
                && notification.extras.containsKey(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION)
                && !notification.extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION);
        if (icon == null && !normalizedPrivate) return;
        // ConversationLayout supplies the app badge. Use WeChat's icon instead of a last-sender fallback.
        if (icon != null) profile.shortcutIcon.set(style, icon);
        // Per-contact avatar loading must not select the legacy per-sender layout for private messages.
        if (profile.conversationType.getInt(style) == 0) profile.conversationType.setInt(style, 1);
    }

    static boolean isMessaging(Notification notification) {
        return TEMPLATE_MESSAGING.equals(notification.extras.getString(Notification.EXTRA_TEMPLATE));
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
        boolean trackedImage = ImagePreviewLoader.isTracked(notification)
                && ImagePreviewPresentation.hasIncomingPlaceholder(notification.extras);
        if (trackedImage && !ImagePreviewPresentation.disallowReapply(result)) return result;
        if (!ImagePreviewLoader.isPreview(notification)) return result;
        Bitmap bitmap = notification.extras.getParcelable(Notification.EXTRA_PICTURE);
        Icon pictureIcon = notification.extras.getParcelable(Notification.EXTRA_PICTURE_ICON);
        if (bitmap == null && pictureIcon == null) return result;
        int messagingRoot = resources.getIdentifier("status_bar_latest_event_content", "id", "android");
        if (!ImagePreviewPresentation.supportsMessageData(resources, original.getLayoutId(), messagingRoot)) return result;
        Bundle display = ImagePreviewPresentation.withoutPlaceholder(notification.extras,
                notification.extras.getLong(ImagePreviewLoader.IMAGE_MESSAGE_ID));
        if (display == null) return result;
        RemoteViews image = ImagePreviewRemoteViews.create(moduleInfo, R.layout.wechat_group_image_preview);
        // NotificationManager may offload the bitmap and retain only a URI-backed picture icon.
        if (bitmap != null) image.setImageViewBitmap(R.id.wechat_preview_image, bitmap);
        else image.setImageViewIcon(R.id.wechat_preview_image, pictureIcon);
        // The 2025 MessagingStyle body is inset from the card edge, but children appended here are not.
        // Use the framework's own content inset so OEM dimensions and RTL follow the surrounding message.
        int conversation = resources.getIdentifier("notification_2025_template_expanded_conversation", "layout", "android");
        int messaging = resources.getIdentifier("notification_2025_template_expanded_messaging", "layout", "android");
        if (original.getLayoutId() == conversation || original.getLayoutId() == messaging) {
            int insetId = resources.getIdentifier("notification_2025_content_margin_start", "dimen", "android");
            if (insetId != 0) {
                int inset = resources.getDimensionPixelSize(insetId);
                boolean rtl = resources.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                image.setViewPadding(R.id.wechat_preview_image, rtl ? 0 : inset, 0, rtl ? inset : 0, 0);
            }
        }
        // Apply text replacement and picture addition together; never alter the source notification while a worker resolves.
        result.setBundle(messagingRoot, "setData", display);
        result.addView(column, image);
        return result;
    }

    private WeChatNotificationAppearance() {}
}
