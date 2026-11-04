package com.oasisfeng.nevo.xposed;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.oasisfeng.nevo.decorators.wechat.WeChatDecorator;
import com.oasisfeng.nevo.decorators.wechat.WeChatNotificationRemoval;

/** Requests the removed notification's native read action; the caller selects the host package. */
final class NotificationRemovalRead {
    private static final String TAG = "WeChat.Identity";

    static boolean shouldRequest(int reason, int flags, long token) {
        return reason == NotificationListenerService.REASON_CLICK && token != 0
                && (flags & Notification.FLAG_GROUP_SUMMARY) == 0;
    }

    static boolean sameCreator(String postingPackage, int postingUid, String creatorPackage, int creatorUid) {
        return postingPackage != null && postingPackage.equals(creatorPackage) && postingUid == creatorUid;
    }

    // The production caller runs in SystemUI, whose manifest grants INTERACT_ACROSS_USERS_FULL.
    // The module manifest is not the runtime permission source for this call.
    @SuppressLint("MissingPermission")
    static void handleRemoved(Context context, StatusBarNotification removed, int reason) {
        if (context == null || removed == null || !WeChatNotificationRemoval.shouldResetRound(reason)) return;
        Notification notification = removed.getNotification();
        if (notification == null || notification.extras == null) return;
        long token = notification.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN);
        if (token == 0) return;
        request(context, removed, reason);
        Intent reset = new Intent(WeChatNotificationRemoval.ACTION_RESET_ROUND)
                .setPackage(WeChatDecorator.WECHAT_PACKAGE)
                .putExtra(WeChatNotificationRemoval.EXTRA_NOTIFICATION_ID, removed.getId())
                .putExtra(WeChatNotificationRemoval.EXTRA_ROUND_TOKEN, token);
        try {
            context.sendBroadcastAsUser(reset, removed.getUser());
            if (BuildConfig.DEBUG) Log.d(TAG, "source=removal_broadcast id=" + removed.getId()
                    + " reason=" + reason + " tokenPresent=true");
        } catch (RuntimeException failure) {
            Log.w(TAG, "source=removal_broadcast_failed reason=" + reason + " type=" + failure.getClass().getSimpleName());
        }
    }

    /** A true result means send returned normally, not that WeChat completed its read operation. */
    @SuppressLint("NewApi") // getUid() already exists as a public hidden method on API 26; the caller runs in SystemUI.
    static boolean request(Context context, StatusBarNotification removed, int reason) {
        if (context == null || removed == null) return false;
        try {
            Notification notification = removed.getNotification();
            if (notification == null || notification.extras == null || !shouldRequest(reason, notification.flags,
                    notification.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN))) return false;
            Bundle extensions = notification.extras.getBundle("android.car.EXTENSIONS");
            Bundle conversation = extensions == null ? null : extensions.getBundle("car_conversation");
            Parcelable nativeRead = conversation == null ? null : conversation.getParcelable("on_read");
            if (!(nativeRead instanceof PendingIntent)) {
                skipped("missing_native_action");
                return false;
            }
            PendingIntent action = (PendingIntent) nativeRead;
            if (!sameCreator(removed.getPackageName(), removed.getUid(), action.getCreatorPackage(), action.getCreatorUid())) {
                skipped("creator_mismatch");
                return false;
            }
            action.send(context, 0, new Intent().setPackage(action.getCreatorPackage())
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES));
            if (BuildConfig.DEBUG) Log.d(TAG, "source=removal_read_requested id=" + removed.getId());
            return true;
        } catch (PendingIntent.CanceledException cancelled) {
            Log.w(TAG, "source=removal_read_failed reason=cancelled");
        } catch (RuntimeException failure) {
            Log.w(TAG, "source=removal_read_failed type=" + failure.getClass().getSimpleName());
        }
        return false;
    }

    private static void skipped(String reason) {
        if (BuildConfig.DEBUG) Log.d(TAG, "source=removal_read_skipped reason=" + reason);
    }

    private NotificationRemovalRead() {}
}
