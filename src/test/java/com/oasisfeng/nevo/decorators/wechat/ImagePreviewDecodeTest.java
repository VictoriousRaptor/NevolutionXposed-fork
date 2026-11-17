package com.oasisfeng.nevo.decorators.wechat;

import android.graphics.Bitmap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 28}, manifest = Config.NONE)
public class ImagePreviewDecodeTest {
	@Rule public TemporaryFolder files = new TemporaryFolder();

	@Test public void longImageIsNotShrunkToNotificationHeight() throws Exception {
		assertDecodedSize(1080, 6000, false, 848, 4714);
	}

	@Test public void convertedImageBytesUseTheSameResolutionPolicy() throws Exception {
		assertDecodedSize(3000, 3000, true, 1200, 1200);
	}

	@Test public void smallImageIsNotUpscaled() throws Exception {
		assertDecodedSize(320, 240, false, 320, 240);
	}

	private void assertDecodedSize(int width, int height, boolean converted, int outputWidth, int outputHeight)
			throws Exception {
		File file = files.newFile("synthetic-image.png");
		Bitmap source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		byte[] bytes = null;
		try {
			if (converted) {
				try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
					assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output));
					bytes = output.toByteArray();
				}
				// The original file only supplies the identity/stability check after native conversion.
				try (FileOutputStream output = new FileOutputStream(file)) { output.write(1); }
			} else {
				try (FileOutputStream output = new FileOutputStream(file)) {
					assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output));
				}
			}
		} finally { source.recycle(); }
		ImagePreviewLoader.Candidate candidate = new ImagePreviewLoader.Candidate(file, file.length(),
				file.lastModified(), width, height, "image/png", ImagePreviewPolicy.QUALITY_HD, bytes);
		Bitmap decoded = ImagePreviewLoader.decode(candidate);
		assertNotNull(decoded);
		try {
			assertEquals(outputWidth, decoded.getWidth());
			assertEquals(outputHeight, decoded.getHeight());
			assertTrue((long) decoded.getWidth() * decoded.getHeight() <= ImagePreviewPolicy.MAX_PIXELS);
		} finally { decoded.recycle(); }
	}
}
