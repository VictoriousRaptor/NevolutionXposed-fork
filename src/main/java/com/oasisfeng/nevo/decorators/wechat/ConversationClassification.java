package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.os.Bundle;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;
import com.oasisfeng.nevo.xposed.BuildConfig;

/** Reads original platform evidence and stamps only explicit provenance, not inferred layout flags. */
final class ConversationClassification {
    static final String TYPE = "nevo.verifiedType";
    static final String SOURCE = "nevo.typeSource";

    static ConversationTypePolicy.Decision resolve(Conversation cached, Notification n) {
        Bundle extras = n.extras;
        boolean stored = extras.getBoolean(NotificationMessages.STORED);
        Boolean nativeGroup = !stored && "android.app.Notification$MessagingStyle".equals(extras.getString(Notification.EXTRA_TEMPLATE))
                && extras.containsKey(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION)
                ? extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION) : null;
        ConversationTypePolicy.Decision previous = cached.typeEvidence;
        if (previous == null && stored) previous = restore(extras);
        String key = cached.knownKey();
        if (key == null && stored && restore(extras) != null) key = extras.getString(NotificationMessages.KEY);
        ConversationTypePolicy.Decision decision = ConversationTypePolicy.resolve(key, nativeGroup, previous);
        if (key != null) cached.key = key;
        cached.typeEvidence = decision;
        if (BuildConfig.DEBUG) Log.d("WeChat.Identity", "source=classification type=" + decision.type
                + " evidence=" + decision.source + " nativeFlag=" + (nativeGroup != null) + " stored=" + stored);
        return decision;
    }

    static void stamp(Conversation c, Bundle extras) {
        if (c.typeEvidence == null) return;
        extras.putInt(TYPE, c.typeEvidence.type);
        extras.putString(SOURCE, c.typeEvidence.source.name());
    }

    private static ConversationTypePolicy.Decision restore(Bundle extras) {
        int type = extras.getInt(TYPE, -1);
        String source = extras.getString(SOURCE);
        if (type < ConversationTypePolicy.DIRECT || type > ConversationTypePolicy.BOT) return null;
        if (!"TALKER".equals(source) && !"NATIVE_STYLE".equals(source)) return null;
        return new ConversationTypePolicy.Decision(type, ConversationTypePolicy.Source.valueOf(source));
    }
}
