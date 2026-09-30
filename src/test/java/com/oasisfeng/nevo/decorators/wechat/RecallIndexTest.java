package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecallIndexTest {
    @Test public void sameIdsInOtherConversationsAreIndependent() {
        RecallIndex index = new RecallIndex(); index.add("peer", 7);
        assertArrayEquals(new long[]{7}, index.ids("peer"));
        assertEquals(0, index.ids("group").length);
        index.add("group", 8);
        assertArrayEquals(new long[]{8}, index.ids("group"));
    }
    @Test public void boundedIndexRetainsRecentlyRecalledConversation() {
        RecallIndex index = new RecallIndex();
        for (int i = 0; i < 64; i++) index.add("peer" + i, i + 1);
        index.add("peer0", 200); index.add("peer64", 65);
        assertArrayEquals(new long[]{1, 200}, index.ids("peer0"));
        assertEquals(0, index.ids("peer1").length);
    }
    @Test public void profileRequiresExactVersionAndCode() {
        assertNotNull(WeChatRecallEvents.messageClass("8.0.72", 3085));
        assertNotNull(WeChatRecallEvents.messageClass("8.0.78", 3180));
        assertNotNull(WeChatRecallEvents.messageClass("8.0.77", 3160));
        assertNotNull(WeChatRecallEvents.messageClass("8.0.77", 3141));
        assertNull(WeChatRecallEvents.messageClass("8.0.77", 3180));
        assertNull(WeChatRecallEvents.messageClass("8.0.78", 3181));
    }
}
