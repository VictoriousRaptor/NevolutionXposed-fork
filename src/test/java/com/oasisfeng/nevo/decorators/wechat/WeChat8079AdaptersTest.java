package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pins the APK-verified contracts and rejects mixed version/build combinations. */
public class WeChat8079AdaptersTest {
    @Test public void sharesVerifiedMessageIdentityAcrossIndependentComponents() {
        WeChatImageProfile image = WeChatImageProfile.forVersion("8.0.79", 3182);
        WeChatAppReplyEvents.Profile reply = WeChatAppReplyEvents.Profile.forVersion("8.0.79", 3182);
        WeChatRecallEvents.Profile recall = WeChatRecallEvents.Profile.forVersion("8.0.79", 3182);
        assertNotNull(image); assertNotNull(reply); assertNotNull(recall);
        assertEquals("com.tencent.mm.storage.e9", image.message);
        assertEquals(image.message, reply.messageClass);
        assertEquals(image.message, recall.message);
        assertEquals("N0", image.talker);
        assertEquals(image.talker, reply.talker);
        assertEquals(image.talker, recall.talker);
        assertEquals("C0", image.sender);
        assertEquals(image.sender, reply.isSend);
        assertEquals("I0", image.serverId);
        assertEquals(image.serverId, recall.serverId);
        assertEquals("en.js", recall.payload);
        assertEquals("r1", reply.setStatus);
        assertEquals("M0", reply.status);
        assertArrayEquals(new String[]{"a3", "M2", "F2"}, reply.excluded);
    }

    @Test public void selectsNewStateAccessorWithoutChangingOlderBuilds() {
        WeChatImageProfile image = WeChatImageProfile.forVersion("8.0.79", 3182);
        assertEquals("gh5.z", image.state);
        assertEquals("f", image.stateGet);
        assertEquals("sb0.l", image.flow);
        assertEquals("I3", image.pathMethod);
        assertEquals("v61.j", image.download);
        assertEquals("Bj", image.downloadGet);
        assertEquals("d", WeChatImageProfile.forVersion("8.0.72", 3085).stateGet);
        assertEquals("d", WeChatImageProfile.forVersion("8.0.77", 3141).stateGet);
        assertEquals("d", WeChatImageProfile.forVersion("8.0.77", 3160).stateGet);
    }

    @Test public void rejectsUnknownBuildsAndCrossedNamesInEveryComponent() {
        for (String name : new String[]{"8.0.72", "8.0.77", "8.0.78", "8.0.79", "8.0.79.dev", "unknown"}) {
            for (long code : new long[]{0, 3085, 3141, 3160, 3180, 3182, 3183}) {
                if (!name.equals("8.0.79") && code != 3182) continue;
                boolean expected = name.equals("8.0.79") && code == 3182;
                assertEquals(name + "/" + code, expected, WeChatImageProfile.forVersion(name, code) != null);
                assertEquals(name + "/" + code, expected, WeChatAppReplyEvents.Profile.forVersion(name, code) != null);
                assertEquals(name + "/" + code, expected, WeChatRecallEvents.Profile.forVersion(name, code) != null);
            }
        }
    }
}
