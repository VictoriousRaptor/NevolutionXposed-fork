package com.oasisfeng.nevo.decorators.wechat;

import android.app.PendingIntent;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;


/** Notification generation and native read target captured for one reply operation. */
record ReplyTarget(long token, Conversation conversation, PendingIntent readAction) {
    /** A UI acknowledgement may update the ended card, but never a new card or identity. */
    boolean matches(Conversation current, long currentToken, String peerId) {
        return token > 0 && conversation != null && conversation == current && token == currentToken
                && current.notificationPeerId != null && current.notificationPeerId.equals(peerId);
    }
}
