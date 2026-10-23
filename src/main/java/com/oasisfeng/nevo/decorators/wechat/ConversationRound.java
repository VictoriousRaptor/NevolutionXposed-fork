package com.oasisfeng.nevo.decorators.wechat;

import java.util.ArrayList;
import java.util.List;

/** A reply stays pending until a newer peer event or a confirmed newer notification acknowledges it. */
final class ConversationRound {
    long latestPeerTime;
    long cutoff;
    long lastAppReplyTime;
    long appReplyToken;
    boolean pendingAppReply;
    int repliesBeforeAppReply;
    final List<String> activeReplies = new ArrayList<>();
    final List<String> pendingReplies = new ArrayList<>();
    final List<String> recentReplyIds = new ArrayList<>();
    final List<Long> recentAppReplyIds = new ArrayList<>();

    boolean reply(String id) {
        if (activeReplies.contains(id) || pendingReplies.contains(id) || recentReplyIds.contains(id)) return false;
        recentReplyIds.add(id);
        if (recentReplyIds.size() > 25) recentReplyIds.remove(0);
        pendingReplies.add(id);
        if (pendingReplies.size() > 25) {
            pendingReplies.remove(0);
            if (pendingAppReply && repliesBeforeAppReply > 0) repliesBeforeAppReply--;
        }
        return true;
    }

    void forgetReply(String id) {
        activeReplies.remove(id);
        removePendingReply(id);
    }

    private boolean removePendingReply(String id) {
        int index = pendingReplies.indexOf(id);
        if (index < 0) return false;
        pendingReplies.remove(index);
        if (pendingAppReply && index < repliesBeforeAppReply) repliesBeforeAppReply--;
        return true;
    }

    /** A peer notification already arrived while this successful reply callback was pending. */
    void acknowledgeReply(String id, long readPeerTime) {
        if (!removePendingReply(id)) return;
        activeReplies.add(id);
        if (activeReplies.size() > 25) activeReplies.remove(0);
        cutoff = Math.max(cutoff, Math.max(1, readPeerTime));
    }

    /** Generation order also acknowledges an undated peer after an app send. */
    void acknowledgeAppReply(long peerToken) {
        if (!pendingAppReply || appReplyToken == 0 || peerToken <= appReplyToken) return;
        activeReplies.clear();
        discardAppReplyPrefix();
    }

    private void discardAppReplyPrefix() {
        if (!pendingAppReply) return;
        pendingReplies.subList(0, Math.min(repliesBeforeAppReply, pendingReplies.size())).clear();
        pendingAppReply = false;
        repliesBeforeAppReply = 0;
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
            discardAppReplyPrefix();
            activeReplies.addAll(pendingReplies);
            pendingReplies.clear();
        }
        latestPeerTime = time;
        return advanced;
    }

    boolean retain(boolean self, String replyId, long time, long messageRound, boolean showLastReply) {
        if (cutoff == 0) return true;
        return self ? pendingReplies.contains(replyId) || (showLastReply && activeReplies.contains(replyId))
                : time > cutoff || messageRound == cutoff;
    }
}
