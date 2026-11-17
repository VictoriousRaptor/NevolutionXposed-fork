package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;

/** Replaces only an identified message's text, retaining every other Bundle field. */
final class VoiceNotificationText {
    static final String PLAIN = "nevo.voice.transcribed";
    private static final String ORIGINAL = "nevo.voice.original";

    static boolean apply(Notification notification, long serverId, String transcript) {
        if (serverId <= 0 || transcript == null || transcript.isEmpty()) return false;
        long[] recalls = notification.extras.getLongArray(NotificationMessages.RECALLED_IDS);
        if (recalls != null) for (long recalled : recalls) if (recalled == serverId) return false;
        Parcelable[] messages = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages == null) return false;
        Parcelable[] result = messages.clone();
        boolean changed = false;
        for (int i = 0; i < messages.length; i++) {
            if (!(messages[i] instanceof Bundle)) continue;
            Bundle source = (Bundle) messages[i];
            Bundle metadata = source.getBundle("extras");
            if (metadata == null || metadata.getLong(NotificationMessages.SERVER_ID) != serverId
                    || metadata.containsKey(NotificationMessages.REPLY_ID)) continue;
            CharSequence original = metadata.getCharSequence(ORIGINAL);
            if (original == null) original = NotificationMarkdown.originalMessageText(source);
            if (original == null) continue;
            Bundle copy = new Bundle(source), extra = new Bundle(metadata);
            String text = original.toString() + "\n" + VoiceTranscriptionQueue.bounded(transcript);
            extra.putCharSequence(ORIGINAL, original);
            extra.putBoolean(PLAIN, true);
            extra.remove(NotificationMarkdown.RAW_TEXT);
            extra.remove(NotificationMarkdown.OWNER);
            copy.putBundle("extras", extra);
            copy.putCharSequence("text", text);
            result[i] = copy;
            changed = true;
            if (notification.extras.getLong(NotificationMessages.SERVER_ID) == serverId) {
                notification.extras.putCharSequence(Notification.EXTRA_TEXT, text);
                notification.extras.remove(NotificationMarkdown.RAW_TEXT);
                notification.extras.putBoolean(PLAIN, true);
            }
        }
        if (changed) notification.extras.putParcelableArray(Notification.EXTRA_MESSAGES, result);
        return changed;
    }
    private VoiceNotificationText() {}
}
