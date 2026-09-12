package com.oasisfeng.nevo.decorators.wechat;

/** Narrow helpers for message-associated images; notification images are never discovered by scanning directories. */
final class ImageCandidateSelector {
	static final long WINDOW_MS = 2000;

	/** Tolerates either timestamp being expressed in seconds or milliseconds; the window itself stays absolute. */
	static boolean isRecent(long messageTime, long notificationTime) {
		if (messageTime <= 0 || notificationTime <= 0) return false;
		return near(messageTime, notificationTime)
				|| near(messageTime, notificationTime * 1000)
				|| near(messageTime * 1000, notificationTime);
	}

	private static boolean near(long actual, long expected) {
		return actual >= expected - WINDOW_MS && actual <= expected + WINDOW_MS;
	}

	static boolean matchesRequest(long expected, long actual) {
		return expected != 0 && expected == actual;
	}
}
