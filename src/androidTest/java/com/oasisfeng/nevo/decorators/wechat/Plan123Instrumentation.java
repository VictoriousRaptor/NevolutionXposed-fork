package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;
import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Tests real Android serialization and reply forwarding without requiring WeChat. */
final class Plan123Instrumentation {
    static void recallSequence(Context context) {
        for (boolean group : new boolean[]{false, true}) {
            Conversation c = conversation(group);
            Notification a = normalize(c, raw(context, "A", 100, 1), null);
            Notification b = normalize(c, raw(context, "B", 200, 2), a);
            Notification staleCopy = b.clone();
            Notification recall = raw(context, "\"Bob\" 撤回了一条消息", 300, 2);
            recall.extras.putBoolean(NotificationMessages.RECALL_PROMPT, true);
            NotificationMessages.markRecall(recall, 2);
            Notification pruned = normalize(c, recall, b);
            texts(c, pruned, "A");
            check("A".contentEquals(pruned.extras.getCharSequence(Notification.EXTRA_TEXT)), "fallback retained recall");
            check(pruned.extras.getBundle(NotificationMessages.ROUND).getLong("latestPeer") == 200,
                    "recall advanced peer watermark");
            Notification d = normalize(c, raw(context, "D", 400, 4), pruned);
            texts(c, d, "A", "D");
            texts(c, normalize(c, d, b), "A", "D");
            texts(c, normalize(c, staleCopy, d), "A");
            Notification preview = new Notification();
            NotificationMessages.copyIdentityExtras(d, preview);
            texts(c, normalize(c, preview, b), "A", "D");
        }
    }

    static void recallWithoutIdentity(Context context) {
        Conversation c = conversation(false);
        Notification a = normalize(c, raw(context, "A", 100, 0), null);
        Notification b = normalize(c, raw(context, "B", 200, 0), a);
        Notification recall = raw(context, "撤回了一条消息", 300, 0);
        recall.extras.putBoolean(NotificationMessages.RECALL_PROMPT, true);
        NotificationMessages.markRecall(recall, 2);
        texts(c, normalize(c, recall, b), "A", "B");
        // Identical text and timestamp are insufficient to remove an untagged message.
        Notification untagged = normalize(c, raw(context, "B", 200, 0), null);
        NotificationMessages.markRecall(untagged, 2);
        texts(c, untagged, "B");
    }

    static void recallSameTextAndTime(Context context) {
        Conversation c = conversation(false);
        Notification a = normalize(c, raw(context, "same", 100, 1), null);
        Notification b = normalize(c, raw(context, "same", 100, 2), a);
        texts(c, b, "same", "same");
        NotificationMessages.markRecall(b, 2);
        texts(c, normalize(c, b, null), "same");
        NotificationMessages.markRecall(b, 1);
        Notification empty = normalize(c, b, null);
        texts(c, empty);
        check(empty.extras.getParcelableArray(Notification.EXTRA_MESSAGES).length == 0, "empty snapshot retained old array");
    }

