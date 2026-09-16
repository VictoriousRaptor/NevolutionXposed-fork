package com.oasisfeng.nevo.decorators.wechat;

import java.util.List;

/** Continuity requires a live predecessor token and an unchanged ordered message prefix, not a text match. */
final class ImageNotificationIdentity {
	static boolean continues(long token, long activeToken, String talker, String currentTalker,
			String event, String currentEvent, List<String> original, List<String> current, int imageCount) {
		return token != 0 && token == activeToken && talker != null && talker.equals(currentTalker)
				&& event != null && event.equals(currentEvent) && imageCount == 1
				&& !original.isEmpty() && current.size() >= original.size()
				&& original.equals(current.subList(0, original.size()));
	}
}
