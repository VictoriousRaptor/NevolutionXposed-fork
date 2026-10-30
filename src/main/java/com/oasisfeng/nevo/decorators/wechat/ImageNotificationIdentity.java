package com.oasisfeng.nevo.decorators.wechat;

import java.util.List;

/** Verified message identity is independent of image bytes; continuity also requires the live notification lineage. */
final class ImageNotificationIdentity {
	final String talker;
	final long serverId;

	private ImageNotificationIdentity(String talker, long serverId) {
		this.talker = talker;
		this.serverId = serverId;
	}

	static ImageNotificationIdentity verified(boolean verified, String talker, long serverId) {
		return verified && talker != null && !talker.trim().isEmpty() && serverId > 0
				? new ImageNotificationIdentity(talker, serverId) : null;
	}

	@Override public boolean equals(Object other) {
		if (!(other instanceof ImageNotificationIdentity)) return false;
		ImageNotificationIdentity identity = (ImageNotificationIdentity) other;
		return serverId == identity.serverId && talker.equals(identity.talker);
	}

	@Override public int hashCode() { return 31 * talker.hashCode() + Long.hashCode(serverId); }

	static boolean continues(long token, long activeToken, String talker, String currentTalker,
			String event, String currentEvent, List<String> original, List<String> current, int imageCount) {
		return token != 0 && token == activeToken && talker != null && talker.equals(currentTalker)
				&& event != null && event.equals(currentEvent) && imageCount == 1
				&& !original.isEmpty() && current.size() >= original.size()
				&& original.equals(current.subList(0, original.size()));
	}
}
