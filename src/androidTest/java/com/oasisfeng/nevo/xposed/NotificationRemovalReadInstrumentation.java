package com.oasisfeng.nevo.xposed;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.content.ContextCompat;

import com.oasisfeng.nevo.decorators.wechat.WeChatNotificationRemoval;
import com.oasisfeng.nevo.decorators.wechat.WeChatDecorator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic local targets only; never posts notifications or sends a broadcast to WeChat. */
public final class NotificationRemovalReadInstrumentation {
    public static void clickUsesRemovedNativeRead(Context context) {
        try (Fixture fixture = new Fixture(context)) {
            Notification removed = fixture.notification(fixture.original, 11);
            Notification replacement = fixture.notification(fixture.replacement, 12);
            removed.deleteIntent = fixture.replacement;
            RecordingContext recording = new RecordingContext(context);
            NotificationRemovalRead.handleRemoved(recording, fixture.status(removed), NotificationListenerService.REASON_CLICK);
            check(fixture.originalReceived.await(5, TimeUnit.SECONDS), "removed native read was not delivered");
            check(fixture.originalReads.get() == 1 && fixture.replacementReads.get() == 0,
                    "click used deleteIntent or a replacement notification target");
            check(removed.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN) == 11
                    && replacement.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN) == 12,
                    "native read changed notification generations");
            recording.checkReset(11);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    public static void removalReadGuards(Context context) {
        try (Fixture fixture = new Fixture(context)) {
            Notification notification = fixture.notification(fixture.original, 11);
            for (int reason : new int[]{NotificationListenerService.REASON_CANCEL, NotificationListenerService.REASON_CANCEL_ALL,
                    NotificationListenerService.REASON_APP_CANCEL, NotificationListenerService.REASON_LISTENER_CANCEL,
                    NotificationListenerService.REASON_TIMEOUT}) {
                check(!NotificationRemovalRead.request(context, fixture.status(notification), reason),
                        "non-click removal requested another native read");
                RecordingContext recording = new RecordingContext(context);
                NotificationRemovalRead.handleRemoved(recording, fixture.status(notification), reason);
                if (reason == NotificationListenerService.REASON_CANCEL || reason == NotificationListenerService.REASON_CANCEL_ALL)
                    recording.checkReset(11);
                else check(recording.broadcasts == 0, "non-user removal reset history");
            }

            notification.flags |= Notification.FLAG_GROUP_SUMMARY;
            check(!NotificationRemovalRead.request(context, fixture.status(notification), NotificationListenerService.REASON_CLICK),
                    "summary requested a native read");
            notification.flags &= ~Notification.FLAG_GROUP_SUMMARY;
            notification.extras.remove(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN);
            check(!NotificationRemovalRead.request(context, fixture.status(notification), NotificationListenerService.REASON_CLICK),
                    "untracked notification requested a native read");

            notification = fixture.notification(fixture.original, 11);
            check(!NotificationRemovalRead.request(context, fixture.status(notification, "synthetic.other", Process.myUid()),
                    NotificationListenerService.REASON_CLICK), "foreign creator package was accepted");
            check(!NotificationRemovalRead.request(context, fixture.status(notification, context.getPackageName(), Process.myUid() + 1),
                    NotificationListenerService.REASON_CLICK), "foreign creator uid was accepted");

            Bundle conversation = notification.extras.getBundle("android.car.EXTENSIONS").getBundle("car_conversation");
            conversation.remove("on_read");
            notification.deleteIntent = fixture.original;
            check(!NotificationRemovalRead.request(context, fixture.status(notification), NotificationListenerService.REASON_CLICK),
                    "missing on_read reused an arbitrary deleteIntent");
            conversation.putParcelable("on_read", new Intent("synthetic.invalid_read"));
            check(!NotificationRemovalRead.request(context, fixture.status(notification), NotificationListenerService.REASON_CLICK),
                    "wrong native read type was accepted");
            check(fixture.originalReads.get() == 0 && fixture.replacementReads.get() == 0, "guarded action was sent");
        }
    }

    public static void cancelledRemovalReadReturnsNormally(Context context) {
        try (Fixture fixture = new Fixture(context)) {
            Notification notification = fixture.notification(fixture.original, 11);
            fixture.original.cancel();
            check(!NotificationRemovalRead.request(context, fixture.status(notification), NotificationListenerService.REASON_CLICK),
                    "cancelled native read was reported as requested");
            check(fixture.originalReads.get() == 0, "cancelled native read was delivered");
        }
    }

    public static void removalFailuresStillResetHistory(Context context) {
        for (int failure = 0; failure < 3; failure++) {
            try (Fixture fixture = new Fixture(context)) {
                Notification notification = fixture.notification(fixture.original, 11);
                RecordingContext recording = new RecordingContext(context);
                if (failure == 0) notification.extras.getBundle("android.car.EXTENSIONS")
                        .getBundle("car_conversation").remove("on_read");
                else if (failure == 1) fixture.original.cancel();
                else recording.failRead = true;
                NotificationRemovalRead.handleRemoved(recording, fixture.status(notification), NotificationListenerService.REASON_CLICK);
                recording.checkReset(11);
                check(fixture.originalReads.get() == 0 && fixture.replacementReads.get() == 0,
                        "failed read delivered a native action");
                if (failure == 2) check(recording.readFailures == 1, "runtime read failure was not exercised");
            }
        }
    }

