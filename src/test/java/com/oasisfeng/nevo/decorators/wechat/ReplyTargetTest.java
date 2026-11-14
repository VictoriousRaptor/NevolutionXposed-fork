package com.oasisfeng.nevo.decorators.wechat;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReplyTargetTest {
    private static Conversation conversation() {
        Conversation c = new Conversation(7);
        c.notificationPeerId = "synthetic-peer";
        return c;
    }

    @Test public void endedRoundCanAcknowledgeItsStillVisibleCard() {
        Conversation c = conversation();
        ReplyTarget target = new ReplyTarget(10, c, null);
        c.round.end(10);
        assertTrue(target.matches(c, 10, "synthetic-peer"));
        assertEquals(10, c.round.endThroughToken);
    }

    @Test public void lateCompletionCannotAcknowledgeANewerGeneration() {
        Conversation c = conversation();
        assertFalse(new ReplyTarget(10, c, null).matches(c, 11, "synthetic-peer"));
    }

    @Test public void reusedNotificationIdDoesNotMatchTheOldConversation() {
        Conversation old = conversation(), replacement = conversation();
        assertFalse(new ReplyTarget(10, old, null).matches(replacement, 10, "synthetic-peer"));
    }

    @Test public void differentOrUnknownPeerCannotBeRefreshed() {
        Conversation c = conversation();
        ReplyTarget target = new ReplyTarget(10, c, null);
        assertFalse(target.matches(c, 10, "other-peer"));
        assertFalse(target.matches(c, 10, null));
        c.notificationPeerId = null;
        assertFalse(target.matches(c, 10, null));
    }

    @Test public void missingReadActionDoesNotPreventUiAcknowledgement() {
        Conversation c = conversation();
        assertTrue(new ReplyTarget(10, c, null).matches(c, 10, "synthetic-peer"));
    }

    @Test public void missingConversationOrInvalidGenerationCannotMatch() {
        Conversation c = conversation();
        assertFalse(new ReplyTarget(0, c, null).matches(c, 0, "synthetic-peer"));
        assertFalse(new ReplyTarget(-1, c, null).matches(c, -1, "synthetic-peer"));
        assertFalse(new ReplyTarget(10, null, null).matches(c, 10, "synthetic-peer"));
        assertFalse(new ReplyTarget(10, c, null).matches(null, 10, "synthetic-peer"));
    }
}
