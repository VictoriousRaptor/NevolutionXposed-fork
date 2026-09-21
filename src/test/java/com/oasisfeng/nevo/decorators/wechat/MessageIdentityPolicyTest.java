package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import static com.oasisfeng.nevo.decorators.wechat.MessageIdentityPolicy.Identity.*;

public class MessageIdentityPolicyTest {
    @Test public void structuredPersonWinsEvenOverReplyMarker() {
        assertEquals(PEER, MessageIdentityPolicy.resolve(true, null, true, true));
    }
    @Test public void missingOrEmptySenderIsNotSelfWithoutEvidence() {
        assertEquals(UNKNOWN, MessageIdentityPolicy.resolve(false, null, false, false));
        assertEquals(UNKNOWN, MessageIdentityPolicy.resolve(false, "", false, false));
        assertEquals(UNKNOWN, MessageIdentityPolicy.resolve(false, "", false, true));
    }
    @Test public void explicitReplyAndMessagingContractAreSelf() {
        assertEquals(SELF, MessageIdentityPolicy.resolve(false, null, true, false));
        assertEquals(SELF, MessageIdentityPolicy.resolve(false, null, false, true));
    }
    @Test public void namesNeverImplySelf() {
        for (String name : Arrays.asList("我", "群名", "Alice"))
            assertEquals(PEER, MessageIdentityPolicy.resolve(false, name, false, true));
    }
    @Test public void knownDifferentTalkersOverrideMatchingTitles() {
        assertFalse(MessageIdentityPolicy.sameConversation("同名", "a", "同名", "b"));
        assertTrue(MessageIdentityPolicy.sameConversation("旧昵称", "a", "新昵称", "a"));
        assertFalse(MessageIdentityPolicy.sameConversation("甲", null, "乙", null));
        assertFalse(MessageIdentityPolicy.sameConversation(null, null, null, null));
    }
    @Test public void oldRepliesDoNotReplaceLatestIncoming() {
        assertEquals(Arrays.asList("reply", "incoming"), merge(
                entry("incoming", 200, "peer", null, 0), entry("reply", 100, "self", "r", 1)));
    }
    @Test public void repeatedTextsAtDifferentTimesSurvive() {
        assertEquals(2, merge(entry("same", 100, "same", null, 0), entry("same", 200, "same", null, 1)).size());
    }
    @Test public void crossSourceCopiesCollapseButRepeatedOccurrencesSurvive() {
        assertEquals(Arrays.asList("a", "b"), merge(
                entry("a", 100, "same", null, 0), entry("b", 100, "same", null, 0),
                entry("copy-a", 100, "same", null, 1), entry("copy-b", 100, "same", null, 1)));
    }
    @Test public void distinctIdentityOrAttachmentSurvives() {
        assertEquals(3, merge(entry("a", 100, "peer/text", null, 0),
                entry("b", 100, "self/text", null, 1), entry("c", 100, "peer/image", null, 2)).size());
    }
    @Test public void zeroTimeHistoryIsStableAndNeverGetsNewTime() {
        assertEquals(Arrays.asList("undated-a", "undated-b", "current"), merge(
                entry("current", 100, "same", null, 0), entry("undated-a", 0, "same", null, 1),
                entry("undated-b", 0, "same", null, 2)));
    }
    @Test public void replyIdDeduplicatesAcrossRebuilds() {
        assertEquals(Arrays.asList("reply"), merge(entry("reply", 100, "self", "r", 0),
                entry("copy", 100, "self", "r", 1)));
    }
    @Test public void retainsOnlyNewestBoundedHistory() {
        List<MessageIdentityPolicy.Entry<String>> entries = Arrays.asList(
                entry("a", 1, "a", null, 0), entry("b", 2, "b", null, 0));
        assertEquals(Arrays.asList("b"), MessageIdentityPolicy.merge(entries, 1));
    }
    @SafeVarargs private static List<String> merge(MessageIdentityPolicy.Entry<String>... entries) {
        return MessageIdentityPolicy.merge(Arrays.asList(entries), 25);
    }
    private static MessageIdentityPolicy.Entry<String> entry(String value, long time, String signature, String reply, int source) {
        return new MessageIdentityPolicy.Entry<>(value, time, signature, reply, source);
    }
}
