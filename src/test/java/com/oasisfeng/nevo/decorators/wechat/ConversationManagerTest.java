package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationManagerTest {
    @Test public void archivedConversationIsProtectedFromIdleIdentityEviction() {
        ConversationManager manager = new ConversationManager(id -> id == 0);
        ConversationManager.Conversation active = manager.getConversation(0);
        for (int id = 1; id < 300; id++) manager.getConversation(id);
        assertSame(active, manager.getConversation(0));
    }

    @Test public void historicalIdentitiesHaveABoundedLifetime() {
        ConversationManager manager = new ConversationManager();
        for (int id = 0; id < 300; id++) manager.acceptTalker(manager.getConversation(id), "peer-" + id);
        assertNull(manager.knownKeyForId(0));
        assertEquals("peer-299", manager.knownKeyForId(299));
    }

    @Test public void recentlyUsedIdentitySurvivesOlderIdleEntries() {
        ConversationManager manager = new ConversationManager();
        ConversationManager.Conversation first = manager.getConversation(0);
        for (int id = 1; id < 256; id++) manager.acceptTalker(manager.getConversation(id), "peer-" + id);
        assertSame(first, manager.getConversation(0));
        manager.getConversation(256);
        assertSame(first, manager.getConversation(0));
        assertNull(manager.knownKeyForId(1));
    }

    @Test public void evictedInstancesCannotAcceptLateTalkerCallbacks() {
        ConversationManager manager = new ConversationManager();
        ConversationManager.Conversation first = manager.getConversation(0);
        for (int id = 1; id <= 256; id++) manager.getConversation(id);
        assertFalse(manager.acceptTalker(first, "old-peer"));
        assertTrue(manager.acceptTalker(manager.getConversation(0), "new-peer"));
        assertEquals("new-peer", manager.knownKeyForId(0));
    }
}
