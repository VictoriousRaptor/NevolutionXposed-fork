package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeChatMessageTest {
    @Test public void directGroupAndMediaTickersAreChat() {
        assertTrue(WeChatMessage.isChat("Alice: hello"));
        assertTrue(WeChatMessage.isChat("Group: Bob: hello"));
        assertTrue(WeChatMessage.isChat("Alice:[图片]"));
        assertTrue(WeChatMessage.isChat(new StringBuilder("Alice: [文件]")));
    }
    @Test public void friendRequestsMissingAndColonFreeTickersAreNotChat() {
        assertFalse(WeChatMessage.isChat("Alice请求添加你为朋友"));
        assertFalse(WeChatMessage.isChat(null));
        assertFalse(WeChatMessage.isChat(""));
        assertFalse(WeChatMessage.isChat("hello"));
        assertFalse(WeChatMessage.isChat(":hello"));
        assertFalse(WeChatMessage.isChat(":"));
    }
    @Test public void eligibilityDoesNotRequireNativeReplyObjects() {
        assertTrue(WeChatMessage.isChat("Alice: hello"));
        assertTrue(WeChatMessage.isChat(":Alice: hello"));
        assertTrue(WeChatMessage.canUseSyntheticReply("Alice: hello", true, true));
        assertFalse(WeChatMessage.canUseSyntheticReply("Alice: hello", true, false));
        assertFalse(WeChatMessage.canUseSyntheticReply("Alice: hello", false, true));
        assertFalse(WeChatMessage.canUseSyntheticReply("好友请求", true, true));
    }
}
