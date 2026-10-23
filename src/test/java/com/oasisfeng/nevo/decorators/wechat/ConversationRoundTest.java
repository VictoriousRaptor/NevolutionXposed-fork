package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationRoundTest {

    @Test public void lateReplyCallbackDoesNotMakeTheNextIncomingDeleteItsPeer() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.incoming(200);
        round.reply("B"); round.acknowledgeReply("B", 100);
        assertFalse(round.incoming(300));
        assertTrue(round.retain(false, null, 200, -1, false));
        assertTrue(round.retain(true, "B", 150, -1, true));
        assertFalse(round.retain(true, "B", 150, -1, false));
        assertFalse(round.reply("B"));
    }

    @Test public void newerUndatedPeerCanAcknowledgeTheWholePendingReplyGroup() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.reply("B1");
        assertFalse(round.incoming(0));
        round.reply("B2");
        round.acknowledgeReply("B1", 100); round.acknowledgeReply("B2", 100);
        assertFalse(round.incoming(300));
        assertTrue(round.retain(false, null, 0, round.cutoff, false));
        assertTrue(round.retain(true, "B1", 110, -1, true));
        assertTrue(round.retain(true, "B2", 150, -1, true));
        assertTrue(round.pendingReplies.isEmpty());
    }

    @Test public void hidingLastReplyKeepsPendingReplyAndDeduplication() {
        ConversationRound round = new ConversationRound();
        round.incoming(100);
        round.reply("B");
        assertTrue(round.retain(true, "B", 150, -1, false));
        round.incoming(200);
        assertFalse(round.retain(false, null, 100, -1, false));
        assertFalse(round.retain(true, "B", 150, -1, false));
        assertTrue(round.retain(false, null, 200, -1, false));
        assertFalse(round.reply("B"));
        round.reply("E");
        assertTrue(round.retain(true, "E", 250, -1, false));
        round.incoming(300);
        assertFalse(round.retain(true, "E", 250, -1, false));
    }

    @Test public void displayChoiceIsNotStoredInRoundState() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.reply("B"); round.incoming(200);
        assertFalse(round.retain(true, "B", 150, -1, false));
        assertTrue(round.retain(true, "B", 150, -1, true));
    }

    @Test public void retiredReplyUuidStillRejectsADuplicateCallback() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.reply("B"); round.incoming(200);
        round.forgetReply("B");
        round.reply("E"); round.incoming(300);
        assertFalse(round.reply("B"));
    }

    @Test public void acknowledgmentPreservesTheAppReplyPartition() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.reply("B1"); round.appReply(7, 140); round.reply("B2");
        round.acknowledgeReply("B1", 100); round.acknowledgeReply("B2", 100);
        assertEquals(0, round.repliesBeforeAppReply);
        round.reply("B3");
        assertTrue(round.incoming(300));
        assertTrue(round.retain(true, "B3", 200, -1, true));
    }

    @Test public void undatedPeerAcknowledgesAppBoundaryBeforeLateReply() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.reply("before"); round.appReply(7, 140);
        round.appReplyToken = 10;
        round.reply("after");
        round.acknowledgeAppReply(11);
        round.acknowledgeReply("before", 100); round.acknowledgeReply("after", 100);
        assertFalse(round.pendingAppReply);
        assertFalse(round.activeReplies.contains("before"));
        assertTrue(round.activeReplies.contains("after"));
        assertFalse(round.incoming(300));
        assertTrue(round.retain(true, "after", 150, -1, true));
    }

    @Test public void acknowledgmentDoesNotConsumeAnAppSendAfterThatPeer() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.reply("before"); round.appReply(7, 240);
        round.appReplyToken = 11;
        round.acknowledgeAppReply(11);
        assertTrue(round.pendingAppReply);
        round.acknowledgeReply("before", 100);
        assertTrue(round.incoming(300));
        assertFalse(round.retain(true, "before", 150, -1, true));
    }

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
        assertTrue(round.retain(false, null, 100, -1, true));
        assertTrue(round.incoming(200));
        assertFalse(round.retain(false, null, 100, -1, true));
        assertTrue(round.retain(true, "B", 150, -1, true));
        assertTrue(round.retain(false, null, 200, -1, true));
        assertFalse(round.incoming(300));
        assertTrue(round.retain(false, null, 200, -1, true));
    }
    @Test public void consecutiveRepliesFormOneBlockAndNextRoundReplacesIt() {
        ConversationRound round = new ConversationRound();
        round.incoming(100); round.reply("B1"); round.reply("B2"); round.incoming(200);
        assertTrue(round.retain(true, "B1", 110, -1, true));
        assertTrue(round.retain(true, "B2", 120, -1, true));
        round.incoming(300); round.reply("E");
        assertTrue(round.retain(true, "B1", 110, -1, true));
        assertTrue(round.retain(true, "E", 310, -1, true));
        round.incoming(400);
        assertFalse(round.retain(true, "B1", 110, -1, true));
        assertFalse(round.retain(false, null, 300, -1, true));
        assertTrue(round.retain(true, "E", 310, -1, true));
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
        assertFalse(round.retain(false, null, 0, -1, true));
        assertTrue(round.retain(false, null, 0, 100, true));
        round.reply("E"); round.incoming(300);
        assertFalse(round.retain(false, null, 0, 100, true));
    }
    @Test public void stateIsBoundedAndNewConversationStartsEmpty() {
        ConversationRound round = new ConversationRound();
        for (int i = 0; i < 100; i++) round.reply("r" + i);
        assertEquals(25, round.pendingReplies.size());
        assertEquals(25, round.recentReplyIds.size());
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
        assertTrue(round.retain(false, null, 100, -1, true));
        assertTrue(round.incoming(200));
        assertFalse(round.retain(false, null, 100, -1, true));
        assertFalse(round.retain(true, "old-notification-reply", 120, -1, true));
        assertTrue(round.retain(false, null, 200, -1, true));
        assertFalse(round.pendingAppReply);
    }

    @Test public void notificationReplyAfterAppReplyStaysInNewRound() {
        ConversationRound round = new ConversationRound();
        round.incoming(100);
        round.reply("before-app");
        assertTrue(round.appReply(10, 150));
        round.reply("after-app");
        assertTrue(round.incoming(200));
        assertFalse(round.retain(true, "before-app", 120, -1, true));
        assertTrue(round.retain(true, "after-app", 160, -1, true));
        assertFalse(round.retain(false, null, 100, -1, true));
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
        assertFalse(round.retain(false, null, 0, -1, true));
        assertTrue(round.retain(false, null, 200, -1, true));
    }
}
