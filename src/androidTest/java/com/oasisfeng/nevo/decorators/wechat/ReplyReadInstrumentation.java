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
    static void nativeReplyReadBeforeFailure(Context context) {
        for (int result : new int[]{0, 7, -1}) {
            try (Fixture fixture = new Fixture(context, result, false)) {
                if (result == -1) fixture.reply.cancel();
                Notification n = fixture.notification();
                fixture.builder.attachReplyAction(740, n);
                RemoteInput input = n.actions[0].getRemoteInputs()[0];
                Intent fill = new Intent();
                Bundle results = new Bundle();
                results.putCharSequence(input.getResultKey(), "B");
                RemoteInput.addResultsToIntent(new RemoteInput[]{input}, fill, results);
                n.actions[0].actionIntent.send(context, 0, fill);
                fixture.verify(result == 0);
            } catch (Exception failure) { throw new AssertionError(failure); }
        }
    }

    static void syntheticReplyReadBeforeFailure(Context context) {
        for (int result : new int[]{0, 7, -1}) {
            try (Fixture fixture = new Fixture(context, result, true)) {
                fixture.builder.attachReplyAction(740, fixture.notification());
                Intent proxy = new Intent().setData(Uri.fromParts("id", "740", null))
                        .putExtra(WeChatReplyProfile.USERNAME_EXTRA, "synthetic-peer");
                RemoteInput input = new RemoteInput.Builder("synthetic_reply_result_key").build();
                Bundle results = new Bundle();
                results.putCharSequence(input.getResultKey(), "B");
                RemoteInput.addResultsToIntent(new RemoteInput[]{input}, proxy, results);
                java.lang.reflect.Field field = MessagingBuilder.class.getDeclaredField("mSyntheticReplyReceiver");
                field.setAccessible(true);
                ((BroadcastReceiver) field.get(fixture.builder)).onReceive(context, proxy);
                fixture.verify(result == 0);
            } catch (Exception failure) { throw new AssertionError(failure); }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Context context;
        final int result;
        final AtomicInteger reads = new AtomicInteger(), sent = new AtomicInteger(), aborted = new AtomicInteger();
        final CountDownLatch nativeRead = new CountDownLatch(1), completed = new CountDownLatch(1);
        final String readAction = "nevo.test.REPLY_READ", replyAction = "nevo.test.REPLY_SEND";
        final PendingIntent read, reply;
        final MessagingBuilder builder;
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (readAction.equals(intent.getAction())) nativeRead.countDown();
                else {
                    check(reads.get() == 1, "reply dispatched before local read");
                    setResultCode(result);
                }
            }
        };

        Fixture(Context context, int result, boolean synthetic) {
            this.context = context;
            this.result = result;
            IntentFilter filter = new IntentFilter(readAction);
            filter.addAction(replyAction);
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            read = PendingIntent.getBroadcast(context, 740, new Intent(readAction).setPackage(context.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            reply = PendingIntent.getBroadcast(context, 741, new Intent(replyAction).setPackage(context.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            builder = new MessagingBuilder(context, new MessagingBuilder.Controller() {
                @Override public void recastNotification(int id, WeChatDecorator.ModifyNotification... modifies) {}
                @Override public void onReplyRead(int id, MessagingBuilder.ReplyTarget target) { reads.incrementAndGet(); }
                @Override public void onReplySent(int id, MessagingBuilder.ReplyTarget target, CharSequence text, long time, String replyId) {
                    sent.incrementAndGet(); completed.countDown();
                }
                @Override public long beginNotificationReply(int id) { return 1; }
                @Override public void abortNotificationReply(long reservation) { aborted.incrementAndGet(); completed.countDown(); }
            }) {
                @Override boolean dispatchSyntheticReply(Context context, Intent intent) {
                    check(synthetic && reads.get() == 1, "synthetic dispatch preceded local read");
                    if (result == -1) throw new IllegalStateException("synthetic test dispatch failure");
                    return result == 0;
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

        void verify(boolean success) throws InterruptedException {
            check(nativeRead.await(5, TimeUnit.SECONDS), "native read was skipped after a failed reply");
            check(completed.await(5, TimeUnit.SECONDS), "reply outcome was not handled");
            check(reads.get() == 1, "local read was not performed exactly once");
            check(sent.get() == (success ? 1 : 0), "failed reply was recorded as sent");
            check(aborted.get() == (success ? 0 : 1), "failed reply reservation was not aborted");
        }

        @Override public void close() {
            builder.close(); read.cancel(); reply.cancel(); context.unregisterReceiver(receiver);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