    public static void removalResetFailureDoesNotSuppressRead(Context context) {
        try (Fixture fixture = new Fixture(context)) {
            RecordingContext recording = new RecordingContext(context);
            recording.failBroadcast = true;
            NotificationRemovalRead.handleRemoved(recording, fixture.status(fixture.notification(fixture.original, 11)),
                    NotificationListenerService.REASON_CLICK);
            check(recording.broadcasts == 1, "reset failure was not exercised");
            check(fixture.originalReceived.await(5, TimeUnit.SECONDS), "reset failure suppressed the native read");
            check(fixture.originalReads.get() == 1 && fixture.replacementReads.get() == 0,
                    "reset failure changed native read dispatch");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    /** Captures every cross-user send instead of delivering the production reset to WeChat. */
    private static final class RecordingContext extends ContextWrapper {
        int broadcasts, readFailures;
        boolean failRead, failBroadcast;
        Intent reset;
        UserHandle user;

        RecordingContext(Context context) { super(context); }

        @Override public ContentResolver getContentResolver() {
            if (failRead) {
                readFailures++;
                throw new IllegalStateException("Synthetic native read failure");
            }
            return super.getContentResolver();
        }

        @Override public void sendBroadcastAsUser(Intent intent, UserHandle user) {
            broadcasts++;
            reset = new Intent(intent);
            this.user = user;
            if (failBroadcast) throw new IllegalStateException("Synthetic reset failure");
        }

        void checkReset(long token) {
            check(broadcasts == 1 && reset != null, "read failure suppressed history reset");
            check(WeChatNotificationRemoval.ACTION_RESET_ROUND.equals(reset.getAction())
                    && WeChatDecorator.WECHAT_PACKAGE.equals(reset.getPackage()), "reset target changed");
            check(reset.getIntExtra(WeChatNotificationRemoval.EXTRA_NOTIFICATION_ID, -1) == 946
                    && reset.getLongExtra(WeChatNotificationRemoval.EXTRA_ROUND_TOKEN, 0) == token,
                    "reset did not preserve the removed generation");
            check(Process.myUserHandle().equals(user), "reset user changed");
        }
    }

    private static final class Fixture implements AutoCloseable {
        private static final String ORIGINAL_ACTION = "nevo.test.REMOVAL_READ_ORIGINAL";
        private static final String REPLACEMENT_ACTION = "nevo.test.REMOVAL_READ_REPLACEMENT";
        final Context context;
        final AtomicInteger originalReads = new AtomicInteger(), replacementReads = new AtomicInteger();
        final CountDownLatch originalReceived = new CountDownLatch(1);
        final PendingIntent original, replacement;
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (ORIGINAL_ACTION.equals(intent.getAction())) {
                    originalReads.incrementAndGet();
                    originalReceived.countDown();
                } else if (REPLACEMENT_ACTION.equals(intent.getAction())) replacementReads.incrementAndGet();
            }
        };

        Fixture(Context context) {
            this.context = context;
            IntentFilter filter = new IntentFilter(ORIGINAL_ACTION);
            filter.addAction(REPLACEMENT_ACTION);
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            PendingIntent createdOriginal = null;
            try {
                createdOriginal = PendingIntent.getBroadcast(context, 946,
                        new Intent(ORIGINAL_ACTION).setPackage(context.getPackageName()),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                replacement = PendingIntent.getBroadcast(context, 947,
                        new Intent(REPLACEMENT_ACTION).setPackage(context.getPackageName()),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                original = createdOriginal;
            } catch (RuntimeException failure) {
                try { if (createdOriginal != null) createdOriginal.cancel(); }
                finally { context.unregisterReceiver(receiver); }
                throw failure;
            }
        }

        Notification notification(PendingIntent read, long token) {
            Notification notification = new Notification.Builder(context, "synthetic-removal")
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Synthetic peer")
                    .setContentText("Synthetic message").build();
            Bundle conversation = new Bundle();
            conversation.putParcelable("on_read", read);
            Bundle extensions = new Bundle();
            extensions.putBundle("car_conversation", conversation);
            notification.extras.putBundle("android.car.EXTENSIONS", extensions);
            notification.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, token);
            return notification;
        }

        StatusBarNotification status(Notification notification) {
            return status(notification, context.getPackageName(), Process.myUid());
        }

        @SuppressWarnings("deprecation")
        @SuppressLint("NewApi") // The production getUid() call is also present on supported pre-29 framework runtimes.
        StatusBarNotification status(Notification notification, String packageName, int uid) {
            StatusBarNotification status = new StatusBarNotification(packageName, packageName, 946, "synthetic", uid, 0, 0,
                    notification, Process.myUserHandle(), 100);
            check(packageName.equals(status.getPackageName()) && status.getUid() == uid,
                    "synthetic notification package or uid differs from its creator fixture");
            return status;
        }

        @Override public void close() {
            try { original.cancel(); }
            finally {
                try { replacement.cancel(); }
                finally { context.unregisterReceiver(receiver); }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private NotificationRemovalReadInstrumentation() {}
}
