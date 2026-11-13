package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Framework checks use synthetic messages and local targets, without invoking WeChat. */
final class ReplyReadInstrumentation {
    static void nativeReplyDoesNotReuseReadTarget(Context context) {
        try (Fixture fixture = new Fixture(context, 0, false)) {
            fixture.build(fixture.notification());
            Notification current = fixture.notification();
            Bundle car = current.extras.getBundle("android.car.EXTENSIONS").getBundle("car_conversation");
            car.remove("on_read");
            fixture.currentRead = null;
            fixture.build(current);
            send(context, current);
            check(fixture.dispatched.await(5, TimeUnit.SECONDS), "reply was not dispatched");
            check(fixture.nativeRead.getCount() == 1, "old notification read target was reused");
            check(fixture.completed.await(5, TimeUnit.SECONDS) && fixture.completions.get() == 1, "missing read action blocked UI completion");
            check(fixture.submissions.get() == 1 && fixture.dispatches.get() == 1, "missing read action blocked submission");
            check(fixture.conversation.round.endThroughToken == 1, "submission did not end the captured round");
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    static void nativeReplyReadBeforeFailure(Context context) {
        for (int result : new int[]{0, 7, -1}) {
            try (Fixture fixture = new Fixture(context, result, false)) {
                if (result == -1) fixture.reply.cancel();
                Notification n = fixture.notification();
                fixture.build(n);
                send(context, n);
                fixture.verify();
            } catch (Exception failure) { throw new AssertionError(failure); }
        }
    }

    static void syntheticReplyReadBeforeFailure(Context context) {
        for (int result : new int[]{0, 7, -1}) {
            try (Fixture fixture = new Fixture(context, result, true)) {
                fixture.build(fixture.notification());
                Intent proxy = new Intent().setData(Uri.fromParts("id", "740", null))
                        .putExtra(WeChatReplyProfile.USERNAME_EXTRA, "synthetic-peer");
                RemoteInput input = new RemoteInput.Builder("synthetic_reply_result_key").build();
                Bundle results = new Bundle();
                results.putCharSequence(input.getResultKey(), "B");
                RemoteInput.addResultsToIntent(new RemoteInput[]{input}, proxy, results);
                java.lang.reflect.Field field = MessagingBuilder.class.getDeclaredField("mSyntheticReplyReceiver");
                field.setAccessible(true);
                ((BroadcastReceiver) field.get(fixture.builder)).onReceive(context, proxy);
                fixture.verify();
            } catch (Exception failure) { throw new AssertionError(failure); }
        }
    }

    private static void send(Context context, Notification notification) throws PendingIntent.CanceledException {
        RemoteInput input = notification.actions[0].getRemoteInputs()[0];
        Intent fill = new Intent();
        Bundle results = new Bundle();
        results.putCharSequence(input.getResultKey(), "B");
        RemoteInput.addResultsToIntent(new RemoteInput[]{input}, fill, results);
        notification.actions[0].actionIntent.send(context, 0, fill);
    }

    private static final class Fixture implements AutoCloseable {
        final Context context;
        final int result;
        final boolean synthetic;
        final AtomicInteger submissions = new AtomicInteger(), dispatches = new AtomicInteger(), completions = new AtomicInteger();
        final CountDownLatch nativeRead = new CountDownLatch(1), dispatched = new CountDownLatch(1), completed = new CountDownLatch(1);
        final String readAction = "nevo.test.REPLY_READ", replyAction = "nevo.test.REPLY_SEND";
        final PendingIntent read, reply;
        PendingIntent currentRead;
        final ConversationManager.Conversation conversation = new ConversationManager.Conversation(740);
        final MessagingBuilder builder;
        volatile Notification latest;
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                check(submissions.get() == 1 && conversation.round.endThroughToken == 1,
                        "native read or dispatch preceded submission boundary");
                if (readAction.equals(intent.getAction())) {
                    advanceWithC();
                    nativeRead.countDown();
                } else {
                    advanceWithC();
                    dispatches.incrementAndGet();
                    setResultCode(result);
                    dispatched.countDown();
                }
            }
        };

        Fixture(Context context, int result, boolean synthetic) {
            this.context = context;
            this.result = result;
            this.synthetic = synthetic;
            conversation.title = "Alice";
            conversation.key = "synthetic-peer";
            IntentFilter filter = new IntentFilter(readAction);
            filter.addAction(replyAction);
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            read = PendingIntent.getBroadcast(context, 740, new Intent(readAction).setPackage(context.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            reply = PendingIntent.getBroadcast(context, 741, new Intent(replyAction).setPackage(context.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            currentRead = read;
            builder = new MessagingBuilder(context, new MessagingBuilder.Controller() {
                @Override public ReplyTarget replyTarget(int id) { return new ReplyTarget(1, conversation, currentRead); }
                @Override public void onReplyFinished(int id, ReplyTarget target) {
                    check(submissions.get() == 1 && target != null && target.token() == 1
                            && target.conversation() == conversation, "completion used a newer reply target");
                    completions.incrementAndGet();
                    completed.countDown();
                }
                @Override public void onReplySubmitted(int id, ReplyTarget target) {
                    check(nativeRead.getCount() == 1 && dispatches.get() == 0,
                            "submission callback followed native read or dispatch");
                    submissions.incrementAndGet();
                    NotificationMessages.clearReadHistory(target.conversation(), latest, target.token());
                    check(NotificationMessages.rebuild(conversation, latest, java.util.Collections.emptyList()).isEmpty(),
                            "submitted round still contained A");
                }
            }) {
                @Override boolean dispatchSyntheticReply(Context ignored, Intent intent) {
                    check(synthetic && submissions.get() == 1 && conversation.round.endThroughToken == 1,
                            "synthetic dispatch preceded submission boundary");
                    advanceWithC();
                    dispatches.incrementAndGet();
                    try {
                        if (result == -1) throw new IllegalStateException("synthetic test dispatch failure");
                        return result == 0;
                    } finally { dispatched.countDown(); }
                }
            };
        }

        Notification notification() {
            Notification.CarExtender.UnreadConversation car = new Notification.CarExtender.Builder("Alice")
                    .addMessage("A").setLatestTimestamp(100).setReadPendingIntent(read)
                    .setReplyAction(reply, new RemoteInput.Builder("native-key").build()).build();
            return new Notification.Builder(context, "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Alice").setContentText("[动画表情]").setTicker("Alice: [动画表情]")
                    .extend(new Notification.CarExtender().setUnreadConversation(car)).build();
        }

        void build(Notification notification) {
            notification.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 1);
            androidx.core.app.NotificationCompat.MessagingStyle style =
                    builder.buildConversation(conversation, 740, notification, java.util.Collections.emptyList());
            NotificationMessages.stamp(conversation, notification);
            NotificationMessages.flatIntoExtras(style, notification.extras);
            latest = notification;
        }

        synchronized void advanceWithC() {
            if (latest.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN) == 2) return;
            Notification c = new Notification.Builder(context, "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Alice").setContentText("C").setTicker("Alice: C").setWhen(0).build();
            c.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 2);
            androidx.core.app.NotificationCompat.MessagingStyle style = new androidx.core.app.NotificationCompat.MessagingStyle(
                    new androidx.core.app.Person.Builder().setName("Synthetic self").build());
            for (androidx.core.app.NotificationCompat.MessagingStyle.Message message :
                    NotificationMessages.rebuild(conversation, c, java.util.Collections.singletonList(latest))) style.addMessage(message);
            NotificationMessages.stamp(conversation, c);
            NotificationMessages.flatIntoExtras(style, c.extras);
            latest = c;
        }

        void verify() throws InterruptedException {
            check(nativeRead.await(5, TimeUnit.SECONDS), "native read was skipped after a failed reply");
            if (synthetic || result != -1)
                check(dispatched.await(5, TimeUnit.SECONDS), "reply was not dispatched");
            check(completed.await(5, TimeUnit.SECONDS) && completions.get() == 1,
                    "send processing did not finish the notification UI on success/failure");
            check(submissions.get() == 1, "valid send did not submit exactly once");
            check(dispatches.get() == ((!synthetic && result == -1) ? 0 : 1), "unexpected dispatch count");
            check(conversation.round.endThroughToken == 1, "dispatch result restored the ended round");
            java.util.List<androidx.core.app.NotificationCompat.MessagingStyle.Message> messages =
                    NotificationMessages.rebuild(conversation, latest, java.util.Collections.emptyList());
            check(messages.size() == 1 && "C".contentEquals(messages.get(0).getText()),
                    "dispatch failure or late result restored A or cleared the new C");
        }

        @Override public void close() {
            builder.close(); read.cancel(); reply.cancel(); context.unregisterReceiver(receiver);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
