package com.oasisfeng.nevo.sdk;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Parcel;

import com.oasisfeng.nevo.decorators.wechat.WeChatNotificationRemoval;

/** Exercises the production LocalDecorator adapter with actual Android notification actions. */
public final class NotificationArchiveInstrumentation extends NevoDecoratorService.LocalDecorator {
    private NotificationArchiveInstrumentation() { super("test"); }

    public static void replyActionSurvivesRemovalAndRebuild(Context context) {
        final int id = 982734;
        PendingIntent target = PendingIntent.getBroadcast(context, id,
                new Intent("nevo.test.REPLY").setPackage(context.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
        try {
            clearArchivedNotifications(id);
            for (int round = 1; round <= 3; round++) {
                Notification notification = new Notification.Builder(context, "test")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .addAction(new Notification.Action.Builder(null, "Reply", target)
                                .addRemoteInput(new RemoteInput.Builder("reply").setAllowFreeFormInput(true).build())
                                .build()).build();
                notification.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, round);
                cache(id, notification);
                cache(id, notification); // Reply recast of the same notification.
                Notification rebuilt = Notification.Builder.recoverBuilder(context, notification).build();
                replaceCachedNotification(id, notification, rebuilt);
                if (getArchivedNotification(id) != rebuilt) throw new AssertionError("Rebuild not cached");
                final long token = round;
                if (clearArchivedNotificationsIf(id, n -> WeChatNotificationRemoval.tokenMatches(
                        n.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN), token - 1)))
                    throw new AssertionError("Old removal deleted current notification");
                Parcel parcel = Parcel.obtain();
                try {
                    getArchivedNotification(id).writeToParcel(parcel, 0);
                    parcel.setDataPosition(0);
                    Notification outgoing = Notification.CREATOR.createFromParcel(parcel);
                    if (outgoing.actions == null || outgoing.actions.length != 1
                            || !outgoing.actions[0].getRemoteInputs()[0].getAllowFreeFormInput())
                        throw new AssertionError("Reply action lost");
                } finally { parcel.recycle(); }
                if (!clearArchivedNotificationsIf(id, n -> WeChatNotificationRemoval.tokenMatches(
                        n.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN), token)))
                    throw new AssertionError("Current removal rejected");
                if (getArchivedNotification(id) != null) throw new AssertionError("History not cleared");
            }
        } finally {
            clearArchivedNotifications(id);
            target.cancel();
        }
    }
}
