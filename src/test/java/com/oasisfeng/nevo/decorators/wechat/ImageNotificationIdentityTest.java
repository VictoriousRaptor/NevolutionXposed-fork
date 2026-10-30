package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ImageNotificationIdentityTest {
	@Test public void verifiedIdentityRequiresBothProvenanceAndMessageKey() {
		assertNull(ImageNotificationIdentity.verified(false, "a", 9001));
		assertNull(ImageNotificationIdentity.verified(true, null, 9001));
		assertNull(ImageNotificationIdentity.verified(true, "", 9001));
		assertNull(ImageNotificationIdentity.verified(true, "  ", 9001));
		assertNull(ImageNotificationIdentity.verified(true, "a", 0));
		assertNull(ImageNotificationIdentity.verified(true, "a", -1));
		assertNotNull(ImageNotificationIdentity.verified(true, "a", 9001));
	}
	@Test public void repeatedImageNeedsANewRequestWhenVerifiedMessageChanges() {
		ImageNotificationIdentity first = ImageNotificationIdentity.verified(true, "a", 9001);
		assertEquals(first, ImageNotificationIdentity.verified(true, "a", 9001));
		assertNotEquals(first, ImageNotificationIdentity.verified(true, "a", 9002));
		assertNotEquals(first, ImageNotificationIdentity.verified(true, "b", 9001));
		assertNotEquals(first, null);
	}
	@Test public void unverifiedIdentityCannotResolveAmbiguousImages() {
		ImageEventIndex index = new ImageEventIndex();
		index.put("a", 1, 9001, 20000, Arrays.asList("/first"), 300);
		index.put("a", 2, 9002, 20000, Arrays.asList("/second"), 301);
		assertNull(index.select("a", ImageNotificationIdentity.verified(false, "a", 9002), 20000, 302, 303));
		assertEquals("a:2", index.select("a", ImageNotificationIdentity.verified(true, "a", 9002), 20000, 302, 303).key);
	}
	@Test public void verifiedIdentityCannotCrossConversationEvenWhenServerIdMatches() {
		ImageEventIndex index = new ImageEventIndex();
		index.put("b", 1, 9001, 20000, Arrays.asList("/other-chat"), 300);
		assertNull(index.select("b", ImageNotificationIdentity.verified(true, "a", 9001), 20000, 302, 303));
	}
	@Test public void appendedTextAndDuplicateRetainLiveLineage() {
		assertTrue(ImageNotificationIdentity.continues(7, 7, "a", "a", "a:1", "a:1",
				Arrays.asList("image"), Arrays.asList("image", "text"), 1));
		assertTrue(ImageNotificationIdentity.continues(7, 7, "a", "a", "a:1", "a:1",
				Arrays.asList("image"), Arrays.asList("image"), 1));
	}
	@Test public void removedOrReplacedNotificationCannotResume() {
		for (long active : new long[] {0, 8}) assertFalse(ImageNotificationIdentity.continues(7, active,
				"a", "a", "a:1", "a:1", Arrays.asList("image"), Arrays.asList("image"), 1));
	}
	@Test public void differentConversationOrEventFailsClosed() {
		assertFalse(ImageNotificationIdentity.continues(7, 7, "a", "b", "a:1", "a:1",
				Arrays.asList("image"), Arrays.asList("image"), 1));
		for (String event : new String[] {null, "a:2"}) assertFalse(ImageNotificationIdentity.continues(7, 7,
				"a", "a", "a:1", event, Arrays.asList("image"), Arrays.asList("image"), 1));
	}
	@Test public void multipleImagesOrMissingHistoryAreAmbiguous() {
		assertFalse(ImageNotificationIdentity.continues(7, 7, "a", "a", "a:1", "a:1",
				Arrays.asList("image"), Arrays.asList("image", "image"), 2));
		assertFalse(ImageNotificationIdentity.continues(7, 7, "a", "a", "a:1", "a:1",
				Arrays.asList("old", "image"), Arrays.asList("image"), 1));
		assertFalse(ImageNotificationIdentity.continues(7, 7, "a", "a", "a:1", "a:1",
				Collections.emptyList(), Arrays.asList("image"), 1));
	}
	@Test public void zeroTimestampNeedsBothEventAndLiveToken() {
		assertTrue(ImageNotificationIdentity.continues(7, 7, "a", "a", "a:1", "a:1",
				Arrays.asList("0:sender:图片"), Arrays.asList("0:sender:图片", "0:sender:text"), 1));
		assertFalse(ImageNotificationIdentity.continues(7, 0, "a", "a", "a:1", "a:1",
				Arrays.asList("0:sender:图片"), Arrays.asList("0:sender:图片", "0:sender:text"), 1));
	}
}
