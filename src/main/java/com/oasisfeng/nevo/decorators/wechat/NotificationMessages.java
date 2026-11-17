package com.oasisfeng.nevo.decorators.wechat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.MessagingStyle;
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
import java.util.regex.Pattern;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;
import static androidx.core.app.NotificationCompat.EXTRA_CONVERSATION_TITLE;
import static androidx.core.app.NotificationCompat.EXTRA_IS_GROUP_CONVERSATION;
import static androidx.core.app.NotificationCompat.EXTRA_MESSAGES;
import static androidx.core.app.NotificationCompat.EXTRA_SELF_DISPLAY_NAME;

/** Common Android adapter for all notification/reply paths. */
final class NotificationMessages {
    static final String REPLY_ID = "nevo.replyId";
    static final String STORED = "nevo.messagesNormalized";
    static final String TITLE = "nevo.conversationTitle";
    static final String KEY = "nevo.conversationKey";
    static final String PEER_ID = "nevo.notificationPeerId";
    static final String ROUND = "nevo.conversationRound";
    private static final String MESSAGE_ROUND = "nevo.messageRound";
    private static final String MESSAGE_NOTIFICATION_TOKEN = "nevo.messageNotificationToken";
    static final String SERVER_ID = "nevo.wechat.messageServerId";
    static final String RECALLED_IDS = "nevo.wechat.recalledServerIds";
    static final String RECALL_PROMPT = "nevo.wechat.recallPrompt";
    static final String REMOTE_INPUT_HISTORY_ITEMS = "android.remoteInputHistoryItems";
    static final String REMOTE_INPUT_SPINNER = "android.remoteInputSpinner";
    static final String HIDE_SMART_REPLIES = "android.hideSmartReplies";
    private static final Pattern UNREAD_COUNT_PREFIX = Pattern.compile("^\\[\\d+(?:条|則)?\\]");

    private static final String KEY_TEXT = "text";
    private static final String KEY_TIMESTAMP = "time";
    private static final String KEY_SENDER = "sender";
    @RequiresApi(P) private static final String KEY_SENDER_PERSON = "sender_person";
    private static final String KEY_DATA_MIME_TYPE = "type";
    private static final String KEY_DATA_URI= "uri";
    private static final String KEY_EXTRAS_BUNDLE = "extras";

    static void flatIntoExtras(final MessagingStyle messaging, final Bundle extras) {
        extras.putBoolean(NotificationMessages.STORED, true);
        final Person user = messaging.getUser();
        if (user != null) {
            extras.putCharSequence(EXTRA_SELF_DISPLAY_NAME, user.getName());
            if (SDK_INT >= P) extras.putParcelable(Notification.EXTRA_MESSAGING_PERSON, toAndroidPerson(user));    // Not included in NotificationCompat
        }
        if (messaging.getConversationTitle() != null) extras.putCharSequence(EXTRA_CONVERSATION_TITLE, messaging.getConversationTitle());
        else extras.remove(EXTRA_CONVERSATION_TITLE);
        final List<Message> messages = messaging.getMessages();
        extras.putParcelableArray(EXTRA_MESSAGES, getBundleArrayForMessages(messages));
        extras.putBoolean(EXTRA_IS_GROUP_CONVERSATION, messaging.isGroupConversation());
    }

    static Bundle[] getBundleArrayForMessages(final List<Message> messages) {
        final int N = messages.size();
        final Bundle[] bundles = new Bundle[N];
        for (int i = 0; i < N; i ++) bundles[i] = toBundle(messages.get(i));
        return bundles;
    }

