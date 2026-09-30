package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeChat8077AdaptersTest {
    @Test public void buildsShareIdentityOnlyWithinTheirOwnProfile() {
        for (long code : new long[]{3141, 3160}) {
            WeChatImageProfile image = WeChatImageProfile.forVersion("8.0.77", code);
            WeChatAppReplyEvents.Profile reply = WeChatAppReplyEvents.Profile.forVersion("8.0.77", code);
            WeChatRecallEvents.Profile recall = WeChatRecallEvents.Profile.forVersion("8.0.77", code);
            assertNotNull(image); assertNotNull(reply); assertNotNull(recall);
            assertEquals(image.message, reply.messageClass);
            assertEquals(image.message, recall.message);
            assertEquals(image.talker, reply.talker);
            assertEquals(image.talker, recall.talker);
            assertEquals(image.sender, reply.isSend);
            assertEquals(image.serverId, recall.serverId);
        }
        WeChatImageProfile play = WeChatImageProfile.forVersion("8.0.77", 3141);
        WeChatImageProfile china = WeChatImageProfile.forVersion("8.0.77", 3160);
        assertNotEquals(play.flow, china.flow);
        assertNotEquals(play.download, china.download);
        assertEquals("r1", WeChatAppReplyEvents.Profile.forVersion("8.0.77", 3141).setStatus);
        assertEquals("u1", WeChatAppReplyEvents.Profile.forVersion("8.0.77", 3160).setStatus);
        assertEquals("en.gs", WeChatRecallEvents.Profile.forVersion("8.0.77", 3141).payload);
        assertEquals("fm.fs", WeChatRecallEvents.Profile.forVersion("8.0.77", 3160).payload);
    }

    @Test public void unsupportedCombinationsKeepEachComponentDisabled() {
        for (String name : new String[]{"8.0.72", "8.0.77", "8.0.78", "unknown"}) {
            for (long code : new long[]{0, 3085, 3141, 3160, 3180}) {
                boolean imageExpected = name.equals("8.0.72") && code == 3085
                        || name.equals("8.0.77") && (code == 3141 || code == 3160);
                boolean eventsExpected = imageExpected || name.equals("8.0.78") && code == 3180;
                assertEquals(name + "/" + code, imageExpected,
                        WeChatImageProfile.forVersion(name, code) != null);
                assertEquals(name + "/" + code, eventsExpected,
                        WeChatAppReplyEvents.Profile.forVersion(name, code) != null);
                assertEquals(name + "/" + code, eventsExpected,
                        WeChatRecallEvents.Profile.forVersion(name, code) != null);
            }
        }
    }
}
