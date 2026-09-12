package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImageCandidateSelectorTest {
	@Test public void restrictsFileTimeToNotificationWindow() {
		assertTrue(ImageCandidateSelector.isRecent(9945, 10000));
		assertFalse(ImageCandidateSelector.isRecent(7999, 10000));
		assertFalse(ImageCandidateSelector.isRecent(12001, 10000));
		assertFalse(ImageCandidateSelector.isRecent(0, 1000));
	}
	@Test public void rejectsReplacedOrMissingNotificationToken() {
		assertTrue(ImageCandidateSelector.matchesRequest(12, 12));
		assertFalse(ImageCandidateSelector.matchesRequest(12, 13));
		assertFalse(ImageCandidateSelector.matchesRequest(12, 0));
		assertFalse(ImageCandidateSelector.matchesRequest(0, 0));
	}
}
