package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationRoundTest {

    @Test public void recalledReplyLeavesNoStaleRoundId() {
        ConversationRound round = new ConversationRound();
        round.incoming(100);
        round.reply("before"); round.appReply(7, 150); round.reply("after");
        round.forgetReply("before");
        assertEquals(0, round.repliesBeforeAppReply);
        round.incoming(200);
        assertFalse(round.activeReplies.contains("before"));
        assertTrue(round.activeReplies.contains("after"));
        round.forgetReply("after");
        assertTrue(round.activeReplies.isEmpty());
    }
    @Test public void replyWaitsForNewIncomingBeforeRemovingOldPeers() {
        ConversationRound round = new ConversationRound();
        round.incoming(100);
        round.reply("B");
        assertTrue(round.retain(false, null, 100, -1));
        assertTrue(round.incoming(200));
        assertFalse(round.retain(false, null, 100, -1));
        assertTrue(round.retain(true, "B", 150, -1));
        assertTrue(round.retain(false, null, 200, -1));
        assertFalse(round.incoming(300));
        assertTrue(round.retain(false, null, 200, -1));
    }
    @Test public void consecutiveRepliesFormOneBlockAndNextRoundReplacesIt() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.reply("B1"); round.reply("B2"); round.incoming(200);
        assertTrue(round.retain(true, "B1", 110, -1));
        assertTrue(round.retain(true, "B2", 120, -1));
        round.incoming(300); round.reply("E");
        assertTrue(round.retain(true, "B1", 110, -1));
        assertTrue(round.retain(true, "E", 310, -1));
        round.incoming(400);
        assertFalse(round.retain(true, "B1", 110, -1));
        assertFalse(round.retain(false, null, 300, -1));
        assertTrue(round.retain(true, "E", 310, -1));
    }
    @Test public void duplicateStaleOrUndatedEventsDoNotAdvance() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.reply("B");
        for (long time : new long[]{0, -1, 90, 100}) assertFalse(round.incoming(time));
        assertEquals(0, round.cutoff);
        assertEquals(1, round.pendingReplies.size());
        assertTrue(round.incoming(101));
    }
    @Test public void duplicateReplyCallbackIsIgnoredBeforeAndAfterTransition() {
        ConversationRound round = new ConversationRound();
        round.incoming(100);
        assertTrue(round.reply("B")); assertFalse(round.reply("B"));
        round.incoming(200);
        assertFalse(round.reply("B"));
        assertTrue(round.pendingReplies.isEmpty());
    }
    @Test public void undatedMessageBelongsOnlyToItsRecordedRound() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.reply("B"); round.incoming(200);
        assertFalse(round.retain(false, null, 0, -1));
        assertTrue(round.retain(false, null, 0, 100));
        round.reply("E"); round.incoming(300);
        assertFalse(round.retain(false, null, 0, 100));
    }
    @Test public void stateIsBoundedAndNewConversationStartsEmpty() {
        ConversationRound round = new ConversationRound();
        for (int i = 0; i < 100; i++) round.reply("r" + i);
        assertEquals(25, round.pendingReplies.size());
        ConversationRound reset = new ConversationRound();
        assertEquals(0, reset.cutoff);
        assertTrue(reset.pendingReplies.isEmpty());
    }

    @Test public void appReplyWaitsForNextPeerAndDoesNotKeepOldHistory() {
        ConversationRound round = new ConversationRound();
        round.incoming(100);
        round.reply("old-notification-reply");
        assertTrue(round.appReply(10, 150));
        assertFalse(round.appReply(10, 150));
        assertFalse(round.incoming(100));
        assertTrue(round.retain(false, null, 100, -1));
        assertTrue(round.incoming(200));
        assertFalse(round.retain(false, null, 100, -1));
        assertFalse(round.retain(true, "old-notification-reply", 120, -1));
        assertTrue(round.retain(false, null, 200, -1));
        assertFalse(round.pendingAppReply);
    }

    @Test public void notificationReplyAfterAppReplyStaysInNewRound() {
        ConversationRound round = new ConversationRound();
        round.incoming(100);
        round.reply("before-app");
        assertTrue(round.appReply(10, 150));
        round.reply("after-app");
        assertTrue(round.incoming(200));
        assertFalse(round.retain(true, "before-app", 120, -1));
        assertTrue(round.retain(true, "after-app", 160, -1));
        assertFalse(round.retain(false, null, 100, -1));
    }

    @Test public void repeatedAndLateAppEventsCannotResetANewerRound() {
        ConversationRound round = new ConversationRound();
        round.incoming(100);
        assertTrue(round.appReply(10, 150));
        assertTrue(round.appReply(11, 160));
        assertTrue(round.incoming(200));
        assertFalse(round.appReply(10, 150));
        assertFalse(round.appReply(12, 190));
        assertFalse(round.incoming(300));
        assertTrue(round.appReply(13, 310));
        assertTrue(round.incoming(400));
    }

    @Test public void appReplyCanEndAnUndatedHistory() {
        ConversationRound round = new ConversationRound();
        assertTrue(round.appReply(10, 150));
        assertTrue(round.incoming(200));
        assertEquals(1, round.cutoff);
        assertFalse(round.retain(false, null, 0, -1));
        assertTrue(round.retain(false, null, 200, -1));
    }
}
