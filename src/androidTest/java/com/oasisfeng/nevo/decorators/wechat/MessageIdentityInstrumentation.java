package com.oasisfeng.nevo.decorators.wechat;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Framework-only instrumentation runner: no test-library download or host WeChat is required. */
public final class MessageIdentityInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        com.oasisfeng.nevo.sdk.NevoDecoratorService.setAppContext(getTargetContext());
        String[] names = { "personAndAttachmentRoundTrip", "staleTickerAndCarHistory", "unknownAndGroupNames",
                "replyThenIncoming", "repeatedRebuildAndPreview", "untrustedMissingSender", "reusedNotificationId",
                "rawPersonOnlyNotification", "nativeMessagingSelf", "rawColonText", "undatedSnapshots",
                "stablePeerAcrossTalkerAndAvatar", "twoRoundsAndReplay", "groupRound", "roundPreviewAndUndated",
                "removedRoundStartsFresh",
                "classifiesRawFirstMessages", "classificationSurvivesNextMessage", "lateTalkerIsIsolated",
                "legacyGroupGuessIsRepaired", "nativeGroupEvidence", "replyActionSurvivesArchiveRemoval" };
        Runnable[] tests = { this::personAndAttachmentRoundTrip, this::staleTickerAndCarHistory, this::unknownAndGroupNames,
                this::replyThenIncoming, this::repeatedRebuildAndPreview, this::untrustedMissingSender, this::reusedNotificationId,
                this::rawPersonOnlyNotification, this::nativeMessagingSelf, this::rawColonText, this::undatedSnapshots,
                this::stablePeerAcrossTalkerAndAvatar, this::twoRoundsAndReplay, this::groupRound, this::roundPreviewAndUndated,
                this::removedRoundStartsFresh,
                this::classifiesRawFirstMessages, this::classificationSurvivesNextMessage, this::lateTalkerIsIsolated,
                this::legacyGroupGuessIsRepaired, this::nativeGroupEvidence,
                () -> com.oasisfeng.nevo.sdk.NotificationArchiveInstrumentation.replyActionSurvivesRemovalAndRebuild(getTargetContext()) };
        int failures = 0;
        for (int i = 0; i < tests.length; i++) {
            Bundle status = new Bundle();
            status.putString("class", getClass().getName());
            status.putString("test", names[i]);
            status.putInt("numtests", tests.length);
            status.putInt("current", i + 1);
            sendStatus(1, status);
            try { tests[i].run(); sendStatus(0, status); }
            catch (Throwable error) {
                failures++;
                status.putString("stack", android.util.Log.getStackTraceString(error));
                sendStatus(-2, status);
            }
        }
        Bundle result = new Bundle();
        result.putString("stream", "Identity instrumentation: " + (tests.length - failures) + "/" + tests.length + " passed\n");
        if (failures != 0) result.putString("shortMsg", failures + " identity tests failed");
        finish(failures == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void personAndAttachmentRoundTrip() {
        Person peer = new Person.Builder().setName("Alice").setKey("alice-key").setUri("peer:alice")
                .setBot(true).setImportant(true)
                .setIcon(IconCompat.createWithBitmap(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))).build();
        Message original = new Message("photo", 123, peer).setData("image/jpeg", android.net.Uri.parse("content://test/photo"));
        original.getExtras().putString("custom", "kept");
        Bundle bundle = parcel(MessagingBuilder.toBundle(original));
        bundle.remove("sender");
        Message restored = NotificationMessages.read(bundle, conversation(false), false, false);
        check(restored != null && restored.getPerson() != null, "person-only message became self");
        check("alice-key".equals(restored.getPerson().getKey()), "key lost");
        check("peer:alice".equals(restored.getPerson().getUri()), "URI lost");
        check(restored.getPerson().isBot() && restored.getPerson().isImportant(), "person flags lost");
        check(restored.getPerson().getIcon() != null, "avatar lost");
        check(original.getDataUri().equals(restored.getDataUri()), "attachment lost");
        check("image/jpeg".equals(restored.getDataMimeType()), "MIME lost");
        check("kept".equals(restored.getExtras().getString("custom")), "extras lost");
    }

    private void staleTickerAndCarHistory() {
        Notification n = notification("明天见", 200);
        n.tickerText = "Alice: 你好";
        Bundle car = new Bundle();
        Bundle conversation = new Bundle();
        Bundle old = new Bundle(); old.putString("text", "你好");
        Bundle latest = new Bundle(); latest.putString("text", "明天见");
        conversation.putParcelableArray("messages", new Bundle[]{old, latest});
        conversation.putStringArray("participants", new String[]{"Alice"});
        conversation.putLong("timestamp", 200);
        car.putBundle("car_conversation", conversation);
        n.extras.putBundle("android.car.EXTENSIONS", car);
        check(new Notification.CarExtender(n).getUnreadConversation().getMessages().length == 2, "invalid car fixture");
        List<Message> messages = NotificationMessages.rebuild(conversation(false), n, Collections.emptyList());
        check(messages.size() == 1 && "明天见".contentEquals(messages.get(0).getText()), "stale ticker replaced current text");
        check(messages.get(0).getPerson() != null, "peer became self");
    }

    private void unknownAndGroupNames() {
        Conversation group = conversation(true);
        Message named = NotificationMessages.incoming(group, notification("群名: hello", 100));
        check(named.getPerson() != null && "群名".contentEquals(named.getPerson().getName()), "group name became self");
        Message unknown = NotificationMessages.incoming(group, notification("hello", 100));
        check(unknown.getPerson() != null && unknown.getPerson().getIcon() == null, "unknown uses self avatar");
    }

    private void replyThenIncoming() {
        Conversation c = conversation(false);
        Notification old = normalized(c, notification("first", 100));
        NotificationMessages.recordReply(old, "my reply", 150, "reply-1");
        Notification latest = notification("new incoming", 200);
        List<Message> messages = NotificationMessages.rebuild(c, latest, Arrays.asList(old, latest));
        check(messages.size() == 2, "previous round was retained");
        check(messages.get(0).getPerson() == null && messages.get(0).getTimestamp() == 150, "reply identity/time changed");
        check(messages.get(1).getPerson() != null, "new incoming became self");
    }

    private void repeatedRebuildAndPreview() {
        Conversation c = conversation(false);
        Notification n = normalized(c, notification("first", 100));
        NotificationMessages.recordReply(n, "reply", 150, "reply-roundtrip");
        n = normalized(c, n);
        Notification recovered = Notification.Builder.recoverBuilder(getTargetContext(), n).build();
        List<Message> first = NotificationMessages.rebuild(c, recovered, Collections.singletonList(n));
        check(first.size() == 2 && first.get(1).getPerson() == null, "recoverBuilder changed reply identity");
        Notification preview = Notification.Builder.recoverBuilder(getTargetContext(), recovered)
                .setStyle(new Notification.BigPictureStyle().bigPicture(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))).build();
        NotificationMessages.copyIdentityExtras(recovered, preview);
        preview.extras = parcel(preview.extras);
        List<Message> second = NotificationMessages.rebuild(c, preview, Arrays.asList(n, recovered));
        check(second.size() == 2, "preview duplicated history");
        check("reply-roundtrip".equals(second.get(1).getExtras().getString(NotificationMessages.REPLY_ID)), "preview lost reply marker");
        check(second.get(1).getPerson() == null && second.get(1).getTimestamp() == 150, "preview changed reply");
    }

    private void untrustedMissingSender() {
        Bundle b = new Bundle(); b.putCharSequence("text", "unknown");
        check(NotificationMessages.read(b, conversation(false), false, true) == null, "untrusted historical sender retained");
        check(NotificationMessages.read(b, conversation(false), false, false).getPerson() != null, "missing sender became self");
        b.putCharSequence("sender", "");
        check(NotificationMessages.read(b, conversation(false), false, false).getPerson() != null, "empty sender became self");
    }

    private void reusedNotificationId() {
        Conversation old = conversation(false);
        old.key = "old-key";
        Notification archived = normalized(old, notification("old text", 100));
        Conversation current = conversation(false); current.key = "new-key";
        List<Message> messages = NotificationMessages.rebuild(current, notification("new text", 200), Collections.singletonList(archived));
        check(messages.size() == 1, "same title mixed different talkers");
        ConversationManager manager = new ConversationManager();
        manager.getConversation(1).setType(Conversation.TYPE_GROUP_CHAT);
        manager.resetConversation(1);
        check(manager.getConversation(1).getType() == Conversation.TYPE_UNKNOWN, "reused ID kept group state");
    }

    private void rawPersonOnlyNotification() {
        Notification n = notification("hello", 100);
        Message m = new Message("hello", 100, new Person.Builder().setName("Alice").setKey("key").build());
        Bundle b = MessagingBuilder.toBundle(m); b.remove("sender");
        n.extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Parcelable[]{b});
        Conversation c = conversation(false);
        List<Message> messages = NotificationMessages.rebuild(c, n, Collections.emptyList());
        check(messages.size() == 1 && c.notificationPeerId.equals(messages.get(0).getPerson().getKey()), "raw person-only message duplicated or lost canonical identity");
    }

    private void nativeMessagingSelf() {
        Notification n = notification("reply", 100);
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        n.extras.putCharSequence("android.selfDisplayName", "我");
        if (Build.VERSION.SDK_INT >= 28) n.extras.putParcelable(Notification.EXTRA_MESSAGING_PERSON, new android.app.Person.Builder().setName("我").build());
        Bundle b = new Bundle(); b.putCharSequence("text", "reply"); b.putLong("time", 100);
        n.extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Parcelable[]{b});
        List<Message> messages = NotificationMessages.rebuild(conversation(false), n, Collections.emptyList());
        check(messages.size() == 1 && messages.get(0).getPerson() == null, "valid MessagingStyle self contract lost");
    }

    private void rawColonText() {
        Message message = NotificationMessages.incoming(conversation(false), notification("https: example", 100));
        check("https: example".contentEquals(message.getText()), "body prefix removed");
    }

    private void undatedSnapshots() {
        Conversation c = conversation(false);
        Notification old = normalized(c, notification("undated", 0));
        Notification copy = Notification.Builder.recoverBuilder(getTargetContext(), old).build();
        check(NotificationMessages.rebuild(c, copy, Collections.singletonList(old)).size() == 1, "undated snapshot multiplied");
        List<Message> messages = NotificationMessages.rebuild(c, notification("current", 200), Arrays.asList(old, copy));
        check(messages.size() == 2 && "current".contentEquals(messages.get(1).getText()), "undated history covered current incoming");
    }

    private Notification notification(String text, long time) {
        return new Notification.Builder(getTargetContext(), "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Alice").setContentText(text).setWhen(time).build();
    }
    private Notification normalized(Conversation c, Notification n) {
        return normalized(c, n, Collections.emptyList());
    }
    private Notification normalized(Conversation c, Notification n, List<Notification> archive) {
        MessagingStyle style = new MessagingStyle(new Person.Builder().setName("我").build());
        for (Message m : NotificationMessages.rebuild(c, n, archive)) style.addMessage(m);
        NotificationMessages.stamp(c, n);
        MessagingBuilder.flatIntoExtras(style, n.extras);
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        return n;
    }

    private void stablePeerAcrossTalkerAndAvatar() {
        Conversation c = conversation(false);
        Notification old = normalized(c, notification("A", 100));
        String stable = c.notificationPeerId;
        c.key = "resolved-talker";
        c.icon = IconCompat.createWithBitmap(Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888));
        Notification latest = normalized(c, notification("C", 200), Collections.singletonList(old));
        List<Message> messages = NotificationMessages.rebuild(c, latest, Collections.emptyList());
        check(messages.size() == 2, "peer history missing");
        for (Message m : messages) {
            check(stable.equals(m.getPerson().getKey()), "talker changed sender identity");
            check(m.getPerson().getIcon() != null, "updated avatar not shared by old messages");
        }
        Conversation restored = conversation(false);
        NotificationMessages.rebuild(restored, latest, Collections.emptyList());
        check(stable.equals(restored.notificationPeerId), "snapshot lost stable sender ID");
        Conversation fresh = conversation(false);
        check(!stable.equals(NotificationMessages.peer(fresh, null).getKey()), "replacement conversation reused identity");
    }

    private void twoRoundsAndReplay() {
        Conversation c = conversation(false);
        Notification a = normalized(c, notification("A", 100));
        NotificationMessages.recordReply(a, "B1", 110, "B1");
        NotificationMessages.recordReply(a, "B2", 120, "B2");
        NotificationMessages.recordReply(a, "B2", 120, "B2");
        checkTexts(c, normalized(c, a), "A", "B1", "B2");
        Notification next = normalized(c, notification("C", 200), Collections.singletonList(a));
        checkTexts(c, next, "B1", "B2", "C");
        next = normalized(c, notification("D", 300), Collections.singletonList(next));
        checkTexts(c, next, "B1", "B2", "C", "D");
        NotificationMessages.recordReply(next, "E", 310, "E");
        next = normalized(c, notification("D", 300), Collections.singletonList(next));
        checkTexts(c, next, "B1", "B2", "C", "D", "E");
        next = normalized(c, notification("F", 400), Collections.singletonList(next));
        checkTexts(c, next, "E", "F");
        next = normalized(c, notification("A", 100), Collections.singletonList(next));
        checkTexts(c, next, "E", "F");
        CharSequence[] history = next.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY);
        check(history.length == 1 && "E".contentEquals(history[0]), "previous reply history resurrected");
    }

    private void groupRound() {
        Conversation c = conversation(true);
        Notification a = normalized(c, notification("Alice: A", 100));
        NotificationMessages.recordReply(a, "B", 150, "group-B");
        Notification next = normalized(c, notification("Bob: C", 200), Collections.singletonList(a));
        next = normalized(c, notification("Carol: D", 300), Collections.singletonList(next));
        List<Message> messages = NotificationMessages.rebuild(c, next, Collections.emptyList());
        check(messages.size() == 3 && messages.get(0).getPerson() == null, "group round did not retain reply");
        check("Bob".contentEquals(messages.get(1).getPerson().getName()), "first member lost");
        check("Carol".contentEquals(messages.get(2).getPerson().getName()), "group members merged");
    }

    private void roundPreviewAndUndated() {
        Conversation c = conversation(false);
        Notification a = normalized(c, notification("A", 100));
        NotificationMessages.recordReply(a, "B", 150, "B");
        Notification next = normalized(c, notification("C", 200), Collections.singletonList(a));
        long removalToken = WeChatNotificationRemoval.nextToken();
        next.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, removalToken);
        Notification preview = Notification.Builder.recoverBuilder(getTargetContext(), next)
                .setStyle(new Notification.BigPictureStyle().bigPicture(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))).build();
        NotificationMessages.copyIdentityExtras(next, preview);
        preview.extras = parcel(preview.extras);
        check(preview.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN) == removalToken,
                "preview lost removal generation");
        checkTexts(c, preview, "B", "C");
        NotificationMessages.recordReply(preview, "E", 250, "E");
        Notification undated = normalized(c, notification("unknown-time", 0), Collections.singletonList(preview));
        checkTexts(c, undated, "unknown-time", "B", "C", "E");
        Notification finalRound = normalized(c, notification("F", 300), Collections.singletonList(undated));
        checkTexts(c, finalRound, "E", "F");
    }

    private void removedRoundStartsFresh() {
        Conversation c = conversation(false);
        Notification first = normalized(c, notification("A", 100));
        NotificationMessages.recordReply(first, "B", 150, "removed-B");
        Notification current = normalized(c, notification("C", 200), Collections.singletonList(first));
        checkTexts(c, current, "B", "C");
        // The removal receiver clears the archive, so the next build receives no prior snapshot.
        Notification fresh = normalized(c, notification("D", 300), Collections.emptyList());
        checkTexts(c, fresh, "D");
    }

    private void checkTexts(Conversation c, Notification n, String... expected) {
        List<Message> messages = NotificationMessages.rebuild(c, n, Collections.emptyList());
        check(messages.size() == expected.length, "unexpected round length: " + messages.size());
        for (int i = 0; i < expected.length; i++) check(expected[i].contentEquals(messages.get(i).getText()), "wrong round content at " + i);
    }
    private static Conversation conversation(boolean group) {
        Conversation c = new Conversation(1); c.title = group ? "群名" : "Alice";
        c.setType(group ? Conversation.TYPE_GROUP_CHAT : Conversation.TYPE_DIRECT_MESSAGE);
        return c;
    }

    private Conversation classified(ConversationManager manager, Notification n) {
        Conversation cached = manager.getConversation(1);
        cached.title = "Alice";
        cached.summary = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        cached.ticker = n.tickerText;
        cached.timestamp = n.when;
        return manager.snapshot(cached, n);
    }

    private void classifiesRawFirstMessages() {
        for (String body : new String[]{"你好", "a", "[2条]你好", "提示: hello"}) {
            for (boolean withTicker : new boolean[]{true, false}) {
                Notification n = notification(body, 100);
                n.tickerText = withTicker ? body : null;
                Conversation c = classified(new ConversationManager(), n);
                check(!c.isGroupChat(), "body/ticker promoted cold-start notification to group");
                List<Message> messages = NotificationMessages.rebuild(c, n, Collections.emptyList());
                check(messages.size() == 1 && "Alice".contentEquals(messages.get(0).getPerson().getName()), "cold-start peer became unknown");
            }
        }
    }

    private void classificationSurvivesNextMessage() {
        ConversationManager manager = new ConversationManager();
        Notification first = notification("hello", 100); first.tickerText = "Alice: hello";
        Conversation initial = classified(manager, first);
        first = normalized(initial, first);
        Notification second = notification("你好", 200); second.tickerText = "你好";
        Conversation next = classified(manager, second);
        List<Message> messages = NotificationMessages.rebuild(next, second, Collections.singletonList(first));
        check(messages.size() == 2 && !next.isGroupChat(), "second message changed to group");
        for (Message message : messages) {
            check("Alice".contentEquals(message.getPerson().getName()), "peer became unknown");
            check(initial.notificationPeerId.equals(message.getPerson().getKey()), "stable peer ID lost");
        }
        manager.acceptTalker(manager.getConversation(1), "alice");
        Notification contradictory = notification("Bob: hello", 300);
        contradictory.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        contradictory.extras.putBoolean("android.isGroupConversation", true);
        check(classified(manager, contradictory).getType() == Conversation.TYPE_DIRECT_MESSAGE, "weaker group evidence replaced verified private talker");
    }

    private void lateTalkerIsIsolated() {
        ConversationManager manager = new ConversationManager();
        Notification n = notification("hello", 100);
        Conversation view = classified(manager, n);
        view.onTalkerResolved.accept("room@chatroom");
        check(view.getType() == Conversation.TYPE_UNKNOWN && view.key == null, "callback changed in-flight view");
        check(!n.extras.containsKey(NotificationMessages.KEY), "callback mutated notification extras");
        check(classified(manager, notification("next", 200)).isGroupChat(), "next build did not consume resolved key");
        manager.resetConversation(1);
        view.onTalkerResolved.accept("room@chatroom");
        check(classified(manager, notification("new contact", 300)).getType() == Conversation.TYPE_UNKNOWN, "stale callback contaminated reused ID");
        Conversation current = manager.getConversation(1);
        check(manager.acceptTalker(current, "alice"), "valid callback rejected");
        check(!manager.acceptTalker(current, "bob"), "conflicting callback overwrote identity");
        ConversationManager pending = new ConversationManager();
        Conversation stale = classified(pending, notification("old request", 100));
        Conversation fresh = classified(pending, notification("new request", 200));
        stale.onTalkerResolved.accept("old@chatroom");
        check(pending.getConversation(1).knownKey() == null, "older generation callback accepted");
        fresh.onTalkerResolved.accept("alice");
        check(classified(pending, notification("new", 300)).getType() == Conversation.TYPE_DIRECT_MESSAGE, "latest callback rejected");
    }

    private void legacyGroupGuessIsRepaired() {
        Notification old = notification("hello", 100);
        old.extras.putBoolean(NotificationMessages.STORED, true);
        old.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        old.extras.putBoolean("android.isGroupConversation", true);
        old.extras.putCharSequence("android.conversationTitle", "Alice");
        Message placeholder = new Message("hello", 100, new Person.Builder().setName("未知发送者").build());
        old.extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Bundle[]{MessagingBuilder.toBundle(placeholder)});
        ConversationManager manager = new ConversationManager();
        Conversation c = classified(manager, old);
        check(!c.isGroupChat(), "module-generated legacy group flag treated as native proof");
        MessagingStyle style = new MessagingStyle(new Person.Builder().setName("我").build());
        for (Message message : NotificationMessages.rebuild(c, old, Collections.emptyList())) style.addMessage(message);
        style.setGroupConversation(c.isGroupChat()).setConversationTitle(null);
        MessagingBuilder.flatIntoExtras(style, old.extras);
        check("Alice".contentEquals(style.getMessages().get(0).getPerson().getName()), "old unknown placeholder not repaired");
        check(!old.extras.getBoolean("android.isGroupConversation") && !old.extras.containsKey("android.conversationTitle"), "stale group layout survived");
    }

    private void nativeGroupEvidence() {
        Notification n = notification("hello", 100);
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        n.extras.putBoolean("android.isGroupConversation", true);
        ConversationManager manager = new ConversationManager();
        Conversation c = classified(manager, n);
        check(c.isGroupChat(), "explicit native group ignored");
        check("未知发送者".contentEquals(NotificationMessages.incoming(c, n).getPerson().getName()), "real unknown group member incorrectly renamed");
        NotificationMessages.stamp(c, n);
        n.extras.putBoolean(NotificationMessages.STORED, true);
        Conversation restored = classified(new ConversationManager(), n);
        check(restored.isGroupChat(), "verified evidence lost on snapshot recovery");
        Notification noFlag = notification("hello", 200);
        noFlag.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        check(classified(manager, noFlag).isGroupChat(), "missing flag treated as explicit direct");
    }
    private static Bundle parcel(Bundle bundle) {
        Parcel p = Parcel.obtain();
        try { p.writeBundle(bundle); p.setDataPosition(0); return p.readBundle(MessageIdentityInstrumentation.class.getClassLoader()); }
        finally { p.recycle(); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
