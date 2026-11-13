package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class ConversationRoundTest {
    @Test public void submitEndsExistingGenerationImmediately() {
        ConversationRound round = new ConversationRound();
        assertTrue(round.retains(10));
        assertTrue(round.end(10));
        assertFalse(round.retains(10));
        assertTrue(round.retains(11));
    }
    @Test public void repeatedAndLateEndsCannotConsumeANewerPeer() {
        ConversationRound round = new ConversationRound();
        assertTrue(round.end(10));
        assertFalse(round.end(10));
        assertFalse(round.end(9));
        assertEquals(10, round.endThroughToken);
        assertTrue(round.retains(11));
    }
    @Test public void noClockIsNeededToStartTheNextRound() {
        ConversationRound round = new ConversationRound();
        round.end(10);
        assertTrue(round.retains(11));
        assertTrue(round.retains(12));
        assertFalse(round.retains(0));
    }
    @Test public void invalidBoundaryDoesNotRetireAnything() {
        ConversationRound round = new ConversationRound();
        assertFalse(round.end(0));
        assertFalse(round.end(-1));
        assertTrue(round.retains(1));
    }
    @Test public void oldVoiceWithANewNotificationTokenKeepsItsFirstGeneration() {
        ConversationRound round = new ConversationRound();
        Object voice = Arrays.asList("sender-a", 101L);
        assertEquals(10, round.firstToken(voice, 10));
        round.end(10);
        assertEquals(10, round.firstToken(voice, 11));
        assertFalse(round.retains(round.firstToken(voice, 12)));
        assertTrue(round.retains(round.firstToken(Arrays.asList("sender-a", 102L), 13)));
    }
    @Test public void sendersAndConversationsHaveSeparateIdentities() {
        ConversationRound one = new ConversationRound(), two = new ConversationRound();
        assertEquals(10, one.firstToken(Arrays.asList("a", 101L), 10));
        one.end(10);
        assertEquals(11, one.firstToken(Arrays.asList("b", 101L), 11));
        assertEquals(12, two.firstToken(Arrays.asList("a", 101L), 12));
    }
    @Test public void missingIdentityDoesNotMergeConsecutiveVoices() {
        ConversationRound round = new ConversationRound();
        assertEquals(10, round.firstToken(null, 10));
        assertEquals(11, round.firstToken(null, 11));
    }
    @Test public void duplicateWithinTheActiveRoundRetainsOriginalOrder() {
        ConversationRound round = new ConversationRound();
        assertEquals(10, round.firstToken("first", 10));
        assertEquals(10, round.firstToken("first", 15));
        assertEquals(11, round.firstToken("second", 11));
        assertTrue(round.retains(round.firstToken("first", 16)));
    }
    @Test public void identityCacheRemainsBoundedAcrossRounds() {
        ConversationRound round = new ConversationRound();
        for (int i = 1; i <= 65; i++) {
            round.firstToken(i, i);
            round.end(i);
        }
        assertEquals(65, round.firstToken(65, 100));
        assertEquals(100, round.firstToken(1, 100));
    }
    @Test public void invalidGenerationCannotPoisonAStableIdentity() {
        ConversationRound round = new ConversationRound();
        assertEquals(0, round.firstToken("voice", 0));
        assertEquals(10, round.firstToken("voice", 10));
    }
}
