package com.oasisfeng.nevo.decorators.wechat;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Parcelable;
import android.os.Bundle;
import com.oasisfeng.nevo.sdk.Decorating;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Uses synthetic notifications only. Queued tasks are cancelled on the main thread before they can post. */
final class InitialAvatarInstrumentation {
    public static final class ColdAvatar {
        public static Bitmap a(String key) { return null; }
    }

    static void cancellationAndReplacement(Instrumentation instrumentation) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            Context context = instrumentation.getTargetContext();
            WeChatDecorator.Local local = new WeChatDecorator.Local("WeChatDecorator");
            local.onCreate(context.getSharedPreferences("identity-test", Context.MODE_PRIVATE));
            try {
                Field avatarsField = WeChatDecorator.Local.class.getDeclaredField("avatars");
                avatarsField.setAccessible(true);
                Field lookup = WeChatAvatar.class.getDeclaredField("lookup");
                lookup.setAccessible(true);
                lookup.set(avatarsField.get(local), ColdAvatar.class.getMethod("a", String.class));
                Field gateField = WeChatDecorator.Local.class.getDeclaredField("initialAvatars");
                gateField.setAccessible(true);
                InitialAvatarGate<?, ?, ?> gate = (InitialAvatarGate<?, ?, ?>) gateField.get(local);
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                Notification a = raw(context, 1001, "A");
                Notification b = raw(context, 1002, "B");
                check(local.apply(manager, null, 9890, a) == Decorating.Processed, "A not normalized");
                check(local.apply(manager, null, 9890, b) == Decorating.Processed, "B not normalized");
                check(local.beforePost(manager, null, 9890, b, true), "cold B not deferred");
                check(local.beforePost(manager, null, 9890, a, true), "stale A was allowed to post");
                check(gate.keys().size() == 1, "stale A removed pending B");
                local.onCancel(null, 9890);
                check(gate.keys().isEmpty(), "cancel did not clear queue");

                Notification c = raw(context, 1003, "C");
                local.apply(manager, "synthetic", 9891, c);
                check(local.beforePost(manager, "synthetic", 9891, c, true), "tagged cold notification not deferred");
                local.onCancel(null, 9891);
                check(gate.keys().size() == 1, "wrong tag cancelled notification");
                local.onCancelAll();
                check(gate.keys().isEmpty(), "cancelAll did not clear queue");

                Notification d = raw(context, 1004, "D");
                local.apply(manager, null, 9892, d);
                check(!local.beforePost(manager, null, 9892, d, false), "missing cancel hook swallowed notification");
            } catch (Throwable error) { failure.set(error); }
            finally { local.onCancelAll(); local.onDestroy(); }
        });
        if (failure.get() != null) throw new AssertionError("Initial avatar queue integration", failure.get());
    }

    static void recallAndAlert(Context context) {
        WeChatDecorator.Local local = new WeChatDecorator.Local("WeChatDecorator");
        local.onCreate(context.getSharedPreferences("identity-test", Context.MODE_PRIVATE));
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            Notification a = raw(context, 2001, "A");
            Notification b = raw(context, 2002, "B");
            local.apply(manager, null, 9893, a);
            local.apply(manager, null, 9893, b);
            NotificationMessages.markRecall(b, 2002);
            Bitmap avatar = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Notification outgoing = WeChatDecorator.Local.prepareInitialAvatar(context, b, avatar);
            check(outgoing.getLargeIcon() != null, "first post missing avatar");
            check((outgoing.flags & Notification.FLAG_ONLY_ALERT_ONCE) == (b.flags & Notification.FLAG_ONLY_ALERT_ONCE),
                    "first post alert flag changed");
            check(!b.extras.getBoolean(NotificationMessages.RECALL_PROMPT), "source snapshot mutated");
            check(local.apply(manager, null, 9893, outgoing) == Decorating.Processed, "remaining unread A suppressed");
            Parcelable[] messages = outgoing.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            check(messages != null && messages.length == 1, "recalled B remained or unread A lost");
            check("A".contentEquals(((Bundle) messages[0]).getCharSequence("text")), "wrong remaining message");

            Notification only = raw(context, 2003, "Only");
            local.apply(manager, null, 9894, only);
            NotificationMessages.markRecall(only, 2003);
            check(local.apply(manager, null, 9894, WeChatDecorator.Local.prepareInitialAvatar(context, only, null))
                    == Decorating.StopPost, "sole recalled message was not suppressed");
        } finally { local.onDestroy(); }
    }

    private static Notification raw(Context context, long serverId, String text) {
        Notification n = new Notification.Builder(context, "identity-test")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Synthetic peer")
                .setContentText(text).setTicker("Synthetic peer: " + text).setWhen(serverId).build();
        n.extras.putBoolean(WeChatRecallEvents.VERIFIED_KEY, true);
        n.extras.putString(NotificationMessages.KEY, "synthetic-avatar-peer");
        n.extras.putLong(NotificationMessages.SERVER_ID, serverId);
        return n;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
