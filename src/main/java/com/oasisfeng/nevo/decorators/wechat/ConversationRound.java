package com.oasisfeng.nevo.decorators.wechat;

import java.util.ArrayList;
import java.util.List;

/** A notification reply starts a pending round; only a strictly newer peer event commits it. */
final class ConversationRound {
    long latestPeerTime;
    long cutoff;
    long lastAppReplyTime;
    boolean pendingAppReply;
    int repliesBeforeAppReply;
    final List<String> activeReplies = new ArrayList<>();
    final List<String> pendingReplies = new ArrayList<>();
    final List<Long> recentAppReplyIds = new ArrayList<>();

    boolean reply(String id) {
        if (activeReplies.contains(id) || pendingReplies.contains(id)) return false;
        pendingReplies.add(id);
        if (pendingReplies.size() > 25) {
            pendingReplies.remove(0);
            if (pendingAppReply && repliesBeforeAppReply > 0) repliesBeforeAppReply--;
        }
        return true;
    }

    void forgetReply(String id) {
        activeReplies.remove(id);
        int index = pendingReplies.indexOf(id);
        if (index >= 0) {
            pendingReplies.remove(index);
            if (pendingAppReply && index < repliesBeforeAppReply) repliesBeforeAppReply--;
        }
    }

    boolean appReply(long messageId, long time) {
        if (messageId <= 0 || time <= latestPeerTime || time < lastAppReplyTime
                || recentAppReplyIds.contains(messageId)) return false;
        lastAppReplyTime = time;
        recentAppReplyIds.add(messageId);
        if (recentAppReplyIds.size() > 16) recentAppReplyIds.remove(0);
        pendingAppReply = true;
        // Replies after this app send still belong to the next visible round.
        repliesBeforeAppReply = pendingReplies.size();
        return true;
    }

    boolean incoming(long time) {
        if (time <= 0 || time <= latestPeerTime) return false;
        boolean advanced = pendingAppReply || (!pendingReplies.isEmpty() && latestPeerTime > 0);
        if (advanced) {
            cutoff = Math.max(1, latestPeerTime);
            activeReplies.clear();
            activeReplies.addAll(pendingReplies.subList(
                    pendingAppReply ? Math.min(repliesBeforeAppReply, pendingReplies.size()) : 0, pendingReplies.size()));
            pendingReplies.clear();
            pendingAppReply = false;
            repliesBeforeAppReply = 0;
        }
        latestPeerTime = time;
        return advanced;
    }

    boolean retain(boolean self, String replyId, long time, long messageRound) {
        if (cutoff == 0) return true;
        return self ? activeReplies.contains(replyId) || pendingReplies.contains(replyId) : time > cutoff || messageRound == cutoff;
    }
}
