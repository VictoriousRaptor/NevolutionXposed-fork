package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import static org.junit.Assert.*;

public class NotificationReplyOriginsTest {
    @Test public void consumesOnlyMatchingNotificationReply() {
        NotificationReplyOrigins origins = new NotificationReplyOrigins();
        assertEquals(0, origins.reserve(null, 100));
        assertTrue(origins.reserve("alice", 100) > 0);
        assertEquals(NotificationReplyOrigins.Origin.APP, origins.classify("bob", 1, 101));
        assertEquals(NotificationReplyOrigins.Origin.NOTIFICATION, origins.classify("alice", 2, 102));
        assertEquals(NotificationReplyOrigins.Origin.DUPLICATE, origins.classify("alice", 2, 103));
        assertEquals(NotificationReplyOrigins.Origin.APP, origins.classify("alice", 3, 104));
    }

    @Test public void failedAndExpiredDispatchesDoNotHideAppSends() {
        NotificationReplyOrigins origins = new NotificationReplyOrigins();
        long failed = origins.reserve("alice", 100);
        origins.abort(failed);
        assertEquals(NotificationReplyOrigins.Origin.APP, origins.classify("alice", 1, 101));
        origins.reserve("alice", 200);
        assertEquals(NotificationReplyOrigins.Origin.APP, origins.classify("alice", 2, 120_201));
    }

    @Test public void multipleNotificationRepliesConsumeOneSuccessEach() {
        NotificationReplyOrigins origins = new NotificationReplyOrigins();
        origins.reserve("alice", 100);
        origins.reserve("alice", 101);
        assertEquals(NotificationReplyOrigins.Origin.NOTIFICATION, origins.classify("alice", 1, 102));
        assertEquals(NotificationReplyOrigins.Origin.NOTIFICATION, origins.classify("alice", 2, 103));
        assertEquals(NotificationReplyOrigins.Origin.APP, origins.classify("alice", 3, 104));
    }
}
