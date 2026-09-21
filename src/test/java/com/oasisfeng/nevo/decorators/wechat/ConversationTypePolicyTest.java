package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationTypePolicyTest {
    @Test public void noEvidenceRemainsUnknown() {
        assertEquals(ConversationTypePolicy.UNKNOWN, ConversationTypePolicy.resolve(null, null, null).type);
        assertEquals(ConversationTypePolicy.UNKNOWN, ConversationTypePolicy.resolve(" ", null, null).type);
    }
    @Test public void talkerClassifiesSupportedKinds() {
        assertEquals(1, ConversationTypePolicy.resolve("wxid_alice", null, null).type);
        assertEquals(2, ConversationTypePolicy.resolve("123@chatroom", null, null).type);
        assertEquals(2, ConversationTypePolicy.resolve("123@im.chatroom", null, null).type);
        assertEquals(3, ConversationTypePolicy.resolve("gh_service", null, null).type);
    }
    @Test public void talkerOverridesConflictingNativeFlag() {
        assertEquals(1, ConversationTypePolicy.resolve("alice", true, null).type);
        assertEquals(2, ConversationTypePolicy.resolve("x@chatroom", false, null).type);
    }
    @Test public void explicitNativeFlagIsEvidenceButMissingFlagIsNotFalse() {
        assertEquals(1, ConversationTypePolicy.resolve(null, false, null).type);
        assertEquals(2, ConversationTypePolicy.resolve(null, true, null).type);
        assertEquals(0, ConversationTypePolicy.resolve(null, null, null).type);
    }
    @Test public void knownConversationSurvivesMissingFields() {
        ConversationTypePolicy.Decision direct = ConversationTypePolicy.resolve("alice", null, null);
        assertSame(direct, ConversationTypePolicy.resolve(null, null, direct));
    }
    @Test public void unverifiedCachedGuessIsDiscarded() {
        ConversationTypePolicy.Decision old = new ConversationTypePolicy.Decision(2, ConversationTypePolicy.Source.UNKNOWN);
        assertEquals(0, ConversationTypePolicy.resolve(null, null, old).type);
    }
    @Test public void nativeEvidenceOverridesEarlierNativeEvidence() {
        ConversationTypePolicy.Decision old = ConversationTypePolicy.resolve(null, true, null);
        assertEquals(1, ConversationTypePolicy.resolve(null, false, old).type);
    }
    @Test public void identityEqualityRequiresKnownKeys() {
        assertFalse(ConversationTypePolicy.sameKnownTalker(null, null));
        assertFalse(ConversationTypePolicy.sameKnownTalker("", ""));
        assertTrue(ConversationTypePolicy.sameKnownTalker("alice", "alice"));
        assertFalse(ConversationTypePolicy.sameKnownTalker("alice", "bob"));
    }
}
