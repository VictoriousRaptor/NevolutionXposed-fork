package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import static org.junit.Assert.*;

public class WeChatAppReplyEventsTest {
    @Test public void usesOnlyExactVerifiedVersionMappings() {
        WeChatAppReplyEvents.Profile play = WeChatAppReplyEvents.Profile.forVersion("8.0.72", 3085);
        assertNotNull(play);
        assertEquals("com.tencent.mm.storage.f9", play.messageClass);
        assertEquals("q1", play.setStatus);

        WeChatAppReplyEvents.Profile china = WeChatAppReplyEvents.Profile.forVersion("8.0.78", 3180);
        assertNotNull(china);
        assertEquals("com.tencent.mm.storage.e9", china.messageClass);
        assertEquals("t1", china.setStatus);

        assertNull(WeChatAppReplyEvents.Profile.forVersion("8.0.72", 3180));
        assertNull(WeChatAppReplyEvents.Profile.forVersion("8.0.78", 3085));
        assertNull(WeChatAppReplyEvents.Profile.forVersion("8.0.76", 0));
    }
}
