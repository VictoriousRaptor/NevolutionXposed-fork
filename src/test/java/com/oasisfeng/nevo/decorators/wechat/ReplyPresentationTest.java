package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReplyPresentationTest {
    @Test public void unknownPresentationCannotBeAcknowledged() {
        assertFalse(new ReplyPresentation().allows(null, 10));
    }

    @Test public void acceptedNewerCardInvalidatesAStillActiveOldCard() {
        ReplyPresentation state = new ReplyPresentation();
        state.accept(null, 10);
        assertTrue(state.allows(null, 10));
        state.accept(null, 20); // C passed BEFORE; system delivery still shows A.
        assertFalse(state.allows(null, 10));
        assertTrue(state.allows(null, 20));
    }

    @Test public void observedCancelInvalidatesBeforeSystemDelivery() {
        ReplyPresentation state = new ReplyPresentation();
        state.accept("tag", 10);
        state.cancel("tag");
        state.restore("tag", 10); // A is still returned by getActiveNotifications().
        assertFalse(state.allows("tag", 10));
        state.accept("tag", 20);
        assertTrue(state.allows("tag", 20));
    }

    @Test public void wrongTagDoesNotInvalidateCurrentCard() {
        ReplyPresentation state = new ReplyPresentation();
        state.accept("tag", 10);
        state.cancel(null);
        assertTrue(state.allows("tag", 10));
        assertFalse(state.allows(null, 10));
    }

    @Test public void cancelAllInvalidatesWithoutRetiringUnreadHistory() {
        ReplyPresentation state = new ReplyPresentation();
        state.accept("tag", 10);
        state.cancelAll();
        state.restore("tag", 10);
        assertFalse(state.allows("tag", 10));
    }

    @Test public void coldProcessMayRestoreAnUnobservedActiveCard() {
        ReplyPresentation state = new ReplyPresentation();
        state.restore(null, 10);
        assertTrue(state.allows(null, 10));
        state.restore(null, 20);
        assertFalse(state.allows(null, 20));
        assertTrue(state.allows(null, 10));
    }

    @Test public void cancellationBeforeFirstCaptureCannotRestoreTheCancelledCard() {
        ReplyPresentation state = new ReplyPresentation();
        state.cancel(null);
        state.restore(null, 10);
        assertFalse(state.allows(null, 10));
    }

    @Test public void invalidOrOlderAcceptanceCannotReplaceNewerCard() {
        ReplyPresentation state = new ReplyPresentation();
        state.accept(null, 20);
        state.accept(null, 10);
        state.accept(null, 0);
        state.accept(null, -1);
        assertTrue(state.allows(null, 20));
        assertFalse(state.allows(null, 10));
        assertFalse(state.allows(null, 0));
    }
}
