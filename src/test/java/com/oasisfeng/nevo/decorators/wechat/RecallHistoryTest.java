package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecallHistoryTest {
    @Test public void exactIdSurvivesSnapshotAndRepeatedRecalls() {
        RecallHistory history = new RecallHistory();
        history.add(2); history.add(2); history.add(4);
        RecallHistory restored = new RecallHistory(); restored.addAll(history.ids());
        assertTrue(restored.contains(2)); assertTrue(restored.contains(4));
        assertFalse(restored.contains(1)); assertFalse(restored.contains(3));
        assertEquals(2, restored.ids().length);
    }
    @Test public void missingIdsCannotRemoveAnything() {
        RecallHistory history = new RecallHistory(); history.add(0); history.add(-1);
        assertEquals(0, history.ids().length); assertFalse(history.contains(0));
    }
    @Test public void stateIsBounded() {
        RecallHistory history = new RecallHistory();
        for (int i = 1; i <= 1000; i++) history.add(i);
        assertEquals(128, history.ids().length); assertTrue(history.contains(1000));
    }
    @Test public void systemPromptFormatDoesNotMatchOrdinaryProse() {
        assertTrue(RecallHistory.isPrompt("撤回一条消息"));
        assertTrue(RecallHistory.isPrompt("[2条]\"Alice\" 撤回了一条消息"));
        assertTrue(RecallHistory.isPrompt("“Bob”撤回了一条消息"));
        assertTrue(RecallHistory.isPrompt("你撤回了一条消息"));
        assertFalse(RecallHistory.isPrompt("Alice: 撤回了一条消息"));
        assertFalse(RecallHistory.isPrompt("讨论撤回了一条消息的行为"));
        assertFalse(RecallHistory.isPrompt(null));
    }
}
