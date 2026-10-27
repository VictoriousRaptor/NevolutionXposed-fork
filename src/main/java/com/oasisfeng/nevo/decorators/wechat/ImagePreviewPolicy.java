package com.oasisfeng.nevo.decorators.wechat;

/** Pure sizing and selection policy for notification image previews. */
public final class ImagePreviewPolicy {
	public static final String PREF_WAIT_MS = "WeChatDecorator.image_preview_wait_ms";
	public static final int DEFAULT_WAIT_MS = 1500;
	public static final int MIN_WAIT_MS = 100;
	public static final int MAX_WAIT_MS = 30000;
	private static final int IDENTITY_FALLBACK_MARGIN_MS = 500;
	static final int MAX_PIXELS = 1024 * 1024;
	static final int QUALITY_UNKNOWN = 0;
	static final int QUALITY_THUMBNAIL = 1;
	static final int QUALITY_HD = 2;

	private ImagePreviewPolicy() {}

	public static int waitMillis(String stored) {
		return isValidWaitMillis(stored) ? Integer.parseInt(stored.trim()) : DEFAULT_WAIT_MS;
	}

	public static boolean isValidWaitMillis(String stored) {
		if (stored == null) return false;
		try {
			int value = Integer.parseInt(stored.trim());
			return value >= MIN_WAIT_MS && value <= MAX_WAIT_MS;
		} catch (NumberFormatException invalid) { return false; }
	}

	static long deadline(long started, int waitMillis, long eventObserved) {
		// Finish the last candidate scan before select() can prune the confirmed event.
		return Math.min(started + waitMillis, eventObserved + ImageEventIndex.TTL_MS - IDENTITY_FALLBACK_MARGIN_MS);
	}

	static boolean shouldWaitForQuality(int width, int height, int sourceQuality,
			int targetWidth, int targetHeight, long elapsedMs, boolean largeDownloadComplete, long waitMillis) {
		if (largeDownloadComplete && sourceQuality >= QUALITY_HD) return false;
		return elapsedMs < waitMillis
				&& (sourceQuality < QUALITY_HD || !coversTarget(width, height, targetWidth, targetHeight));
	}

	static boolean isBetter(int width, int height, int sourceQuality, int currentWidth, int currentHeight,
			int currentSourceQuality, int targetWidth, int targetHeight) {
		if (width <= 0 || height <= 0) return false;
		if (currentWidth <= 0 || currentHeight <= 0) return true;
		boolean covers = coversTarget(width, height, targetWidth, targetHeight);
		boolean currentCovers = coversTarget(currentWidth, currentHeight, targetWidth, targetHeight);
		if (covers != currentCovers) return covers;
		if (covers && sourceQuality != currentSourceQuality) return sourceQuality > currentSourceQuality;
		double quality = quality(width, height, targetWidth, targetHeight);
		double currentQuality = quality(currentWidth, currentHeight, targetWidth, targetHeight);
		if (quality != currentQuality) return quality > currentQuality;
		if (sourceQuality != currentSourceQuality) return sourceQuality > currentSourceQuality;
		return (long) width * height > (long) currentWidth * currentHeight;
	}

	static boolean coversTarget(int width, int height, int targetWidth, int targetHeight) {
		return quality(width, height, targetWidth, targetHeight) >= 1d;
	}

	/** Returns the largest non-upscaled size inside both the display box and pixel budget. */
	static Size fitInside(int width, int height, int maxWidth, int maxHeight, int maxPixels) {
		if (width <= 0 || height <= 0 || maxWidth <= 0 || maxHeight <= 0 || maxPixels <= 0)
			throw new IllegalArgumentException("Invalid image bounds");
		double scale = Math.min(1d, Math.min((double) maxWidth / width, (double) maxHeight / height));
		double scaledPixels = (double) width * height * scale * scale;
		if (scaledPixels > maxPixels) scale *= Math.sqrt(maxPixels / scaledPixels);
		return new Size(Math.max(1, (int) Math.floor(width * scale)), Math.max(1, (int) Math.floor(height * scale)));
	}

	/** Keeps the sampled decode at or above the requested output size, avoiding a later upscale. */
	static int sampleSize(int width, int height, int outputWidth, int outputHeight) {
		if (width <= 0 || height <= 0 || outputWidth <= 0 || outputHeight <= 0)
			throw new IllegalArgumentException("Invalid sample bounds");
		int sample = 1;
		while (sample <= Integer.MAX_VALUE / 2
				&& width / (sample * 2) >= outputWidth && height / (sample * 2) >= outputHeight) sample *= 2;
		return sample;
	}

	private static double quality(int width, int height, int targetWidth, int targetHeight) {
		if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) return 0d;
		// BigPicture uses aspect-fit: reaching either edge means the image can be rendered without upscaling.
		return Math.max((double) width / targetWidth, (double) height / targetHeight);
	}

	static final class Size {
		final int width, height;
		Size(int width, int height) { this.width = width; this.height = height; }
	}
}
