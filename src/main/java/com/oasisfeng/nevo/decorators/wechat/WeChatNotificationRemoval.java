package com.oasisfeng.nevo.decorators.wechat;

import android.service.notification.NotificationListenerService;

import java.util.concurrent.atomic.AtomicLong;

/** Cross-process contract for resetting notification-only conversation history after user removal. */
public final class WeChatNotificationRemoval {

	public static final String ACTION_RESET_ROUND = "com.oasisfeng.nevo.xposed.action.RESET_WECHAT_ROUND";
	public static final String EXTRA_NOTIFICATION_ID = "nevo.wechat.removedNotificationId";
	public static final String EXTRA_ROUND_TOKEN = "nevo.wechat.removedRoundToken";
	public static final String NOTIFICATION_ROUND_TOKEN = "nevo.wechat.roundToken";

	private static final AtomicLong TOKENS = new AtomicLong(System.nanoTime());

	/** A process-local generation is sufficient because the archived notifications are process-local too. */
	public static long nextToken() {
		long token;
		do token = TOKENS.incrementAndGet(); while (token == 0);
		return token;
	}

	public static boolean shouldResetRound(final int reason) {
		switch (reason) {
		case NotificationListenerService.REASON_CLICK:
		case NotificationListenerService.REASON_CANCEL:
		case NotificationListenerService.REASON_CANCEL_ALL:
		case NotificationListenerService.REASON_LISTENER_CANCEL:
		case NotificationListenerService.REASON_LISTENER_CANCEL_ALL:
			return true;
		default:
			return false;
		}
	}

	public static boolean tokenMatches(final long current, final long removed) {
		return current != 0 && current == removed;
	}

	private WeChatNotificationRemoval() {}
}
