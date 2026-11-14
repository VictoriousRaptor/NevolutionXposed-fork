package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcel;

import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Round boundaries use captured notification generations; timestamps only describe displayed messages. */
final class ConversationRoundInstrumentation {
    static void submissionEndsRound(Context context) {
        Conversation c = conversation(false);
        Notification a = normalize(c, raw(context, "A", 100, 1, 10));
        Notification b = normalize(c, raw(context, "B", 200, 2, 20), a);
        texts(c, b, "A", "B");
        NotificationMessages.clearReadHistory(c, b, 20);
        texts(c, b);
        Notification next = normalize(c, raw(context, "C", 0, 3, 30), a, b);
        texts(c, next, "C");
        Notification later = normalize(c, raw(context, "D", 1, 4, 40), a, b, next);
        texts(c, later, "C", "D");
        check(later.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) == null,
                "ended round restored local reply text");
    }

    static void replyCompletionPresentation(Context context) {
        Conversation c = conversation(false);
        Notification a = normalize(c, raw(context, "A", 100, 1, 10));
        Notification spinning = Notification.Builder.recoverBuilder(context, a)
                .setRemoteInputHistory(new CharSequence[]{"Synthetic reply"}).build();
        spinning.extras.putBoolean(NotificationMessages.REMOTE_INPUT_SPINNER, true);
        spinning.extras.putBoolean(NotificationMessages.HIDE_SMART_REPLIES, true);
        c.round.end(10);
        Notification ready = NotificationMessages.completeReplyUi(context, spinning, "Synthetic reply");
        check(!ready.extras.getBoolean(NotificationMessages.REMOTE_INPUT_SPINNER), "reply spinner remained set");
        check(!ready.extras.getBoolean(NotificationMessages.HIDE_SMART_REPLIES), "reply actions stayed hidden");
        check(ready.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) == null
                && ready.extras.getParcelableArray(NotificationMessages.REMOTE_INPUT_HISTORY_ITEMS) == null,
                "reply UI completion kept legacy or structured input history");
        check((ready.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0, "completion could alert again");
        check(token(ready) == 10 && ready.extras.getBoolean(NotificationMessages.STORED)
                && "A".contentEquals(((Bundle) ready.extras.getParcelableArray(Notification.EXTRA_MESSAGES)[0]).getCharSequence("text")),
                "completion replaced the current card or its generation");
        check(spinning.extras.getBoolean(NotificationMessages.REMOTE_INPUT_SPINNER)
                && spinning.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) != null,
                "completion mutated the source card");
        check(c.round.endThroughToken == 10, "UI completion changed the history boundary");
        android.os.Parcelable[] displayed = ready.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        check(displayed.length == 2
                && "Synthetic reply".contentEquals(((Bundle) displayed[1]).getCharSequence("text"))
                && !((Bundle) displayed[1]).containsKey("sender"), "completion omitted the self reply");
        texts(c, ready);
        Conversation nextConversation = conversation(false);
        nextConversation.round.end(10);
        texts(nextConversation, normalize(nextConversation, raw(context, "C", 0, 2, 20), ready), "C");
        Notification next = raw(context, "C", 0, 2, 20);
        next.extras.putAll(spinning.extras);
        next.extras.remove(NotificationMessages.STORED);
        next.extras.putCharSequence(Notification.EXTRA_TEXT, "C");
        next.extras.putLong(NotificationMessages.SERVER_ID, 2);
        next.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 20);
        next.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
        texts(c, normalize(c, next), "C");
        check(!next.extras.getBoolean(NotificationMessages.REMOTE_INPUT_SPINNER)
                && next.extras.getParcelableArray(NotificationMessages.REMOTE_INPUT_HISTORY_ITEMS) == null,
                "new peer inherited structured pending-reply state");
        check((next.flags & Notification.FLAG_ONLY_ALERT_ONCE) == 0, "new peer inherited temporary reply silence");
    }

    static void capturedTargetPreservesNewer(Context context) {
        Conversation c = conversation(false);
        Notification a = normalize(c, raw(context, "A", 100, 1, 10));
        long capturedToken = token(a);
        Notification next = normalize(c, raw(context, "C", 0, 2, 20), a);
        NotificationMessages.clearReadHistory(c, next, capturedToken);
        texts(c, next, "C");
        Notification later = normalize(c, raw(context, "D", 90, 3, 30), a, next);
        texts(c, later, "C", "D");
    }

    static void replyCompletionPublicationGuard(Context context) {
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        try (ControllerFixture fixture = new ControllerFixture(context)) {
            Notification a = fixture.appliedIncoming("tag", "A");
            ReplyTarget target = fixture.controller.replyTarget(fixture.id);
            fixture.controller.onReplySubmitted(fixture.id, target);
            check(fixture.latest() == null && fixture.canCompleteReply(fixture.id, "tag", target, a),
                    "retiring A's archive incorrectly revoked its visible UI completion");
            // C passed the real Local apply/beforePost, but delivery is deliberately absent: active stays A.
            Notification c = fixture.appliedIncoming("tag", "C");
            check(!fixture.beforePost(manager, "tag", fixture.id, c, true), "C was not accepted for delivery");
            check(!fixture.canCompleteReply(fixture.id, "tag", target, a),
                    "old UI completion could overwrite a locally accepted, not-yet-delivered C");
            fixture.texts("C");
        }
        try (ControllerFixture fixture = new ControllerFixture(context)) {
            Notification a = fixture.appliedIncoming("tag", "A");
            ReplyTarget target = fixture.controller.replyTarget(fixture.id);
            fixture.controller.onReplySubmitted(fixture.id, target);
            fixture.onCancel(null, fixture.id);
            check(fixture.canCompleteReply(fixture.id, "tag", target, a), "wrong-tag cancel revoked A");
            fixture.onCancel("tag", fixture.id); // Cancel BEFORE observed; active deliberately stays A.
            check(!fixture.canCompleteReply(fixture.id, "tag", target, a),
                    "completion could resurrect a locally cancelled card before system removal");
            Notification c = fixture.appliedIncoming("tag", "C");
            ReplyTarget next = fixture.controller.replyTarget(fixture.id);
            check(fixture.canCompleteReply(fixture.id, "tag", next, c), "new raw C did not restore publication eligibility");
            fixture.onCancelAll();
            check(!fixture.canCompleteReply(fixture.id, "tag", next, c), "cancelAll left a completion eligible");
            check(next.conversation().round.retains(next.token()), "ordinary app cancelAll ended unread C");
            fixture.texts("C");
        }
    }

    static void repeatedSubmissionPreservesNextRound(Context context) {
        Conversation c = conversation(false);
        Notification a = normalize(c, raw(context, "A", 100, 1, 10));
        NotificationMessages.clearReadHistory(c, a, 10);
        Notification next = normalize(c, raw(context, "C", 0, 2, 20), a);
        NotificationMessages.clearReadHistory(c, next, 10);
        NotificationMessages.clearReadHistory(c, next, 5);
        check(c.round.endThroughToken == 10, "old target moved the end boundary backward or forward");
        texts(c, next, "C");
        Notification later = normalize(c, raw(context, "D", 0, 3, 30), a, next);
        texts(c, later, "C", "D");
    }

    static void stableIdsAcrossEndedRounds(Context context) {
        for (long timestamp : new long[]{0, 100, 400}) {
            Conversation c = conversation(false);
            Notification a = normalize(c, raw(context, "[语音]", 100, 7, 10));
            NotificationMessages.clearReadHistory(c, a, 10);
            Notification replay = normalize(c, raw(context, "[语音]", timestamp, 7, 20), a);
            texts(c, replay);
            Notification next = normalize(c, raw(context, "[语音]", timestamp, 8, 30), a, replay);
            texts(c, next, "[语音]");
            Notification another = normalize(c, raw(context, "[语音]", timestamp, 9, 40), next);
            texts(c, another, "[语音]", "[语音]");
        }
    }

    static void generationOrder(Context context) {
        for (long[] timestamps : new long[][]{{0, 0, 0}, {100, 100, 100}, {300, 200, 100}, {100, 0, 90}}) {
            Conversation c = conversation(false);
            Notification a = normalize(c, raw(context, "A", timestamps[0], 1, 10));
            Notification b = normalize(c, raw(context, "B", timestamps[1], 2, 20), a);
            Notification d = normalize(c, raw(context, "D", timestamps[2], 3, 30), a, b);
            List<Message> messages = messages(c, d);
            texts(c, d, "A", "B", "D");
            for (int i = 0; i < timestamps.length; i++)
                check(messages.get(i).getTimestamp() == timestamps[i], "generation order changed display time");
        }
    }

    static void nativeHistoryCannotCrossEnd(Context context) {
        Conversation c = conversation(false);
        Notification a = normalize(c, raw(context, "A", 500, 1, 10));
        NotificationMessages.clearReadHistory(c, a, 10);
        Notification next = raw(context, "C", 0, 2, 20);
        next.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        next.extras.putCharSequence("android.selfDisplayName", "Synthetic self");
        // Native history has no reliable generation stamp, even when its clock looks newer.
        next.extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Bundle[]{
                NotificationMessages.toBundle(new Message("A", 500, NotificationMessages.peer(c, null))),
                NotificationMessages.toBundle(new Message("untrusted old", 900, NotificationMessages.peer(c, null))),
                NotificationMessages.toBundle(new Message("C", 0, NotificationMessages.peer(c, null)))});
        texts(c, normalize(c, next, a), "C");
    }

    static void staleSnapshotCannotRevive(Context context) {
        Conversation c = conversation(false);
        Notification a = normalize(c, raw(context, "A", 100, 1, 10));
        Notification stale = a.clone();
        Notification preview = Notification.Builder.recoverBuilder(context, a)
                .setStyle(new Notification.BigPictureStyle().bigPicture(
                        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))).build();
        NotificationMessages.copyIdentityExtras(a, preview);
        preview.extras = parcel(preview.extras);
        NotificationMessages.clearReadHistory(c, a, 10);
        Notification next = normalize(c, raw(context, "C", 0, 2, 20), stale, preview);
        c.icon = IconCompat.createWithBitmap(Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888));
        texts(c, normalize(c, stale, next));
        texts(c, normalize(c, preview, next));
        Notification later = normalize(c, raw(context, "D", 0, 3, 30), stale, preview, next);
        texts(c, later, "C", "D");
        for (Message message : messages(c, later))
            check(message.getPerson() != null && message.getPerson().getIcon() != null,
                    "late avatar update lost current-round peer identity");
    }

    static void groupSenderIsolation(Context context) {
        Conversation c = conversation(true);
        Notification a = normalize(c, raw(context, "Bob: [语音]", 100, 7, 10));
        NotificationMessages.clearReadHistory(c, a, 10);
        Notification replay = normalize(c, raw(context, "Bob: [语音]", 100, 7, 20), a);
        texts(c, replay);
        Notification next = normalize(c, raw(context, "Carol: [语音]", 0, 7, 30), a, replay);
        Notification later = normalize(c, raw(context, "Dave: D", 0, 8, 40), next);
        c.icon = IconCompat.createWithBitmap(Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888));
        List<Message> messages = messages(c, later);
        check(messages.size() == 2 && "Carol".contentEquals(messages.get(0).getPerson().getName())
                && "Dave".contentEquals(messages.get(1).getPerson().getName()), "ended round merged group senders");
        for (Message message : messages)
            check(message.getPerson().getIcon() == null, "conversation avatar was written into a group member");
    }

    static void historyBound(Context context) {
        Conversation c = conversation(false);
        Notification previous = null;
        for (int i = 1; i <= 30; i++)
            previous = normalize(c, raw(context, "message-" + i, 0, i, i),
                    previous == null ? new Notification[0] : new Notification[]{previous});
        List<Message> messages = messages(c, previous);
        check(messages.size() == 25 && "message-6".contentEquals(messages.get(0).getText())
                && "message-30".contentEquals(messages.get(24).getText()), "history does not keep the newest 25 generations");
    }

    static void stableIdBound() {
        ConversationRound round = new ConversationRound();
        check(round.firstToken("old", 1) == 1, "first stable ID token was not retained");
        round.end(1);
        for (int i = 2; i <= 65; i++) round.firstToken("stable-" + i, i);
        check(round.firstToken("stable-2", 100) == 2, "stable ID window is smaller than 64");
        check(round.firstToken("old", 101) == 101, "stable ID window retained more than 64 entries");
        round.end(0);
        round.end(1);
        check(round.endThroughToken == 1 && !round.retains(1) && round.retains(2),
                "ending a round is not monotonic and idempotent");
    }

    static void snapshotSharesRound(Context context) {
        ConversationManager manager = new ConversationManager();
        Conversation cached = manager.getConversation(740);
        cached.title = "Alice";
        Conversation before = manager.snapshot(cached, raw(context, "A", 100, 1, 10));
        Conversation after = manager.snapshot(cached, raw(context, "C", 0, 2, 20));
        check(before.round == cached.round && after.round == cached.round, "snapshot copied the mutable round boundary");
        before.round.end(10);
        check(!after.round.retains(10) && after.round.retains(20), "old snapshot did not publish its captured boundary");
    }

    static void controllerRetiresArchive(Context context) {
        try (ControllerFixture fixture = new ControllerFixture(context)) {
            fixture.incoming("A", 100);
            fixture.incoming("B", 200);
            fixture.texts("A", "B");
            ReplyTarget target = fixture.controller.replyTarget(fixture.id);
            fixture.controller.onReplySubmitted(fixture.id, target);
            check(fixture.latest() == null, "submitted round retained its archived card");
            fixture.incoming("C", 0);
            fixture.texts("C");
            fixture.controller.onReplySubmitted(fixture.id, target);
            fixture.texts("C");
            fixture.incoming("D", 0);
            fixture.texts("C", "D");
        }
    }

    static void controllerCapturedTargetPreservesNewer(Context context) {
        try (ControllerFixture fixture = new ControllerFixture(context)) {
            fixture.incoming("A", 100);
            Notification stale = fixture.latest().clone();
            ReplyTarget target = fixture.controller.replyTarget(fixture.id);
            Runnable read = fixture.captureRead();
            check(read != null, "known conversation was not captured by the read observer");
            fixture.incoming("C", 0);
            read.run();
            fixture.controller.onReplySubmitted(fixture.id, target);
            fixture.texts("C");
            Notification current = fixture.latest();
            Notification preview = stale.clone();
            preview.extras.putBoolean("nevo.wechat.imageReady", true);
            check(ImagePreviewLoader.isPreview(preview), "stale preview fixture missed the fast path");
            for (Notification late : new Notification[]{stale, preview}) {
                check(fixture.apply(context.getSystemService(android.app.NotificationManager.class), null, fixture.id, late)
                        == com.oasisfeng.nevo.sdk.Decorating.StopPost, "ended rebuild was accepted by apply");
                check(fixture.beforePost(context.getSystemService(android.app.NotificationManager.class), null,
                        fixture.id, late, true), "ended rebuild was accepted by the final posting guard");
                check(fixture.latest() == current, "late rebuild replaced the newer archived card");
            }
            fixture.incoming("D", 90);
            fixture.texts("C", "D");
        }
        try (ControllerFixture fixture = new ControllerFixture(context)) {
            fixture.incoming("A", 100);
            ReplyTarget target = fixture.controller.replyTarget(fixture.id);
            java.util.function.Consumer<String> pending = fixture.incomingUnresolved("C", 0);
            fixture.controller.onReplySubmitted(fixture.id, target);
            fixture.controller.onReplySubmitted(fixture.id, target);
            pending.accept("synthetic-peer");
            check("synthetic-peer".equals(fixture.conversation.knownKey()),
                    "old/repeated round cleanup invalidated C's pending identity lookup");
            check(fixture.captureRead() != null, "resolved surviving C cannot be matched for a real read event");
            fixture.texts("C");
        }
    }

    static void controllerWithoutActiveNotification(Context context) {
        try (ControllerFixture fixture = new ControllerFixture(context)) {
            fixture.incoming("A", 100);
            ReplyTarget target = fixture.controller.replyTarget(fixture.id);
            fixture.removeArchive();
            fixture.controller.onReplySubmitted(fixture.id, target);
            check(fixture.latest() == null && !target.conversation().round.retains(target.token()),
                    "submission requires an active or archived card to finish the captured round");
            fixture.incoming("C", 0);
            fixture.texts("C");
        }
    }

    /** Uses the production Local controller while leaving final Android notification delivery absent. */
    private static final class ControllerFixture extends WeChatDecorator.Local implements AutoCloseable {
        final int id = 73109;
        final Context context;
        final MessagingBuilder.Controller controller;
        Conversation conversation;
        ControllerFixture(Context context) {
            super("round-test");
            this.context = context;
            clearArchivedNotifications(id);
            onCreate(context.getSharedPreferences("conversation-round-test", Context.MODE_PRIVATE));
            try {
                java.lang.reflect.Field field = WeChatDecorator.Local.class.getDeclaredField("mReplyController");
                field.setAccessible(true);
                controller = (MessagingBuilder.Controller) field.get(this);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        void incoming(String text, long time) {
            List<Notification> archive = new ArrayList<>(getArchivedNotifications(id));
            Notification notification = raw(context, text, time, 0, WeChatNotificationRemoval.nextToken());
            cache(id, notification);
            conversation = controller.replyTarget(id).conversation();
            conversation.title = "Alice";
            conversation.key = "synthetic-peer";
            normalize(conversation, notification, archive.toArray(new Notification[0]));
        }
        Notification appliedIncoming(String tag, String text) {
            Notification n = raw(context, text, 100, 0, 0);
            n.extras.putBoolean(WeChatRecallEvents.VERIFIED_KEY, true);
            n.extras.putString(NotificationMessages.KEY, "synthetic-peer");
            check(apply(context.getSystemService(android.app.NotificationManager.class), tag, id, n)
                    == com.oasisfeng.nevo.sdk.Decorating.Processed, "real Local incoming was not normalized");
            conversation = controller.replyTarget(id).conversation();
            return n;
        }
        void texts(String... expected) {
            check(latest() != null, "current round has no archived notification");
            ConversationRoundInstrumentation.texts(conversation, latest(), expected);
        }
        java.util.function.Consumer<String> incomingUnresolved(String text, long time) {
            try {
                java.lang.reflect.Field field = WeChatDecorator.Local.class.getDeclaredField("mConversationManager");
                field.setAccessible(true);
                ConversationManager manager = (ConversationManager) field.get(this);
                List<Notification> archive = new ArrayList<>(getArchivedNotifications(id));
                Notification notification = raw(context, text, time, 0, WeChatNotificationRemoval.nextToken());
                conversation.key = conversation.resolvedKey = null;
                Conversation view = manager.snapshot(conversation, notification);
                normalize(view, notification, archive.toArray(new Notification[0]));
                cache(id, notification);
                return view.onTalkerResolved;
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        Runnable captureRead() {
            try {
                java.lang.reflect.Method capture = WeChatDecorator.Local.class.getDeclaredMethod("captureRead", String.class);
                capture.setAccessible(true);
                return (Runnable) capture.invoke(this, "synthetic-peer");
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        Notification latest() { return getArchivedNotification(id); }
        void removeArchive() { clearArchivedNotifications(id); }
        @Override public void close() {
            try { clearArchivedNotifications(id); }
            finally { onDestroy(); }
        }
    }

    private static Conversation conversation(boolean group) {
        Conversation c = new Conversation(1);
        c.title = group ? "Synthetic group" : "Alice";
        c.key = group ? "synthetic-group@chatroom" : "synthetic-peer";
        c.setType(group ? Conversation.TYPE_GROUP_CHAT : Conversation.TYPE_DIRECT_MESSAGE);
        return c;
    }
    private static Notification raw(Context context, String text, long time, long id, long token) {
        Notification n = new Notification.Builder(context, "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(text.contains(": ") ? "Synthetic group" : "Alice")
                .setContentText(text).setTicker("Synthetic sender: " + text).setWhen(time).build();
        if (id > 0) n.extras.putLong(NotificationMessages.SERVER_ID, id);
        n.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, token);
        return n;
    }
    private static Notification normalize(Conversation c, Notification n, Notification... archive) {
        MessagingStyle style = new MessagingStyle(new Person.Builder().setName("Synthetic self").build());
        for (Message message : NotificationMessages.rebuild(c, n, Arrays.asList(archive))) style.addMessage(message);
        NotificationMessages.stamp(c, n);
        NotificationMessages.flatIntoExtras(style, n.extras);
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        return n;
    }
    private static List<Message> messages(Conversation c, Notification n) {
        return NotificationMessages.rebuild(c, n, Collections.emptyList());
    }
    private static void texts(Conversation c, Notification n, String... expected) {
        List<Message> messages = messages(c, n);
        check(messages.size() == expected.length, "unexpected history length " + messages.size());
        for (int i = 0; i < expected.length; i++)
            check(expected[i].contentEquals(messages.get(i).getText()), "wrong round message at " + i);
    }
    private static long token(Notification n) {
        return n.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN);
    }
    private static Bundle parcel(Bundle bundle) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(bundle);
            parcel.setDataPosition(0);
            return parcel.readBundle(ConversationRoundInstrumentation.class.getClassLoader());
        } finally { parcel.recycle(); }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
