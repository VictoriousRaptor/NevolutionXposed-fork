package com.oasisfeng.nevo.decorators.wechat;

import android.service.notification.NotificationListenerService;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WeChatNotificationRemovalTest {

	@Test public void userDrivenRemovalResetsRound() {
		assertTrue(WeChatNotificationRemoval.shouldResetRound(NotificationListenerService.REASON_CLICK));
		assertTrue(WeChatNotificationRemoval.shouldResetRound(NotificationListenerService.REASON_CANCEL));
		assertTrue(WeChatNotificationRemoval.shouldResetRound(NotificationListenerService.REASON_CANCEL_ALL));
		assertTrue(WeChatNotificationRemoval.shouldResetRound(NotificationListenerService.REASON_LISTENER_CANCEL));
		assertTrue(WeChatNotificationRemoval.shouldResetRound(NotificationListenerService.REASON_LISTENER_CANCEL_ALL));
	}

	@Test public void systemOrApplicationRemovalDoesNotResetRound() {
		assertFalse(WeChatNotificationRemoval.shouldResetRound(NotificationListenerService.REASON_APP_CANCEL));
		assertFalse(WeChatNotificationRemoval.shouldResetRound(NotificationListenerService.REASON_APP_CANCEL_ALL));
		assertFalse(WeChatNotificationRemoval.shouldResetRound(NotificationListenerService.REASON_SNOOZED));
		assertFalse(WeChatNotificationRemoval.shouldResetRound(NotificationListenerService.REASON_TIMEOUT));
		assertFalse(WeChatNotificationRemoval.shouldResetRound(NotificationListenerService.REASON_CHANNEL_BANNED));
	}

	@Test public void onlyCurrentNonZeroTokenMatches() {
		long current = WeChatNotificationRemoval.nextToken();
		long newer = WeChatNotificationRemoval.nextToken();
		assertNotEquals(0, current);
		assertNotEquals(current, newer);
		assertTrue(WeChatNotificationRemoval.tokenMatches(current, current));
		assertFalse(WeChatNotificationRemoval.tokenMatches(current, newer));
		assertFalse(WeChatNotificationRemoval.tokenMatches(0, 0));
	}
}
