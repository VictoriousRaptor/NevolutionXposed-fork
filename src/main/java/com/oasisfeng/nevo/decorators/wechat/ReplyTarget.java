package com.oasisfeng.nevo.decorators.wechat;

import android.app.PendingIntent;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;

import java.util.List;

/** Notification generation and native read target captured for one reply operation. */
record ReplyTarget(long token, Conversation conversation, List<String> pendingReplies, PendingIntent readAction) {}
