package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ImageEventIndexTest {
	@Test public void neverMatchesAnotherConversation() {
		ImageEventIndex index = new ImageEventIndex();
		index.put("a", 1, 10000, Arrays.asList("/thumb"), 1);
		assertNull(index.select("b", 10000, 1, 2));
		assertNotNull(index.select("a", 10000, 1, 2));
		assertNull(index.select(null, 10000, 1, 2));
	}
	@Test public void refusesTwoMessagesInOneWindow() {
		ImageEventIndex index = new ImageEventIndex();
		index.put("a", 1, 10000, Arrays.asList("/first"), 1);
		index.put("a", 2, 10001, Arrays.asList("/second"), 2);
		assertNull(index.select("a", 10000, 2, 3));
	}
	@Test public void repeatedEventMergesPathsWithoutDuplicatingMessage() {
		ImageEventIndex index = new ImageEventIndex();
		index.put("a", 1, 10000, Arrays.asList("/thumb"), 1);
		index.put("a", 1, 10000, Arrays.asList("/thumb", "/hd"), 2);
		assertEquals(1, index.size(3));
		assertEquals(2, index.select("a", 10000, 2, 3).paths.size());
	}
	@Test public void evictsOldAndExpiredEvents() {
		ImageEventIndex index = new ImageEventIndex();
		for (int i = 1; i <= 100; i++) index.put("a", i, i * 10000L, Arrays.asList("/" + i), 1);
		assertEquals(64, index.size(2));
		assertNull(index.select("a", 10000, 1, 2));
		assertEquals(0, index.size(30001));
	}
	@Test public void rejectsStaleMessageAndInvalidIdentity() {
		ImageEventIndex index = new ImageEventIndex();
		index.put("a", 0, 10000, Arrays.asList("/a"), 1);
		assertEquals(0, index.size(2));
		index.put("a", 1, 10000, Arrays.asList("/a"), 1);
		assertNull(index.select("a", 14000, 60000, 2));
	}
	/** WeChat's own notification timestamp lagged the message by 10s in practice; observation time is the anchor. */
	@Test public void matchesByObservationWhenNotificationTimestampLags() {
		ImageEventIndex index = new ImageEventIndex();
		index.put("a", 7, 1789189363000L, Arrays.asList("/image2/96/8a/th_968a"), 45096);
		assertNotNull(index.select("a", 1789189352397L, 45327, 46000));
		assertNull(index.select("a", 1789189352397L, 60000, 51000));
		assertNull(index.select("b", 1789189352397L, 45327, 46000));
	}
}
