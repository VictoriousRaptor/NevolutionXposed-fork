package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.io.File;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ImageCandidateSelectorTest {
	@Test public void acceptsDecodableFileWithoutExtension() {
		File thumbnail = new File("th_123456");
		assertEquals(thumbnail, ImageCandidateSelector.unique(Arrays.asList(thumbnail), f -> true));
	}
	@Test public void rejectsAmbiguousImagesInsteadOfPickingNewest() {
		assertNull(ImageCandidateSelector.unique(Arrays.asList(new File("a"), new File("b.jpg")), f -> true));
	}
	@Test public void skipsUndecodableFileEvenWithJpgSuffix() {
		File valid = new File("thumbnail");
		assertEquals(valid, ImageCandidateSelector.unique(Arrays.asList(new File("broken.jpg"), valid), valid::equals));
	}
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
