package com.oasisfeng.nevo.decorators.wechat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.RemoteViews;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Render-only message data. The original transcript and its placeholder remain available for fallback. */
@SuppressLint("BlockedPrivateApi") // Optional RemoteViews flag is signature-checked; unavailable runtimes keep the placeholder.
final class ImagePreviewPresentation {
    private static final class Capabilities {
        static final NotificationAppearanceHookProfile.Image IMAGE = NotificationAppearanceHookProfile.image(
                RemoteViews.class, Bundle.class, messageLayout("com.android.internal.widget.ConversationLayout"),
                messageLayout("com.android.internal.widget.MessagingLayout"));
    }

    static NotificationAppearanceHookProfile.Image imageCapabilities() { return Capabilities.IMAGE; }

    private static Class<?> messageLayout(String name) {
        try { return Class.forName(name, false, RemoteViews.class.getClassLoader()); }
        catch (ClassNotFoundException | RuntimeException unavailable) { return null; }
    }

    static Bundle withoutPlaceholder(Bundle source, long imageServerId) {
        Parcelable[] messages = source.getParcelableArray(Notification.EXTRA_MESSAGES);
        List<ImagePlaceholderPolicy.Message> candidates = readMessages(source);
        int target = ImagePlaceholderPolicy.target(candidates, imageServerId);
        if (target < 0) return null;
        Bundle message = new Bundle((Bundle) messages[target]);
        Bundle metadata = message.getBundle("extras");
        metadata = metadata == null ? new Bundle() : new Bundle(metadata);
        metadata.remove(NotificationMarkdown.RAW_TEXT);
        metadata.remove(NotificationMarkdown.OWNER);
        message.putBundle("extras", metadata);
        message.putCharSequence("text", "");
        Parcelable[] displayMessages = messages.clone();
        displayMessages[target] = message;
        Bundle display = new Bundle(source);
        display.putParcelableArray(Notification.EXTRA_MESSAGES, displayMessages);
        // The picture is transported by the appended ImageView, not by a second setData bundle.
        display.remove(Notification.EXTRA_PICTURE);
        display.remove("android.pictureIcon"); // Notification.EXTRA_PICTURE_ICON is API 31; this key is optional metadata.
        return display;
    }

    static boolean hasIncomingPlaceholder(Bundle source) {
        for (ImagePlaceholderPolicy.Message message : readMessages(source))
            if (message.incoming && placeholder(message.text)) return true;
        return false;
    }

    /** Mark the old expanded hierarchy so SystemUI inflates the image update instead of reapplying it. */
    static boolean disallowReapply(RemoteViews views) {
        NotificationAppearanceHookProfile.Reapply capability = imageCapabilities().reapply;
        if (capability == null) return false;
        try {
            capability.addFlags.invoke(views, capability.disallowedFlag);
            return hasReapplyDisallowed(views);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    static boolean hasReapplyDisallowed(RemoteViews views) {
        NotificationAppearanceHookProfile.Reapply capability = imageCapabilities().reapply;
        if (capability == null) return false;
        try {
            return (Boolean) capability.hasFlags.invoke(views, capability.disallowedFlag);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private static List<ImagePlaceholderPolicy.Message> readMessages(Bundle source) {
        List<ImagePlaceholderPolicy.Message> candidates = new ArrayList<>();
        Parcelable[] messages = source.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages == null) return candidates;
        Parcelable user = source.getParcelable("android.messagingUser");
        CharSequence selfName = source.getCharSequence("android.selfDisplayName");
        for (Parcelable value : messages) {
            Bundle message = value instanceof Bundle ? (Bundle) value : null;
            Bundle metadata = message == null ? null : message.getBundle("extras");
            Parcelable senderValue = message == null ? null : message.getParcelable("sender_person");
            CharSequence senderName = message == null ? null : message.getCharSequence("sender");
            boolean incoming = senderValue != null ? user == null || !user.equals(senderValue)
                    : senderName != null && (selfName == null || !selfName.toString().contentEquals(senderName));
            candidates.add(new ImagePlaceholderPolicy.Message(message == null ? null : message.getCharSequence("text"),
                    metadata == null ? 0 : metadata.getLong(NotificationMessages.SERVER_ID), incoming));
        }
        return candidates;
    }

    private static boolean placeholder(CharSequence text) {
        return text != null && ("[图片]".contentEquals(text) || "图片".contentEquals(text));
    }

    static boolean supportsMessageData(Resources resources, int layout, int rootId) {
        if (rootId == 0) return false;
        try (XmlResourceParser parser = resources.getLayout(layout)) {
            int event;
            do { event = parser.next(); } while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT);
            String name = parser.getName();
            if (!("com.android.internal.widget.ConversationLayout".equals(name)
                    || "com.android.internal.widget.MessagingLayout".equals(name))
                    || parser.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "id", 0) != rootId)
                return false;
            NotificationAppearanceHookProfile.Image capabilities = imageCapabilities();
            return "com.android.internal.widget.ConversationLayout".equals(name)
                    ? capabilities.conversationData != null : capabilities.messagingData != null;
        } catch (Resources.NotFoundException | XmlPullParserException | IOException unavailable) {
            return false;
        }
    }

    private ImagePreviewPresentation() {}
}