    static void recallSelfAndRoundMetadata(Context context) {
        Conversation c = conversation(false);
        Notification n = normalize(c, raw(context, "A", 100, 1), null);
        NotificationMessages.recordReply(n, "self", 150, "reply");
        Parcelable[] messages = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        ((Bundle) messages[messages.length - 1]).getBundle("extras").putLong(NotificationMessages.SERVER_ID, 2);
        NotificationMessages.markRecall(n, 2);
        Notification pruned = normalize(c, n, null);
        texts(c, pruned, "A");
        check(pruned.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) == null, "recalled self in input history");
        check(pruned.extras.getBundle(NotificationMessages.ROUND).getStringArrayList("pending").isEmpty(), "recalled reply ID in round");
    }

    static void recallReusedNotificationId(Context context) {
        Conversation old = conversation(false);
        Notification a = normalize(old, raw(context, "old", 100, 1), null);
        NotificationMessages.markRecall(a, 1);
        Conversation fresh = conversation(false); fresh.title = "Carol"; fresh.key = "carol";
        Notification n = raw(context, "new", 200, 1); n.extras.putCharSequence(Notification.EXTRA_TITLE, "Carol");
        Notification current = normalize(fresh, n, a);
        texts(fresh, current, "new");
        check(current.extras.getLongArray(NotificationMessages.RECALLED_IDS).length == 0, "recall crossed conversation");
    }

    static void emptyRecallStaysSuppressed(Context context) {
        WeChatDecorator.Local local = new WeChatDecorator.Local("WeChatDecorator");
        local.onCreate(context.getSharedPreferences("identity-test", Context.MODE_PRIVATE));
        try {
            Notification recall = raw(context, "撤回了一条消息", 100, 1);
            recall.extras.putBoolean(NotificationMessages.RECALL_PROMPT, true);
            android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
            check(local.apply(manager, null, 810, recall) == com.oasisfeng.nevo.sdk.Decorating.StopPost,
                    "empty recall was published");
            check(local.apply(manager, null, 810, recall.clone()) == com.oasisfeng.nevo.sdk.Decorating.StopPost,
                    "repeated empty recall was revived");
        } finally { local.onDestroy(); }
    }

    static void replyEligibility(Context context) {
        MessagingBuilder builder = new MessagingBuilder(context, context, (id, modifies) -> {});
        PendingIntent nativeReply = PendingIntent.getBroadcast(context, 802,
                new Intent("com.oasisfeng.nevo.test.NATIVE").setPackage(context.getPackageName()), flags());
        try {
            for (String ticker : new String[]{null, "", "好友请求", ":hello"}) {
                Notification n = car(context, nativeReply); n.tickerText = ticker;
                check(builder.buildFromExtender(conversation(false), 802, n, "Alice", Collections.emptyList()) != null,
                        "non-chat content stopped rebuilding");
                check(n.actions == null || n.actions.length == 0, "non-chat gained car reply");
                check(!builder.attachReplyAction(802, n), "non-chat gained media reply");
                Notification.Action original = new Notification.Action.Builder(null, "native", nativeReply)
                        .addRemoteInput(new RemoteInput.Builder("native-key").build()).build();
                n.actions = new Notification.Action[]{original};
                builder.buildFromExtender(conversation(false), 802, n, "Alice", Collections.emptyList());
                check(n.actions.length == 1 && n.actions[0] == original, "non-chat native action was proxied or deleted");
            }
            Notification media = car(context, nativeReply); media.tickerText = "Group: Bob: [文件]";
            check(builder.attachReplyAction(802, media), "chat media lost reply");
            Notification noNative = raw(context, "hello", 100, 0);
            noNative.tickerText = "Alice: hello";
            noNative.contentIntent = nativeReply;
            check(builder.buildFromExtender(conversation(false), 803, noNative, "Alice", Collections.emptyList()) != null,
                    "chat without native objects lost message rebuilding");
            check(noNative.actions == null || noNative.actions.length == 0,
                    "ordinary test process exposed an unverified synthetic reply");
        } finally { builder.close(); nativeReply.cancel(); }
    }

    static void nativeReplyForwarding(Context context) {
        final Bundle[] received = {null};
        CountDownLatch forwarded = new CountDownLatch(1), recorded = new CountDownLatch(1);
        BroadcastReceiver target = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                received[0] = RemoteInput.getResultsFromIntent(intent); forwarded.countDown();
            }
        };
        String action = "com.oasisfeng.nevo.test.FORWARD";
        ContextCompat.registerReceiver(context, target, new IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED);
        PendingIntent nativeReply = PendingIntent.getBroadcast(context, 804, new Intent(action).setPackage(context.getPackageName()), flags());
        Notification history = normalize(conversation(false), raw(context, "A", 100, 1), null);
        NotificationMessages.recordReply(history, "earlier", 150, "earlier");
        MessagingBuilder builder = new MessagingBuilder(context, context, (id, modifies) -> {
            for (WeChatDecorator.ModifyNotification modify : modifies) modify.modify(history);
            recorded.countDown();
        });
        try {
            Intent proxy = new Intent().setData(android.net.Uri.fromParts("id", "804", null))
                    .putExtra("pending_intent", nativeReply).putExtra("result_key", "native-key")
                    .putExtra("reply_prefix", "@Bob\u2005");
            Bundle results = new Bundle(); results.putCharSequence("native-key", "hello");
            RemoteInput.addResultsToIntent(new RemoteInput[]{new RemoteInput.Builder("native-key").build()}, proxy, results);
            java.lang.reflect.Field field = MessagingBuilder.class.getDeclaredField("mReplyReceiver"); field.setAccessible(true);
            ((BroadcastReceiver) field.get(builder)).onReceive(context, proxy);
            check(forwarded.await(5, TimeUnit.SECONDS), "native results not forwarded");
            check("@Bob\u2005hello".contentEquals(received[0].getCharSequence("native-key")), "mention prefix/result key lost");
            check(recorded.await(5, TimeUnit.SECONDS), "reply success not recorded");
            CharSequence[] replies = history.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY);
            check(replies.length == 2 && "earlier".contentEquals(replies[1]), "continuous reply history lost");
        } catch (Exception error) { throw new AssertionError(error); }
        finally { builder.close(); nativeReply.cancel(); context.unregisterReceiver(target); }
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
    }
    private static Notification car(Context context, PendingIntent reply) {
        Notification.CarExtender.UnreadConversation unread = new Notification.CarExtender.Builder("Alice")
                .addMessage("hello").setLatestTimestamp(100).setReplyAction(reply, new RemoteInput.Builder("native-key").build()).build();
        return new Notification.Builder(context, "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Alice").setContentText("hello").setWhen(100)
                .extend(new Notification.CarExtender().setUnreadConversation(unread)).build();
    }
    private static Conversation conversation(boolean group) {
        Conversation c = new Conversation(1); c.title = "Alice"; c.key = group ? "group@chatroom" : "alice";
        c.setType(group ? Conversation.TYPE_GROUP_CHAT : Conversation.TYPE_DIRECT_MESSAGE); return c;
    }
    private static Notification raw(Context context, String text, long time, long serverId) {
        Notification n = new Notification.Builder(context, "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Alice").setContentText(text).setWhen(time).build();
        if (serverId > 0) n.extras.putLong(NotificationMessages.SERVER_ID, serverId);
        return n;
    }
    private static Notification normalize(Conversation c, Notification n, Notification old) {
        MessagingStyle style = new MessagingStyle(new Person.Builder().setName("我").build());
        List<Message> messages = NotificationMessages.rebuild(c, n, old == null ? Collections.emptyList() : Collections.singletonList(old));
        for (Message message : messages) style.addMessage(message);
        NotificationMessages.stamp(c, n); MessagingBuilder.flatIntoExtras(style, n.extras);
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle"); return n;
    }
    private static void texts(Conversation c, Notification n, String... expected) {
        List<Message> messages = NotificationMessages.rebuild(c, n, Collections.emptyList());
        check(messages.size() == expected.length, "unexpected history length " + messages.size());
        for (int i = 0; i < expected.length; i++) check(expected[i].contentEquals(messages.get(i).getText()), "history mismatch at " + i);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
