package com.oasisfeng.nevo.decorators.wechat;

import java.util.ArrayList;
import java.util.List;

/** A notification reply starts a pending round; only a strictly newer peer event commits it. */
final class ConversationRound {
    long latestPeerTime;
    long cutoff;
    final List<String> activeReplies = new ArrayList<>();
    final List<String> pendingReplies = new ArrayList<>();

    boolean reply(String id) {
        if (activeReplies.contains(id) || pendingReplies.contains(id)) return false;
        pendingReplies.add(id);
        if (pendingReplies.size() > 25) pendingReplies.remove(0);
        return true;
    }

    boolean incoming(long time) {
        if (time <= 0 || time <= latestPeerTime) return false;
        boolean advanced = !pendingReplies.isEmpty() && latestPeerTime > 0;
        if (advanced) {
            cutoff = latestPeerTime;
            activeReplies.clear();
            activeReplies.addAll(pendingReplies);
            pendingReplies.clear();
        }
        latestPeerTime = time;
        return advanced;
    }

    boolean retain(boolean self, String replyId, long time, long messageRound) {
        if (cutoff == 0) return true;
        return self ? activeReplies.contains(replyId) || pendingReplies.contains(replyId) : time > cutoff || messageRound == cutoff;
    }
}
