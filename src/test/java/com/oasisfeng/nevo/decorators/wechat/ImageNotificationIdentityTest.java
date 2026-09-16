package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ImageNotificationIdentityTest {
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