    static Bundle toBundle(final Message message) {
        final Bundle bundle = new Bundle();
        bundle.putCharSequence(KEY_TEXT, message.getText());
        bundle.putLong(KEY_TIMESTAMP, message.getTimestamp());        // Must be included even for 0
        final Person sender = message.getPerson();
        if (sender != null) {
            bundle.putBundle("person", sender.toBundle());
            bundle.putCharSequence(KEY_SENDER, sender.getName());    // Legacy listeners need this
            if (SDK_INT >= P) bundle.putParcelable(KEY_SENDER_PERSON, toAndroidPerson(sender));
        }
        if (message.getDataMimeType() != null) bundle.putString(KEY_DATA_MIME_TYPE, message.getDataMimeType());
        if (message.getDataUri() != null) bundle.putParcelable(KEY_DATA_URI, message.getDataUri());
        if (! message.getExtras().isEmpty()) bundle.putBundle(KEY_EXTRAS_BUNDLE, message.getExtras());
        return bundle;
    }

    @RequiresApi(P) @SuppressLint("RestrictedApi") private static android.app.Person toAndroidPerson(final Person user) {
        return user.toAndroidPerson();
    }

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
        return incoming(conversation, notification, true);
    }

    private static Message incoming(Conversation conversation, Notification notification, boolean translateEmoji) {
        CharSequence raw = NotificationMarkdown.originalText(notification.extras);
        if (TextUtils.isEmpty(raw)) return null;
        String text = UNREAD_COUNT_PREFIX.matcher(raw.toString()).replaceFirst("");
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
        Message message = new Message(translateEmoji ? EmojiTranslator.translateMessageText(text) : text,
                notification.when, peer(conversation, sender));
        // This ID comes from NotificationItem itself, never a timestamp/text search in history.
        long serverId = notification.extras.getLong(SERVER_ID);
        if (serverId > 0) message.getExtras().putLong(SERVER_ID, serverId);
        return message;
    }

    static Message read(Bundle bundle, Conversation conversation, boolean messagingContext, boolean historical) {
        CharSequence text = NotificationMarkdown.originalMessageText(bundle);
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
        message.getExtras().remove(NotificationMarkdown.RAW_TEXT);
        message.getExtras().remove(NotificationMarkdown.OWNER);
        String mime = bundle.getString("type");
        Parcelable uri = bundle.getParcelable("uri");
        if (mime != null && uri instanceof android.net.Uri) message.setData(mime, (android.net.Uri) uri);
        return message;
    }

    static List<Message> rebuild(Conversation conversation, Notification current, List<Notification> archive) {
        ConversationRound round = conversation.round;
        long currentToken = current.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN);
        if (currentToken == 0) current.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN,
                currentToken = WeChatNotificationRemoval.nextToken());
        round.end(current.extras.getLong(WeChatNotificationRemoval.READ_THROUGH_TOKEN));
        final long token = currentToken;
        List<Notification> prior = NotificationHistoryWindow.prior(archive, current,
                n -> n.extras.getBoolean(STORED),
                n -> belongsTo(conversation, n) && round.retains(n.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN))
                        && (n.extras.getBoolean(STORED) || (WeChatMessage.isChat(n.tickerText) && !n.extras.getBoolean(RECALL_PROMPT)))
                        && n.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN) != token);
        if (conversation.notificationPeerId == null) {
            Notification identity = current;
            for (Notification older : prior) if (older.extras.getBoolean(STORED)) { identity = older; break; }
            if (identity.extras.getBoolean(STORED)) conversation.notificationPeerId = identity.extras.getString(PEER_ID);
        }
        peerId(conversation);
        List<MessageIdentityPolicy.Entry<Message>> entries = new ArrayList<>();
        RecallHistory recalls = new RecallHistory();
        for (Notification older : archive) if (belongsTo(conversation, older)) {
            recalls.addAll(older.extras.getLongArray(RECALLED_IDS));
            round.end(older.extras.getLong(WeChatNotificationRemoval.READ_THROUGH_TOKEN));
        }
        for (Notification older : prior) {
            if (older.extras.getBoolean(STORED)) add(entries, conversation, older, true, 0);
            else {
                Message message = incoming(conversation, older, false);
                if (message != null) { stampNotificationToken(message, older); addEntry(entries, message, 2); }
            }
        }
        add(entries, conversation, current, false, 1);
        recalls.addAll(current.extras.getLongArray(RECALLED_IDS));
        current.extras.putLongArray(RECALLED_IDS, recalls.ids());
        // Old module snapshots may contain self rows. They are no longer a transcript source.
        entries.removeIf(entry -> entry.value.getPerson() == null || entry.value.getExtras().getBoolean(RECALL_PROMPT)
                || recalls.contains(entry.value.getExtras().getLong(SERVER_ID)));
        if (!conversation.isGroupChat() && conversation.getType() != Conversation.TYPE_BOT_MESSAGE) {
            if (conversation.icon == null) for (int i = entries.size() - 1; i >= 0; i--) {
                Person person = entries.get(i).value.getPerson();
                if (person.getIcon() != null && isKnownPeer(conversation, current, entries.get(i))) {
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
        entries = MessageIdentityPolicy.distinctStableIds(entries);
        // Preserve original generation across both active snapshots and retired rounds.
        for (MessageIdentityPolicy.Entry<Message> entry : entries) {
            Bundle metadata = entry.value.getExtras();
            metadata.putLong(MESSAGE_NOTIFICATION_TOKEN,
                    round.firstToken(entry.stableId, metadata.getLong(MESSAGE_NOTIFICATION_TOKEN)));
            metadata.remove(MESSAGE_ROUND);
        }
        entries.removeIf(entry -> !round.retains(entry.value.getExtras().getLong(MESSAGE_NOTIFICATION_TOKEN)));
        List<Message> result = MessageIdentityPolicy.merge(entries, 25,
                java.util.Comparator.comparingLong(entry -> entry.value.getExtras().getLong(MESSAGE_NOTIFICATION_TOKEN)));
        if (current.extras.getBoolean(RECALL_PROMPT) || recalls.contains(current.extras.getLong(SERVER_ID)))
            current.extras.putCharSequence(Notification.EXTRA_TEXT,
                    result.isEmpty() ? "" : result.get(result.size() - 1).getText());
        if (!current.extras.getBoolean(STORED)) {
            if (current.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) != null
                    || current.extras.getParcelableArray(REMOTE_INPUT_HISTORY_ITEMS) != null
                    || current.extras.getBoolean(REMOTE_INPUT_SPINNER))
                current.flags &= ~Notification.FLAG_ONLY_ALERT_ONCE;
            clearRemoteInput(current.extras);
        }
        current.extras.remove(ROUND);
        if (round.endThroughToken > 0)
            current.extras.putLong(WeChatNotificationRemoval.READ_THROUGH_TOKEN, round.endThroughToken);
        current.extras.putString(PEER_ID, peerId(conversation));
        if (BuildConfig.DEBUG) Log.d("WeChat.Identity", "source=merge inputs=" + entries.size()
                + " output=" + result.size() + " currentTime=" + current.when);
        return result;
    }

    /** Presentation-only update of a still-active card; this does not restore the retired archive. */
    static Notification completeReplyUi(Context context, Notification active, CharSequence reply) {
        Notification copy = active.clone();
        clearRemoteInput(copy.extras);
        // Echo belongs only to this visible card, never to the retired unread archive.
        // Use the submitted text, since SystemUI's input history can be missing or stale.
        if (!TextUtils.isEmpty(reply) && "android.app.Notification$MessagingStyle".equals(
                copy.extras.getString(Notification.EXTRA_TEMPLATE))) {
            Parcelable[] previous = copy.extras.getParcelableArray(EXTRA_MESSAGES);
            int retained = previous == null ? 0
                    : Math.min(previous.length, Notification.MessagingStyle.MAXIMUM_RETAINED_MESSAGES - 1);
            Parcelable[] messages = new Parcelable[retained + 1];
            if (retained > 0) System.arraycopy(previous, previous.length - retained, messages, 0, retained);
            messages[retained] = toBundle(new Message(reply, System.currentTimeMillis(), (Person) null));
            copy.extras.putParcelableArray(EXTRA_MESSAGES, messages);
        }
        Notification completed = Notification.Builder.recoverBuilder(context, copy).setOnlyAlertOnce(true).build();
        NevoDecoratorService.LocalDecorator.markActionsSerialized(completed);
        return completed;
    }

    private static void clearRemoteInput(Bundle extras) {
        extras.remove(Notification.EXTRA_REMOTE_INPUT_HISTORY);
        extras.remove(REMOTE_INPUT_HISTORY_ITEMS);
        extras.putBoolean(REMOTE_INPUT_SPINNER, false);
        extras.putBoolean(HIDE_SMART_REPLIES, false);
    }

    private static boolean isKnownPeer(Conversation conversation, Notification current, MessageIdentityPolicy.Entry<Message> entry) {
        Person person = entry.value.getPerson();
        return person != null && (entry.source == 0 || current.extras.getBoolean(STORED)
                || TextUtils.equals(person.getName(), conversation.title)
                || (person.getKey() != null && (person.getKey().equals(conversation.notificationPeerId)
                || person.getKey().equals(conversation.key))));
    }

    private static void add(List<MessageIdentityPolicy.Entry<Message>> entries, Conversation conversation,
                            Notification n, boolean historical, int source) {
        Bundle extras = n.extras;
        boolean normalized = extras.getBoolean(STORED);
        // A raw system recall prompt is an event, not a peer message or a new round.
        if (!normalized && extras.getBoolean(RECALL_PROMPT)) return;
        boolean context = normalized || ("android.app.Notification$MessagingStyle".equals(
                extras.getString(Notification.EXTRA_TEMPLATE)) && hasUser(extras));
        Parcelable[] bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        int before = entries.size();
        if (bundles != null && (normalized || conversation.round.endThroughToken == 0)) for (Parcelable parcel : bundles) {
            if (!(parcel instanceof Bundle)) continue;
            Message message = read((Bundle) parcel, conversation, context, historical || !context);
            if (message != null) {
                stampNotificationToken(message, n);
                addEntry(entries, message, source);
            }
        }
        // For a raw notification, EXTRA_TEXT is the current incoming message. Unstructured
        // bundles can be stale history; use only explicitly identified history plus this line.
        if (!historical && ((!context) || entries.size() == before)) {
            Message latest = incoming(conversation, n);
            boolean represented = false;
            if (latest != null && latest.getTimestamp() > 0) for (int i = before; i < entries.size(); i++) {
                Message candidate = entries.get(i).value;
                // Let the identity policy compare trusted IDs after sender canonicalization.
                // Text/time alone must not hide a distinct ID or an unidentified occurrence.
                if (latest.getExtras().getLong(SERVER_ID) > 0 || candidate.getExtras().getLong(SERVER_ID) > 0) continue;
                if (candidate.getPerson() != null && candidate.getTimestamp() == latest.getTimestamp()
                        && TextUtils.equals(candidate.getText(), latest.getText())
                        && (!conversation.isGroupChat() || TextUtils.equals(candidate.getPerson().getName(), latest.getPerson().getName())))
                    represented = true;
            }
            if (latest != null && !represented) {
                stampNotificationToken(latest, n);
                addEntry(entries, latest, source + 1);
            }
        }
    }

    private static void stampNotificationToken(Message message, Notification notification) {
        if (!message.getExtras().containsKey(MESSAGE_NOTIFICATION_TOKEN))
            message.getExtras().putLong(MESSAGE_NOTIFICATION_TOKEN,
                    notification.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN));
    }

    static void clearReadHistory(Conversation conversation, Notification notification, long token) {
        conversation.round.end(token);
        notification.extras.putLong(WeChatNotificationRemoval.READ_THROUGH_TOKEN, conversation.round.endThroughToken);
        if (notification.extras.getBoolean(STORED))
            notification.extras.putParcelableArray(Notification.EXTRA_MESSAGES,
                    getBundleArrayForMessages(rebuild(conversation, notification, java.util.Collections.emptyList())));
    }

    private static boolean hasUser(Bundle extras) {
        if (Build.VERSION.SDK_INT >= 28 && extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON) instanceof android.app.Person)
            return !TextUtils.isEmpty(((android.app.Person) extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON)).getName());
        return !TextUtils.isEmpty(extras.getCharSequence(NotificationCompat.EXTRA_SELF_DISPLAY_NAME));
    }

    private static void addEntry(List<MessageIdentityPolicy.Entry<Message>> entries, Message message, int source) {
        Person person = message.getPerson();
        Object identity = person == null ? "self" : Arrays.asList(string(person.getKey()), string(person.getUri()), string(person.getName()));
        long serverId = message.getExtras().getLong(SERVER_ID);
        Object signature = serverId > 0 ? Arrays.asList(identity, "server", serverId)
                : Arrays.asList(identity, string(message.getText()), message.getDataMimeType(), message.getDataUri());
        entries.add(new MessageIdentityPolicy.Entry<>(message, message.getTimestamp(), signature,
                person == null ? message.getExtras().getString(REPLY_ID) : null, source,
                serverId > 0 ? signature : null));
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
        notification.extras.remove(ROUND);
        if (conversation.round.endThroughToken > 0) notification.extras.putLong(WeChatNotificationRemoval.READ_THROUGH_TOKEN, conversation.round.endThroughToken);
    }

    /** BigPictureStyle may rebuild presentation extras; keep the original message identities. */
    static void copyIdentityExtras(Notification source, Notification target) {
        Bundle identity = new Bundle(source.extras);
        java.util.Set<String> keep = new java.util.HashSet<>(Arrays.asList(STORED, TITLE, KEY, PEER_ID, ROUND,
                SERVER_ID, RECALLED_IDS, RECALL_PROMPT, WeChatRecallEvents.VERIFIED_KEY,
                WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN,
                WeChatNotificationRemoval.READ_THROUGH_TOKEN,
                ConversationClassification.TYPE, ConversationClassification.SOURCE,
                WeChatNotificationAppearance.OWNER,
                Notification.EXTRA_REMOTE_INPUT_HISTORY, NotificationMarkdown.RAW_TEXT,
                Notification.EXTRA_MESSAGES, "android.messagingUser", "android.selfDisplayName"));
        for (String key : new java.util.HashSet<>(identity.keySet())) if (!keep.contains(key)) identity.remove(key);
        target.extras.putAll(identity);
    }

    static void markRecall(Notification notification, long serverId) {
        RecallHistory history = new RecallHistory();
        history.addAll(notification.extras.getLongArray(RECALLED_IDS));
        history.add(serverId);
        notification.extras.putLongArray(RECALLED_IDS, history.ids());
    }

    static boolean containsServerId(Notification notification, long serverId) {
        if (serverId <= 0) return false;
        Parcelable[] messages = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) for (Parcelable message : messages) {
            if (!(message instanceof Bundle)) continue;
            Bundle extras = ((Bundle) message).getBundle("extras");
            if (extras != null && extras.getLong(SERVER_ID) == serverId) return true;
        }
        return false;
    }

    /** A raw repost of a message seen while transcription was off is not a new voice message. */
    static boolean isFirstAppearance(Notification notification, long serverId) {
        long token = notification.extras.getLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN);
        if (serverId <= 0 || token <= 0) return false;
        Parcelable[] messages = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) for (Parcelable message : messages) {
            if (!(message instanceof Bundle)) continue;
            Bundle metadata = ((Bundle) message).getBundle("extras");
            if (metadata != null && metadata.getLong(SERVER_ID) == serverId
                    && metadata.getLong(MESSAGE_NOTIFICATION_TOKEN) == token) return true;
        }
        return false;
    }

    private static String string(CharSequence value) { return value == null ? null : value.toString(); }
}
