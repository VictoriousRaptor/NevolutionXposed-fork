package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import static org.junit.Assert.*;

public class ImagePreviewPolicyTest {
	@Test public void higherResolutionWinsRegardlessOfOrder() {
		assertTrue(ImagePreviewPolicy.isBetter(1280, 720, ImagePreviewPolicy.QUALITY_THUMBNAIL,
				384, 216, ImagePreviewPolicy.QUALITY_THUMBNAIL, 1080, 608));
		assertFalse(ImagePreviewPolicy.isBetter(384, 216, ImagePreviewPolicy.QUALITY_THUMBNAIL,
				1280, 720, ImagePreviewPolicy.QUALITY_THUMBNAIL, 1080, 608));
		assertTrue(ImagePreviewPolicy.isBetter(800, 200, ImagePreviewPolicy.QUALITY_THUMBNAIL,
				384, 384, ImagePreviewPolicy.QUALITY_THUMBNAIL, 1080, 608));
	}

	@Test public void hdWinsWhenBothCandidatesCoverTheDisplay() {
		assertTrue(ImagePreviewPolicy.isBetter(1280, 720, ImagePreviewPolicy.QUALITY_HD,
				1920, 1080, ImagePreviewPolicy.QUALITY_THUMBNAIL, 1080, 608));
		assertFalse(ImagePreviewPolicy.isBetter(640, 360, ImagePreviewPolicy.QUALITY_HD,
				1920, 1080, ImagePreviewPolicy.QUALITY_THUMBNAIL, 1080, 608));
	}

	@Test public void waitsForUndersizedCandidateThenFallsBackAtDeadline() {
		assertTrue(ImagePreviewPolicy.shouldWaitForQuality(384, 216, ImagePreviewPolicy.QUALITY_THUMBNAIL,
				1080, 608, 4999));
		assertFalse(ImagePreviewPolicy.shouldWaitForQuality(384, 216, ImagePreviewPolicy.QUALITY_THUMBNAIL,
				1080, 608, 5000));
		assertFalse(ImagePreviewPolicy.shouldWaitForQuality(1280, 720, ImagePreviewPolicy.QUALITY_HD,
				1080, 608, 0));
		assertTrue(ImagePreviewPolicy.shouldWaitForQuality(1920, 1080, ImagePreviewPolicy.QUALITY_THUMBNAIL,
				1080, 608, 100));
	}

	@Test public void completedLargeDownloadPublishesLastAvailableHdCandidate() {
		assertFalse(ImagePreviewPolicy.shouldWaitForQuality(277, 513, ImagePreviewPolicy.QUALITY_HD,
				1239, 846, 400, true));
		assertTrue(ImagePreviewPolicy.shouldWaitForQuality(277, 513, ImagePreviewPolicy.QUALITY_THUMBNAIL,
				1239, 846, 400, true));
	}

	@Test public void fitNeverUpscalesAndStaysWithinPixelBudget() {
		ImagePreviewPolicy.Size small = ImagePreviewPolicy.fitInside(320, 240, 1080, 608, ImagePreviewPolicy.MAX_PIXELS);
		assertEquals(320, small.width);
		assertEquals(240, small.height);

		ImagePreviewPolicy.Size large = ImagePreviewPolicy.fitInside(4000, 3000, 1080, 608, ImagePreviewPolicy.MAX_PIXELS);
		assertEquals(810, large.width);
		assertEquals(608, large.height);
		assertTrue((long) large.width * large.height <= ImagePreviewPolicy.MAX_PIXELS);

		ImagePreviewPolicy.Size square = ImagePreviewPolicy.fitInside(4000, 4000, 2000, 2000, ImagePreviewPolicy.MAX_PIXELS);
		assertTrue((long) square.width * square.height <= ImagePreviewPolicy.MAX_PIXELS);
		assertEquals(square.width, square.height);
	}

	@Test public void samplingKeepsDecodeLargeEnoughForFinalScale() {
		assertEquals(4, ImagePreviewPolicy.sampleSize(4000, 3000, 810, 608));
		assertEquals(1, ImagePreviewPolicy.sampleSize(1280, 720, 1080, 608));
		assertEquals(1, ImagePreviewPolicy.sampleSize(320, 240, 320, 240));
	}
}
