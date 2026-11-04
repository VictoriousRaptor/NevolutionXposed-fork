package com.oasisfeng.nevo.xposed;

import android.app.Notification;
import android.service.notification.NotificationListenerService;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationRemovalReadTest {
    @Test public void onlyClickRequestsAnotherNativeRead() {
        assertTrue(NotificationRemovalRead.shouldRequest(NotificationListenerService.REASON_CLICK, 0, 1));
        for (int reason : new int[]{NotificationListenerService.REASON_CANCEL, NotificationListenerService.REASON_CANCEL_ALL,
                NotificationListenerService.REASON_APP_CANCEL, NotificationListenerService.REASON_APP_CANCEL_ALL,
                NotificationListenerService.REASON_LISTENER_CANCEL, NotificationListenerService.REASON_LISTENER_CANCEL_ALL,
                NotificationListenerService.REASON_TIMEOUT})
            assertFalse(NotificationRemovalRead.shouldRequest(reason, 0, 1));
    }

    @Test public void summariesAndNotificationsWithoutAGenerationAreExcluded() {
        assertFalse(NotificationRemovalRead.shouldRequest(NotificationListenerService.REASON_CLICK, 0, 0));
        assertFalse(NotificationRemovalRead.shouldRequest(NotificationListenerService.REASON_CLICK,
                Notification.FLAG_GROUP_SUMMARY, 1));
    }

    @Test public void nativeReadMustBelongToThePostingPackageAndUid() {
        assertTrue(NotificationRemovalRead.sameCreator("synthetic.host", 12345, "synthetic.host", 12345));
        assertFalse(NotificationRemovalRead.sameCreator("synthetic.host", 12345, "synthetic.other", 12345));
        assertFalse(NotificationRemovalRead.sameCreator("synthetic.host", 12345, "synthetic.host", 12346));
        assertFalse(NotificationRemovalRead.sameCreator(null, 12345, null, 12345));
    }
}
