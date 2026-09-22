package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.xposed.BuildConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Common Android adapter for all notification/reply paths. */
final class NotificationMessages {
    static final String REPLY_ID = "nevo.replyId";
    static final String STORED = "nevo.messagesNormalized";
    static final String TITLE = "nevo.conversationTitle";
    static final String KEY = "nevo.conversationKey";
    static final String PEER_ID = "nevo.notificationPeerId";
    static final String ROUND = "nevo.conversationRound";
    private static final String MESSAGE_ROUND = "nevo.messageRound";

    private static String peerId(Conversation conversation) {
        if (conversation.notificationPeerId == null) conversation.notificationPeerId = "nevo:peer:" + UUID.randomUUID();
        return conversation.notificationPeerId;
    }

    static Person peer(Conversation conversation, CharSequence name) {
        if (conversation.isGroupChat()) {
            return TextUtils.isEmpty(name)
                    ? new Person.Builder().setName("未知发送者").build()
                    : conversation.getGroupParticipant(name.toString(), name.toString());
        }
        return new Person.Builder().setName(TextUtils.isEmpty(conversation.title) ? " " : conversation.title)
                .setKey(peerId(conversation)).setIcon(conversation.icon).build();
    }

    static Message incoming(Conversation conversation, Notification notification) {
        CharSequence raw = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(raw)) return null;
        String text = raw.toString().replaceFirst("^\\[\\d+(?:条|則)?\\]", "");
        int colon = text.indexOf(WeChatMessage.SENDER_MESSAGE_SEPARATOR);
        String sender = null;
        if (colon > 0) {
            String prefix = text.substring(0, colon);
            if (conversation.isGroupChat() || TextUtils.equals(prefix, conversation.title)) {
                sender = prefix;
                text = text.substring(colon + WeChatMessage.SENDER_MESSAGE_SEPARATOR.length());
            }
        }
        // Ticker and car history can lag behind EXTRA_TEXT. Neither determines direction.
        return new Message(EmojiTranslator.translate(text), notification.when, peer(conversation, sender));
    }

    static Message read(Bundle bundle, Conversation conversation, boolean messagingContext, boolean historical) {
        CharSequence text = bundle.getCharSequence("text");
        if (text == null) return null;
        Person person = null;
        if (Build.VERSION.SDK_INT >= 28) {
            Parcelable value = bundle.getParcelable("sender_person");
            if (value instanceof android.app.Person) {
                android.app.Person nativePerson = (android.app.Person) value;
                Context context = NevoDecoratorService.getAppContext();
                IconCompat icon = nativePerson.getIcon() == null || context == null ? null
                        : IconCompat.createFromIcon(context, nativePerson.getIcon());
                person = new Person.Builder().setName(nativePerson.getName())
                        .setKey(nativePerson.getKey()).setUri(nativePerson.getUri())
                        .setBot(nativePerson.isBot()).setImportant(nativePerson.isImportant())
                        .setIcon(icon).build();
            }
        }
        if (person == null && bundle.getBundle("person") != null) person = Person.fromBundle(bundle.getBundle("person"));
        CharSequence sender = bundle.getCharSequence("sender");
        Bundle extras = bundle.getBundle("extras");
        boolean moduleReply = extras != null && !TextUtils.isEmpty(extras.getString(REPLY_ID));
        MessageIdentityPolicy.Identity identity = MessageIdentityPolicy.resolve(person != null,
                sender == null ? null : sender.toString(), moduleReply, messagingContext);
        if (BuildConfig.DEBUG) Log.d("WeChat.Identity", "source=bundle person=" + (person != null)
                + " sender=" + (sender != null) + " moduleReply=" + moduleReply
                + " context=" + messagingContext + " result=" + identity);
        if (identity == MessageIdentityPolicy.Identity.UNKNOWN && historical) return null;
        if (identity == MessageIdentityPolicy.Identity.SELF) person = null;
        else if (person == null) {
            person = TextUtils.isEmpty(sender) || (!conversation.isGroupChat() && TextUtils.equals(sender, conversation.title))
                    ? peer(conversation, null) : new Person.Builder().setName(sender).build();
        }
        Message message = new Message(text, bundle.getLong("time", 0), person);
        if (extras != null) message.getExtras().putAll(extras);
        String mime = bundle.getString("type");
        Parcelable uri = bundle.getParcelable("uri");
        if (mime != null && uri instanceof android.net.Uri) message.setData(mime, (android.net.Uri) uri);
        return message;
    }

    static List<Message> rebuild(Conversation conversation, Notification current, List<Notification> archive) {
        List<MessageIdentityPolicy.Entry<Message>> entries = new ArrayList<>();
        Notification snapshot = current;
        // A normalized notification is already a complete bounded snapshot. Combining every
        // snapshot again would multiply undated lines, which cannot safely be deduplicated.
        if (!current.extras.getBoolean(STORED)) for (int i = archive.size() - 1; i >= 0; i--) {
            Notification older = archive.get(i);
            if (older == current || !belongsTo(conversation, older) || !older.extras.getBoolean(STORED)) continue;
            snapshot = older;
            break;
        }
        if (conversation.notificationPeerId == null) conversation.notificationPeerId = snapshot.extras.getString(PEER_ID);
        peerId(conversation);
        ConversationRound round = readRound(snapshot.extras);
        if (snapshot != current) add(entries, conversation, snapshot, true, 0);
        add(entries, conversation, current, false, 1);
        // Canonicalize before signatures/merge: a later talker lookup must not split one peer.
        if (!conversation.isGroupChat() && conversation.getType() != Conversation.TYPE_BOT_MESSAGE) {
            if (conversation.icon == null) for (int i = entries.size() - 1; i >= 0; i--) {
                Person person = entries.get(i).value.getPerson();
                if (person != null && person.getIcon() != null && isKnownPeer(conversation, current, entries.get(i))) {
                    conversation.icon = person.getIcon(); break;
                }
            }
            Person canonical = peer(conversation, null);
            List<MessageIdentityPolicy.Entry<Message>> normalized = new ArrayList<>();
            for (MessageIdentityPolicy.Entry<Message> entry : entries) {
                Message message = entry.value;
                if (isKnownPeer(conversation, current, entry)) {
                    Message replacement = new Message(message.getText(), message.getTimestamp(), canonical);
                    replacement.getExtras().putAll(message.getExtras());
                    if (message.getDataMimeType() != null) replacement.setData(message.getDataMimeType(), message.getDataUri());
                    message = replacement;
                }
                addEntry(normalized, message, entry.source);
            }
            entries = normalized;
        }
        // Older snapshots may predate round metadata; seed their peer watermark once.
        if (!snapshot.extras.containsKey(ROUND)) for (MessageIdentityPolicy.Entry<Message> entry : entries)
            if ((snapshot == current || entry.source == 0) && entry.value.getPerson() != null)
                round.latestPeerTime = Math.max(round.latestPeerTime, entry.time);
        boolean advanced = false;
        if (!current.extras.getBoolean(STORED)) {
            long newest = 0;
            for (MessageIdentityPolicy.Entry<Message> entry : entries)
                if (entry.source > 0 && entry.value.getPerson() != null) newest = Math.max(newest, entry.time);
            advanced = round.incoming(newest);
            if (BuildConfig.DEBUG) Log.d("WeChat.Identity", "source=round advanced=" + advanced
                    + " incomingTime=" + newest + " watermark=" + round.latestPeerTime);
        }
        if (!advanced && !current.extras.getBoolean(STORED)) for (MessageIdentityPolicy.Entry<Message> entry : entries)
            if (entry.source > 0 && entry.time <= 0 && entry.value.getPerson() != null)
                entry.value.getExtras().putLong(MESSAGE_ROUND, round.cutoff);
        final ConversationRound retainedRound = round;
        entries.removeIf(entry -> !retainedRound.retain(entry.value.getPerson() == null, entry.replyId, entry.time,
                entry.value.getExtras().getLong(MESSAGE_ROUND, -1)));
        List<Message> result = MessageIdentityPolicy.merge(entries, 25);
        for (Message message : result) message.getExtras().putLong(MESSAGE_ROUND, round.cutoff);
        writeRound(current.extras, round);
        current.extras.putString(PEER_ID, peerId(conversation));
        if (round.cutoff > 0) updateInputHistory(current.extras, result);
        if (BuildConfig.DEBUG) Log.d("WeChat.Identity", "source=merge inputs=" + entries.size()
                + " output=" + result.size() + " currentTime=" + current.when);
        return result;
    }

    private static boolean isKnownPeer(Conversation conversation, Notification current, MessageIdentityPolicy.Entry<Message> entry) {
        Person person = entry.value.getPerson();
        if (person == null) return false;
        return entry.source == 0 || current.extras.getBoolean(STORED)
                || TextUtils.equals(person.getName(), conversation.title)
                || (person.getKey() != null && (person.getKey().equals(conversation.notificationPeerId)
                || person.getKey().equals(conversation.key)));
    }

    private static void add(List<MessageIdentityPolicy.Entry<Message>> entries, Conversation conversation,
                            Notification n, boolean historical, int source) {
        Bundle extras = n.extras;
        boolean normalized = extras.getBoolean(STORED);
        boolean context = normalized || ("android.app.Notification$MessagingStyle".equals(
                extras.getString(Notification.EXTRA_TEMPLATE)) && hasUser(extras));
        Parcelable[] bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        int before = entries.size();
        if (bundles != null) for (Parcelable parcel : bundles) {
            if (!(parcel instanceof Bundle)) continue;
            Message message = read((Bundle) parcel, conversation, context, historical || !context);
            if (message != null) addEntry(entries, message, source);
        }
        // For a raw notification, EXTRA_TEXT is the current incoming message. Unstructured
        // bundles can be stale history; use only explicitly identified history plus this line.
        if (!historical && ((!context) || entries.size() == before)) {
            Message latest = incoming(conversation, n);
            boolean represented = false;
            if (latest != null && latest.getTimestamp() > 0) for (int i = before; i < entries.size(); i++) {
                Message candidate = entries.get(i).value;
                if (candidate.getPerson() != null && candidate.getTimestamp() == latest.getTimestamp()
                        && TextUtils.equals(candidate.getText(), latest.getText())
                        && (!conversation.isGroupChat() || TextUtils.equals(candidate.getPerson().getName(), latest.getPerson().getName())))
                    represented = true;
            }
            if (latest != null && !represented) addEntry(entries, latest, source + 1);
        }
    }

    private static boolean hasUser(Bundle extras) {
        if (Build.VERSION.SDK_INT >= 28 && extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON) instanceof android.app.Person)
            return !TextUtils.isEmpty(((android.app.Person) extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON)).getName());
        return !TextUtils.isEmpty(extras.getCharSequence(NotificationCompat.EXTRA_SELF_DISPLAY_NAME));
    }

    private static void addEntry(List<MessageIdentityPolicy.Entry<Message>> entries, Message message, int source) {
        Person person = message.getPerson();
        Object identity = person == null ? "self" : Arrays.asList(string(person.getKey()), string(person.getUri()), string(person.getName()));
        Object signature = Arrays.asList(identity, string(message.getText()), message.getDataMimeType(), message.getDataUri());
        entries.add(new MessageIdentityPolicy.Entry<>(message, message.getTimestamp(), signature,
                person == null ? message.getExtras().getString(REPLY_ID) : null, source));
    }

    static boolean belongsTo(Conversation conversation, Notification notification) {
        Bundle extras = notification.extras;
        String title = extras.getString(TITLE);
        if (title == null) title = string(extras.getCharSequence(Notification.EXTRA_TITLE));
        return MessageIdentityPolicy.sameConversation(string(conversation.title), conversation.key, title, extras.getString(KEY));
    }

    static void stamp(Conversation conversation, Notification notification) {
		ConversationClassification.stamp(conversation, notification.extras);
        notification.extras.putString(TITLE, string(conversation.title));
        notification.extras.putString(KEY, conversation.key);
        if (conversation.notificationPeerId != null) notification.extras.putString(PEER_ID, conversation.notificationPeerId);
    }

    /** BigPictureStyle may rebuild presentation extras; keep the original message identities. */
    static void copyIdentityExtras(Notification source, Notification target) {
        Bundle identity = new Bundle(source.extras);
        java.util.Set<String> keep = new java.util.HashSet<>(Arrays.asList(STORED, TITLE, KEY, PEER_ID, ROUND,
                WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN,
                ConversationClassification.TYPE, ConversationClassification.SOURCE,
                Notification.EXTRA_REMOTE_INPUT_HISTORY,
                Notification.EXTRA_MESSAGES, "android.messagingUser", "android.selfDisplayName"));
        for (String key : new java.util.HashSet<>(identity.keySet())) if (!keep.contains(key)) identity.remove(key);
        target.extras.putAll(identity);
    }

    static void recordReply(Notification notification, CharSequence text, long timestamp, String replyId) {
        ConversationRound round = readRound(notification.extras);
        if (!notification.extras.containsKey(ROUND)) round.latestPeerTime = Math.max(0, notification.when);
        if (!round.reply(replyId)) return;
        writeRound(notification.extras, round);
        Bundle reply = new Bundle();
        reply.putCharSequence("text", text);
        reply.putLong("time", timestamp);
        Bundle metadata = new Bundle();
        metadata.putString(REPLY_ID, replyId);
        reply.putBundle("extras", metadata);
        Parcelable[] old = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        Parcelable[] messages = old == null ? new Parcelable[1] : Arrays.copyOf(old, old.length + 1);
        messages[messages.length - 1] = reply;
        notification.extras.putParcelableArray(Notification.EXTRA_MESSAGES, messages);
        CharSequence[] history = notification.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY);
        CharSequence[] updated = new CharSequence[history == null ? 1 : history.length + 1];
        updated[0] = text;
        if (history != null) System.arraycopy(history, 0, updated, 1, history.length);
        notification.extras.putCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY, updated);
    }

    static String newReplyId() { return UUID.randomUUID().toString(); }

    private static ConversationRound readRound(Bundle extras) {
        ConversationRound round = new ConversationRound();
        Bundle data = extras.getBundle(ROUND);
        if (data == null) return round;
        round.latestPeerTime = data.getLong("latestPeer");
        round.cutoff = data.getLong("cutoff");
        ArrayList<String> active = data.getStringArrayList("active");
        ArrayList<String> pending = data.getStringArrayList("pending");
        if (active != null) round.activeReplies.addAll(active);
        if (pending != null) round.pendingReplies.addAll(pending);
        return round;
    }

    private static void writeRound(Bundle extras, ConversationRound round) {
        Bundle data = new Bundle();
        data.putLong("latestPeer", round.latestPeerTime);
        data.putLong("cutoff", round.cutoff);
        data.putStringArrayList("active", new ArrayList<>(round.activeReplies));
        data.putStringArrayList("pending", new ArrayList<>(round.pendingReplies));
        extras.putBundle(ROUND, data);
    }

    private static void updateInputHistory(Bundle extras, List<Message> messages) {
        List<CharSequence> replies = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--)
            if (messages.get(i).getPerson() == null) replies.add(messages.get(i).getText());
        if (replies.isEmpty()) extras.remove(Notification.EXTRA_REMOTE_INPUT_HISTORY);
        else extras.putCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY, replies.toArray(new CharSequence[0]));
    }
    private static String string(CharSequence value) { return value == null ? null : value.toString(); }
    private NotificationMessages() {}
}
