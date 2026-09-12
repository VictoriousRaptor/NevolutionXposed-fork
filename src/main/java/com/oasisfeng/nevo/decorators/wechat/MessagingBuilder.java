package com.oasisfeng.nevo.decorators.wechat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Notification.Action;
import android.app.Notification.CarExtender;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LongSparseArray;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.xposed.MainHook;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import static android.app.Notification.EXTRA_REMOTE_INPUT_HISTORY;
import static android.app.Notification.EXTRA_TEXT;
import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;
import static android.os.Build.VERSION_CODES.S;
import static androidx.core.app.NotificationCompat.EXTRA_CONVERSATION_TITLE;
import static androidx.core.app.NotificationCompat.EXTRA_IS_GROUP_CONVERSATION;
import static androidx.core.app.NotificationCompat.EXTRA_MESSAGES;
import static androidx.core.app.NotificationCompat.EXTRA_SELF_DISPLAY_NAME;

import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.R;

import static com.oasisfeng.nevo.decorators.wechat.WeChatMessage.SENDER_MESSAGE_SEPARATOR;
import static com.oasisfeng.nevo.sdk.NevoDecoratorService.TEMPLATE_BIG_PICTURE;
import static com.oasisfeng.nevo.sdk.NevoDecoratorService.TEMPLATE_MESSAGING;
import static com.oasisfeng.nevo.sdk.NevoDecoratorService.LocalDecorator.setActions;


/**
 * Build the modernized {@link MessagingStyle} for WeChat conversation.
 *
 * Refactored by Oasis on 2018-8-9.
 */
class MessagingBuilder {

	private static final int MAX_NUM_HISTORICAL_LINES = 10;

	private static final String ACTION_REPLY = "REPLY";
	private static final String ACTION_MENTION = "MENTION";
	private static final String ACTION_ZOOM = "ZOOM";
	private static final String ACTION_SYNTHETIC_REPLY = "SYNTHETIC_REPLY";
	private static final String SCHEME_ID = "id";
	private static final String EXTRA_REPLY_ACTION = "pending_intent";
	private static final String EXTRA_RESULT_KEY = "result_key";
	private static final String EXTRA_REPLY_PREFIX = "reply_prefix";
	private static final String DEFAULT_AUTO_REPLY_RESULT_KEY = MainHook.WECHAT_AUTO_REPLY_RESULT_KEY;

	private static void logReply(final String stage, final String detail) {
		Log.d(TAG, "NX_REPLY stage=" + stage + (detail == null || detail.isEmpty() ? "" : " " + detail));
	}

	private static final String KEY_TEXT = "text";
	private static final String KEY_TIMESTAMP = "time";
	private static final String KEY_SENDER = "sender";
	@RequiresApi(P) private static final String KEY_SENDER_PERSON = "sender_person";
	private static final String KEY_DATA_MIME_TYPE = "type";
	private static final String KEY_DATA_URI= "uri";
	private static final String KEY_EXTRAS_BUNDLE = "extras";

	private static final String KEY_USERNAME = "key_username";
	private static final String MENTION_SEPARATOR = " ";			// Separator between @nick and text. It's not a regular white space, but U+2005.

	public static int guessType(String key) {
		if (key.endsWith("@chatroom") || key.endsWith("@im.chatroom"/* WeWork */))
			return Conversation.TYPE_GROUP_CHAT;
		if (key.startsWith("gh_"))
			return Conversation.TYPE_BOT_MESSAGE;
		return Conversation.TYPE_DIRECT_MESSAGE;
	}

	/**
	 * 从已存档消息重建会话
	 * 
	 * @param conversation 会话的内存存根
	 * @param n 当前消息
	 * @param title 标题
	 * @param archieve 消息存档
	 * @return 会话的图形化
	 */
	@Nullable MessagingStyle buildFromArchive(final Conversation conversation, final Notification n, final CharSequence title, final List<Notification> archive) {
		// Chat history in big content view
		if (archive.isEmpty()) {
			if (BuildConfig.DEBUG) Log.d(TAG, "No history");
			return null;
		}

		final LongSparseArray<CharSequence> tickerArray = new LongSparseArray<>(MAX_NUM_HISTORICAL_LINES);
		final LongSparseArray<CharSequence> textArray = new LongSparseArray<>(MAX_NUM_HISTORICAL_LINES);
		CharSequence text;
		int count = 0, num_lines_with_colon = 0;
		final String redundant_prefix = title.toString() + SENDER_MESSAGE_SEPARATOR;
		for (final Notification notification : archive) {
			tickerArray.put(notification.when, notification.tickerText);
			final Bundle its_extras = notification.extras;
			final CharSequence its_title = EmojiTranslator.translate(its_extras.getCharSequence(Notification.EXTRA_TITLE));
			if (! title.equals(its_title)) {
				// ID reset by WeChat due to notification removal in previous evolving
				if (BuildConfig.DEBUG) Log.d(TAG, "Skip other conversation with the same key in archive");
				continue;
			}
			final CharSequence its_text = its_extras.getCharSequence(EXTRA_TEXT);
			if (its_text == null) {
				Log.w(TAG, "No text in archived notification.");
				continue;
			}
			final int result = trimAndExtractLeadingCounter(its_text);
			if (result >= 0) {
				count = result & 0xFFFF;
				CharSequence trimmed_text = its_text.subSequence(result >> 16, its_text.length());
				if (trimmed_text.toString().startsWith(redundant_prefix))	// Remove redundant prefix
					trimmed_text = trimmed_text.subSequence(redundant_prefix.length(), trimmed_text.length());
				else if (trimmed_text.toString().indexOf(SENDER_MESSAGE_SEPARATOR) > 0) num_lines_with_colon ++;
				textArray.put(notification.when, trimmed_text);
			} else {
				count = 1;
				textArray.put(notification.when, text = its_text);
				if (text.toString().indexOf(SENDER_MESSAGE_SEPARATOR) > 0) num_lines_with_colon ++;
			}
		}
		n.number = count;
		if (textArray.size() == 0) {
			Log.w(TAG, "No lines extracted, expected " + count);
			return null;
		}

		final MessagingStyle messaging = new MessagingStyle(mUserSelf);
		final boolean sender_inline = num_lines_with_colon == textArray.size();
		for (int i = 0, size = textArray.size(); i < size; i++)	{		// All lines have colon in text
			messaging.addMessage(buildMessage(conversation, textArray.keyAt(i), tickerArray.valueAt(i), textArray.valueAt(i), sender_inline ? null : title.toString()));
		}
		return messaging;
	}

	/**
	 * 从车载扩展信息重建会话
	 */
	@Nullable MessagingStyle buildFromExtender(final Conversation conversation, final int id, final Notification n, final CharSequence title, final List<Notification> archive) {
		final Notification.CarExtender extender = new Notification.CarExtender(n);
		final CarExtender.UnreadConversation convs = extender.getUnreadConversation();
		if (convs == null) {
			final MessagingStyle fromActions = buildFromActions(conversation, id, n, title, archive);
			if (fromActions != null) return fromActions;
			return buildWithSyntheticReply(conversation, id, n, title, archive);
		}
		final long latest_timestamp = convs.getLatestTimestamp();
		if (latest_timestamp > 0) n.when = conversation.timestamp = latest_timestamp;

		final PendingIntent on_reply = convs.getReplyPendingIntent();
		if (conversation.key == null) {
			try {
				if (on_reply != null) on_reply.send(mContext, 0, null, (p, intent, r, d, b) -> {
					final String key = conversation.key = intent.getStringExtra(KEY_USERNAME);	// setType() below will trigger rebuilding of conversation sender.
					conversation.setType(guessType(key));
				}, null);
			} catch (final PendingIntent.CanceledException e) {
				Log.e(TAG, "Error parsing reply intent.", e);
			}
		}

		final MessagingStyle messaging = new MessagingStyle(mUserSelf);
		final Message[] messages = WeChatMessage.buildFromCarConversation(conversation, convs, archive);
		if (hasUsableContent(messages)) {
			for (final Message message : messages) messaging.addMessage(message);
		} else {
			// WeChat 8.0.72 fills its car conversation with placeholders ("[消息]")
			// for ordinary messages. Keep the real notification text and use the car
			// conversation only for the reply intent below.
			logReply("car_content_placeholder", "notificationId=" + id + " source=notification_text");
			final CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
			if (text != null) {
				final String sender = extractSenderFromText(text);
				final CharSequence msgText = sender != null
						? text.subSequence(sender.length() + SENDER_MESSAGE_SEPARATOR.length(), text.length()) : text;
				messaging.addMessage(new Message(msgText, n.when,
						new Person.Builder().setName(conversation.title != null ? conversation.title.toString() : " ").build()));
			}
		}

		// 从 EXTRA_MESSAGES 保留之前的用户回复（关键！其他消息由车载会话提供）
		final boolean hasUserReplyInMessages = appendUserRepliesFromMessages(messaging, n);

		final PendingIntent on_read = convs.getReadPendingIntent();
		if (on_read != null) mMarkReadPendingIntents.put(id, on_read);	// Mapped by evolved key,

		final List<Action> actions = new ArrayList<>();
		// 回复：使用 RemoteInput 内联回复（修改版 HyperIsland 已保留 RemoteInput）
		final RemoteInput remote_input;
		if (SDK_INT >= N && on_reply != null && (remote_input = convs.getRemoteInput()) != null) {
			final CharSequence[] input_history = n.extras.getCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY);
			final PendingIntent proxy = proxyDirectReply(id, n, on_reply, remote_input, input_history, null);
			final RemoteInput.Builder reply_remote_input = new RemoteInput.Builder(remote_input.getResultKey()).addExtras(remote_input.getExtras())
					.setAllowFreeFormInput(true);
			final String participant = convs.getParticipant();
			if (participant != null) reply_remote_input.setLabel(participant);

			final Action.Builder reply_action = new Action.Builder(null, actionReply, proxy)
					.addRemoteInput(reply_remote_input.build()).setAllowGeneratedReplies(true);
			if (SDK_INT >= P) reply_action.setSemanticAction(Action.SEMANTIC_ACTION_REPLY);
			actions.add(reply_action.build());
		} else if (SDK_INT >= N && on_reply != null) {
			// Some WeChat builds omit RemoteInput from CarExtender but still provide a valid
			// reply PendingIntent. Preserve that version-specific PendingIntent instead of
			// inventing a broadcast to an obfuscated receiver.
			final RemoteInput fallbackRemoteInput = new RemoteInput.Builder(DEFAULT_AUTO_REPLY_RESULT_KEY)
					.setAllowFreeFormInput(true).setLabel(actionReply).build();
			final PendingIntent proxy = proxyDirectReply(id, n, on_reply, fallbackRemoteInput,
					n.extras.getCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY), null);
			final Action.Builder reply_action_builder = new Action.Builder(null, actionReply, proxy)
					.addRemoteInput(fallbackRemoteInput)
					.setAllowGeneratedReplies(true);
			if (SDK_INT >= P) reply_action_builder.setSemanticAction(Action.SEMANTIC_ACTION_REPLY);
			actions.add(reply_action_builder.build());
			logReply("action_native_fallback", "notificationId=" + id + " resultKey=" + DEFAULT_AUTO_REPLY_RESULT_KEY);
		}
		// 放大
		if (n.extras.containsKey(WeChatDecorator.EXTRA_PICTURE_PATH)) {
			final Intent intent = new Intent(ACTION_ZOOM).setData(Uri.fromParts(SCHEME_ID, Integer.toString(id), null));
			final Action.Builder zoom_action = new Action.Builder(null, actionZoom, PendingIntent.getBroadcast(mContext, 0, intent.setPackage(mContext.getPackageName()), pendingIntentFlags()));
			actions.add(zoom_action.build());
		}
		// 从 EXTRA_REMOTE_INPUT_HISTORY 补充用户回复（仅当 EXTRA_MESSAGES 中没有时）
		appendRemoteInputHistory(messaging, n, hasUserReplyInMessages);

		setActions(n, actions.toArray(new Action[actions.size()]));
		return messaging;
	}

	private static Message buildMessage(final Conversation conversation, final long when, final @Nullable CharSequence ticker,
										final CharSequence text, @Nullable String sender) {
		CharSequence actual_text = text;
		if (sender == null) {
			sender = extractSenderFromText(text);
			if (sender != null) {
				actual_text = text.subSequence(sender.length() + SENDER_MESSAGE_SEPARATOR.length(), text.length());
				if (TextUtils.equals(conversation.title, sender)) sender = null;		// In this case, the actual sender is user itself.
			}
		}
		actual_text = EmojiTranslator.translate(actual_text);

		final Person person;
		if (sender != null && sender.isEmpty()) person = null;		// Empty string as a special mark for "self"
		else if (conversation.isGroupChat()) {
			final String ticker_sender = ticker != null ? extractSenderFromText(ticker) : null;	// Group nick is used in ticker and content text, while original nick in sender.
			person = sender == null ? null : conversation.getGroupParticipant(sender, ticker_sender != null ? ticker_sender : sender);
		} else person = new Person.Builder().setName(conversation.title != null ? conversation.title.toString() : " ").build();
		return new Message(actual_text, when, person);
	}

	private static @Nullable String extractSenderFromText(final CharSequence text) {
		final int pos_colon = TextUtils.indexOf(text, SENDER_MESSAGE_SEPARATOR);
		return pos_colon > 0 ? text.toString().substring(0, pos_colon) : null;
	}

	/** The person shown for messages sent by the peer(s) of a conversation. */
	private static Person friendPerson(final Conversation conversation) {
		return new Person.Builder().setName(conversation.title != null ? conversation.title.toString() : " ").build();
	}

	/** Drops a leading "sender: " prefix from a message text. */
	private static CharSequence stripSenderPrefix(final CharSequence text) {
		final String sender = extractSenderFromText(text);
		return sender != null ? text.subSequence(sender.length() + SENDER_MESSAGE_SEPARATOR.length(), text.length()) : text;
	}

	/** Keeps the user's own replies from EXTRA_MESSAGES; peers' messages come from the car conversation. */
	private static boolean appendUserRepliesFromMessages(final MessagingStyle messaging, final Notification n) {
		final Bundle[] existingMessages = (Bundle[]) n.extras.getParcelableArray(EXTRA_MESSAGES);
		if (existingMessages == null) return false;
		boolean hasUserReply = false;
		for (final Bundle msgBundle : existingMessages) {
			final CharSequence text = msgBundle.getCharSequence(KEY_TEXT);
			final CharSequence sender = msgBundle.getCharSequence(KEY_SENDER);
			if (text == null || (sender != null && sender.length() > 0)) continue;
			if (BuildConfig.DEBUG) Log.d(TAG, "Preserving user reply from EXTRA_MESSAGES");
			messaging.addMessage(new Message(text, msgBundle.getLong(KEY_TIMESTAMP, 0), (Person) null));
			hasUserReply = true;
		}
		return hasUserReply;
	}

	/** Appends the replies recorded in EXTRA_REMOTE_INPUT_HISTORY unless the user's reply is already present. */
	private static void appendRemoteInputHistory(final MessagingStyle messaging, final Notification n, final boolean hasUserReply) {
		if (hasUserReply) return;
		final CharSequence[] history = n.extras.getCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY);
		if (history == null || history.length == 0) return;
		for (final CharSequence reply : history)
			if (reply != null && reply.length() > 0)
				messaging.addMessage(new Message(reply, System.currentTimeMillis(), (Person) null));
	}

	/**
	 * Rebuilds the message list from what the notification already carries: existing EXTRA_MESSAGES,
	 * then the archived notifications of the same conversation, then the current text, and finally
	 * the user's replies from EXTRA_REMOTE_INPUT_HISTORY.
	 */
	private static void appendKnownMessages(final MessagingStyle messaging, final Conversation conversation,
			final Notification n, final List<Notification> archive) {
		final Bundle[] existingMessages = (Bundle[]) n.extras.getParcelableArray(EXTRA_MESSAGES);
		boolean hasUserReply = false;
		if (existingMessages != null && existingMessages.length > 0) {
			for (final Bundle msgBundle : existingMessages) {
				final CharSequence text = msgBundle.getCharSequence(KEY_TEXT);
				if (text == null) continue;
				final CharSequence sender = msgBundle.getCharSequence(KEY_SENDER);
				final Person person;
				if (sender != null && sender.length() == 0) {
					person = null;	// 自己发的消息（KEY_SENDER 为空字符串）
					hasUserReply = true;
				} else if (sender != null) {
					person = conversation.isGroupChat()
							? conversation.getGroupParticipant(sender.toString(), sender.toString())
							: new Person.Builder().setName(sender.toString()).build();
				} else {
					person = friendPerson(conversation);	// KEY_SENDER 为 null，按朋友消息处理
				}
				messaging.addMessage(new Message(text, msgBundle.getLong(KEY_TIMESTAMP, 0), person));
			}
		} else if (archive != null && ! archive.isEmpty()) {
			for (final Notification archived : archive) {
				final CharSequence text = archived.extras.getCharSequence(Notification.EXTRA_TEXT);
				if (text != null) messaging.addMessage(new Message(stripSenderPrefix(text), archived.when, friendPerson(conversation)));
			}
		} else {
			final CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
			if (text != null) messaging.addMessage(new Message(stripSenderPrefix(text), n.when, friendPerson(conversation)));
		}
		appendRemoteInputHistory(messaging, n, hasUserReply);
	}

	/** @return the extracted count in 0xFF range and start position in 0xFF00 range */
	private static int trimAndExtractLeadingCounter(final CharSequence text) {
		// Parse and remove the leading "[n]" or [n条/則/…]
		if (text == null || text.length() < 4 || text.charAt(0) != '[') return - 1;
		int text_start = 2, count_end;
		while (text.charAt(text_start++) != ']') if (text_start >= text.length()) return - 1;

		try {
			final String num = text.subSequence(1, text_start - 1).toString();	// may contain the suffix "条/則"
			for (count_end = 0; count_end < num.length(); count_end++) if (! Character.isDigit(num.charAt(count_end))) break;
			if (count_end == 0) return - 1;			// Not the expected "unread count"
			final int count = Integer.parseInt(num.substring(0, count_end));
			if (count < 2) return - 1;

			return count < 0xFFFF ? (count & 0xFFFF) | ((text_start << 16) & 0xFFFF0000) : 0xFFFF | ((text_start << 16) & 0xFF00);
		} catch (final NumberFormatException ignored) {
			if (BuildConfig.DEBUG) Log.d(TAG, "Failed to parse the leading unread counter");
			return - 1;
		}
	}

	/** @return appropriate PendingIntent flags, including FLAG_MUTABLE on Android 12+ for RemoteInput. */
	private static int pendingIntentFlags() {
		return SDK_INT >= S ? (FLAG_UPDATE_CURRENT | FLAG_MUTABLE) : FLAG_UPDATE_CURRENT;
	}

	/** Build reply action directly from notification actions when CarExtender is absent (new WeChat versions). */
	/** True only when the car conversation carries real message text instead of placeholders. */
	private static boolean hasUsableContent(final Message[] messages) {
		if (messages == null || messages.length == 0) return false;
		for (final Message message : messages) {
			final CharSequence text = message.getText();
			if (text == null) continue;
			final String value = text.toString().trim();
			if (value.isEmpty()) continue;
			if ("[消息]".equals(value)) continue;
			return true;
		}
		return false;
	}

	@Nullable private MessagingStyle buildFromActions(final Conversation conversation, final int id, final Notification n, final CharSequence title, final List<Notification> archive) {
		final Action[] actions = n.actions;
		if (actions == null) return null;

		Action replyAction = null;
		RemoteInput replyRemoteInput = null;
		PendingIntent onReply = null;

		for (final Action action : actions) {
			if (action == null || action.actionIntent == null) continue;
			final RemoteInput[] remoteInputs = action.getRemoteInputs();
			if (remoteInputs != null) {
				for (final RemoteInput ri : remoteInputs) {
					if (ri != null && ri.getAllowFreeFormInput()) {
						replyAction = action;
						replyRemoteInput = ri;
						onReply = action.actionIntent;
						break;
					}
				}
				if (replyAction != null) break;
			}
		}

		if (onReply == null || replyRemoteInput == null) {
			if (BuildConfig.DEBUG) Log.d(TAG, "No reply action found in notification actions");
			return null;
		}

		if (BuildConfig.DEBUG) Log.d(TAG, "Found reply action via notification actions fallback");

		final PendingIntent onRead = n.deleteIntent;
		if (onRead != null) mMarkReadPendingIntents.put(id, onRead);

		final MessagingStyle messaging = new MessagingStyle(mUserSelf);

		// 用通知里已有的消息、归档通知或当前文本重建消息列表，并补上输入历史里的用户回复
		appendKnownMessages(messaging, conversation, n, archive);

		final List<Action> newActions = new ArrayList<>();
		final CharSequence[] input_history = n.extras.getCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY);

		if (n.extras.containsKey(WeChatDecorator.EXTRA_PICTURE_PATH)) {
			final Intent intent = new Intent(ACTION_ZOOM).setData(Uri.fromParts(SCHEME_ID, Integer.toString(id), null));
			final Action.Builder zoom_action = new Action.Builder(null, actionZoom,
					PendingIntent.getBroadcast(mContext, 0, intent.setPackage(mContext.getPackageName()), pendingIntentFlags()));
			newActions.add(zoom_action.build());
		}
		// 回复：使用 RemoteInput 内联回复（修改版 HyperIsland 已保留 RemoteInput）
		if (onReply != null && replyRemoteInput != null) {
			final PendingIntent proxy = proxyDirectReply(id, n, onReply, replyRemoteInput, input_history, null);
			final RemoteInput.Builder reply_remote_input = new RemoteInput.Builder(replyRemoteInput.getResultKey())
					.addExtras(replyRemoteInput.getExtras()).setAllowFreeFormInput(true);
			final Action.Builder reply_action_builder = new Action.Builder(null, actionReply, proxy)
					.addRemoteInput(reply_remote_input.build()).setAllowGeneratedReplies(true);
			if (SDK_INT >= P) reply_action_builder.setSemanticAction(Action.SEMANTIC_ACTION_REPLY);
			newActions.add(reply_action_builder.build());
		}

		setActions(n, newActions.toArray(new Action[0]));
		return messaging;
	}

	/** Intercept the PendingIntent in RemoteInput to update the notification with replied message upon success. */
	private PendingIntent proxyDirectReply(final int id, final Notification notification, final PendingIntent on_reply, final RemoteInput remote_input,
										   final @Nullable CharSequence[] input_history, final @Nullable String mention_prefix) {
		final Intent proxy = new Intent(mention_prefix != null ? ACTION_MENTION : ACTION_REPLY)		// Separate action to avoid PendingIntent overwrite.
				.putExtra(EXTRA_REPLY_ACTION, on_reply).putExtra(EXTRA_RESULT_KEY, remote_input.getResultKey())
				.setData(Uri.fromParts(SCHEME_ID, Integer.toString(id), null));
		if (mention_prefix != null) proxy.putExtra(EXTRA_REPLY_PREFIX, mention_prefix);
		if (SDK_INT >= N && input_history != null)
			proxy.putCharSequenceArrayListExtra(EXTRA_REMOTE_INPUT_HISTORY, new ArrayList<>(Arrays.asList(input_history)));
		return PendingIntent.getBroadcast(mContext, 0, proxy.setPackage(mContext.getPackageName()), pendingIntentFlags());
	}

	private final Set<String> mPendingReplies = new java.util.HashSet<>();

	private final BroadcastReceiver mReplyReceiver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent proxy_intent) {
		final PendingIntent reply_action = proxy_intent.getParcelableExtra(EXTRA_REPLY_ACTION);
		final String result_key = proxy_intent.getStringExtra(EXTRA_RESULT_KEY), reply_prefix = proxy_intent.getStringExtra(EXTRA_REPLY_PREFIX);
		final Uri data = proxy_intent.getData(); final Bundle results = RemoteInput.getResultsFromIntent(proxy_intent);
		if (data == null || reply_action == null || result_key == null || results == null) {
			logReply("native_drop", "reason=missing_metadata");
			return;
		}
		final CharSequence input = results.getCharSequence(result_key);
		if (input == null) {
			logReply("native_drop", "reason=missing_input resultKey=" + result_key + " availableKeys=" + results.keySet());
			return;
		}
		logReply("native_receiver", "notificationId=" + data.getSchemeSpecificPart() + " resultKey=" + result_key + " inputLength=" + input.length());
		// M3: 防止无限循环 — 使用 id:text 作为 key，避免 hashCode 碰撞
		final String replyKey = data.getSchemeSpecificPart() + ":" + input.toString();
		if (mPendingReplies.contains(replyKey)) return;
		mPendingReplies.add(replyKey);
		final CharSequence text;
		if (reply_prefix != null) {
			text = reply_prefix + input;
			results.putCharSequence(result_key, text);
			RemoteInput.addResultsToIntent(new RemoteInput[]{ new RemoteInput.Builder(result_key).build() }, proxy_intent, results);
		} else text = input;
		final ArrayList<CharSequence> input_history = SDK_INT >= N ? proxy_intent.getCharSequenceArrayListExtra(EXTRA_REMOTE_INPUT_HISTORY) : null;
		final String part = data.getSchemeSpecificPart();
		try {
			final Intent input_data = addTargetPackageAndWakeUp(reply_action);
			input_data.setClipData(proxy_intent.getClipData());
			// 关键修复：将 RemoteInput 结果附加到发送给 WeChat 的 Intent
			// 标准系统 UI 会自动处理，但 HyperIsland 等第三方灵动岛模块不会
			if (results != null) {
				RemoteInput.addResultsToIntent(
					new RemoteInput[]{ new RemoteInput.Builder(result_key).build() },
					input_data, results);
				logReply("native_remote_input_attached", "resultKey=" + result_key + " inputLength=" + text.length());
			}

			reply_action.send(mContext, 0, input_data, (pendingIntent, intent, _result_code, _result_data, _result_extras) -> {
				logReply("native_pending_intent_callback", "notificationId=" + part + " resultCode=" + _result_code);
				if (SDK_INT >= N) {
					final CharSequence[] inputs;
					if (input_history != null) {
						input_history.add(0, text);
						inputs = input_history.toArray(new CharSequence[0]);
					} else inputs = new CharSequence[] { text };
					final int id = Integer.parseInt(part);
					mController.recastNotification(id, n -> {
						// 清除 pre-applied 标记，允许重新处理
						com.oasisfeng.nevo.xposed.compat.XposedHelpers.setAdditionalInstanceField(n, "pre-applied", null);
						com.oasisfeng.nevo.xposed.compat.XposedHelpers.setAdditionalInstanceField(n, "applied", null);
						final Bundle extras = n.extras;
						// 按女娲石源码逻辑添加用户回复
						Bundle[] messages;
						if (extras.getParcelableArray(EXTRA_MESSAGES) != null) {
							messages = (Bundle[]) extras.getParcelableArray(EXTRA_MESSAGES);
						} else {
							messages = new Bundle[0];
						}
						Bundle[] new_messages = new Bundle[messages.length + 1];
						System.arraycopy(messages, 0, new_messages, 0, messages.length);
						Bundle userMessage = new Bundle();
						userMessage.putCharSequence(KEY_TEXT, text);
						userMessage.putLong(KEY_TIMESTAMP, System.currentTimeMillis());
						// 不设置 KEY_SENDER，让系统自动识别为自己的消息
						new_messages[messages.length] = userMessage;
						extras.putParcelableArray(EXTRA_MESSAGES, new_messages);
						extras.putCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY, inputs);
					});
					markRead(id);
				}
			}, null);
		} catch (final PendingIntent.CanceledException e) {
			Log.w(TAG, "NX_REPLY stage=native_dispatch_failed reason=pending_intent_cancelled notificationId=" + part, e);
			abortBroadcast();
		} catch (final RuntimeException e) {
			Log.w(TAG, "NX_REPLY stage=native_dispatch_failed reason=runtime notificationId=" + part, e);
			abortBroadcast();
		} finally {
			// 延迟清除防循环标志
			final String finalReplyKey = replyKey;
			new Handler(Looper.getMainLooper()).post(() -> mPendingReplies.remove(finalReplyKey));
		}
	} };

	private final BroadcastReceiver mZoomReceiver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent proxy_intent) {
		final String action = proxy_intent.getAction();
		final Uri data = proxy_intent.getData(); final Bundle results = RemoteInput.getResultsFromIntent(proxy_intent);
		final String key = data.getSchemeSpecificPart();
		// final Bundle addition = new Bundle();
		mController.recastNotification(Integer.parseInt(key), n -> {
			final Bundle extras = n.extras;
			if (BuildConfig.DEBUG) Log.d(TAG, "bitmap " + extras.getParcelable(Notification.EXTRA_PICTURE));
			if (TEMPLATE_MESSAGING.equals(extras.getString(Notification.EXTRA_TEMPLATE))) {
				final String path = extras.getString(WeChatDecorator.EXTRA_PICTURE_PATH);
				final BitmapFactory.Options options = new BitmapFactory.Options();
				options.inPreferredConfig = SDK_INT >= O ? Bitmap.Config.HARDWARE : Bitmap.Config.ARGB_8888;
				// extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_BIG_PICTURE);
				extras.putParcelable(Notification.EXTRA_PICTURE, BitmapFactory.decodeFile(path, options));
				// extras.putCharSequence(Notification.EXTRA_SUMMARY_TEXT, text);
				extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_BIG_PICTURE);
			} else {
				extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_MESSAGING); // TODO
			}
			if (BuildConfig.DEBUG) Log.d(TAG, "bitmap " + extras.getParcelable(Notification.EXTRA_PICTURE));
		});
	} };

	/** @param id the notification id */
	void markRead(final int id) {
		final PendingIntent action = mMarkReadPendingIntents.remove(id);
		if (action == null) return;
		try {
			action.send(mContext, 0, addTargetPackageAndWakeUp(action));
		} catch (final PendingIntent.CanceledException e) {
			Log.w(TAG, "Mark-read action is already cancelled: " + id);
		}
	}

	/** Ensure the PendingIntent works even if WeChat is stopped or background-restricted. */
	@NonNull private static Intent addTargetPackageAndWakeUp(final PendingIntent action) {
		return new Intent().addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES).setPackage(action.getCreatorPackage());
	}

	static void flatIntoExtras(final MessagingStyle messaging, final Bundle extras) {
		final Person user = messaging.getUser();
		if (user != null) {
			extras.putCharSequence(EXTRA_SELF_DISPLAY_NAME, user.getName());
			if (SDK_INT >= P) extras.putParcelable(Notification.EXTRA_MESSAGING_PERSON, toAndroidPerson(user));	// Not included in NotificationCompat
		}
		if (messaging.getConversationTitle() != null) extras.putCharSequence(EXTRA_CONVERSATION_TITLE, messaging.getConversationTitle());
		final List<Message> messages = messaging.getMessages();
		// Log.d(TAG, "messages " + messages.size());
		if (! messages.isEmpty()) extras.putParcelableArray(EXTRA_MESSAGES, getBundleArrayForMessages(messages));
		//if (! mHistoricMessages.isEmpty()) extras.putParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES, MessagingBuilder.getBundleArrayForMessages(mHistoricMessages));
		extras.putBoolean(EXTRA_IS_GROUP_CONVERSATION, messaging.isGroupConversation());
	}

	private static Bundle[] getBundleArrayForMessages(final List<Message> messages) {
		final int N = messages.size();
		final Bundle[] bundles = new Bundle[N];
		for (int i = 0; i < N; i ++) bundles[i] = toBundle(messages.get(i));
		return bundles;
	}

	private static Bundle toBundle(final Message message) {
		final Bundle bundle = new Bundle();
		bundle.putCharSequence(KEY_TEXT, message.getText());
		// Log.d(TAG, "message.text " + message.getText());
		bundle.putLong(KEY_TIMESTAMP, message.getTimestamp());		// Must be included even for 0
		final Person sender = message.getPerson();
		if (sender != null) {
			bundle.putCharSequence(KEY_SENDER, sender.getName());	// Legacy listeners need this
			if (SDK_INT >= P) bundle.putParcelable(KEY_SENDER_PERSON, toAndroidPerson(sender));
		}
		if (message.getDataMimeType() != null) bundle.putString(KEY_DATA_MIME_TYPE, message.getDataMimeType());
		if (message.getDataUri() != null) bundle.putParcelable(KEY_DATA_URI, message.getDataUri());
		if (SDK_INT >= O && ! message.getExtras().isEmpty()) bundle.putBundle(KEY_EXTRAS_BUNDLE, message.getExtras());
		//if (message.isRemoteInputHistory()) bundle.putBoolean(KEY_REMOTE_INPUT_HISTORY, message.isRemoteInputHistory());
		return bundle;
	}

	@RequiresApi(P) @SuppressLint("RestrictedApi") private static android.app.Person toAndroidPerson(final Person user) {
		return user.toAndroidPerson();
	}

	interface Controller { void recastNotification(int id, WeChatDecorator.ModifyNotification... modifies); }

	MessagingBuilder(final Context context, final Context packageContext, /* final SharedPreferences preferences,  */final Controller controller) {
		mContext = context;
		// Try to load resources from module APK
		Context moduleContext = null;
		try {
			moduleContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
		} catch (final Exception e) {
			Log.w(TAG, "Failed to create module context: " + e.getMessage());
		}
		final Context pkgCtx = moduleContext != null ? moduleContext : context;
		if (BuildConfig.DEBUG) Log.d(TAG, "pkgCtx=" + pkgCtx + " moduleContext=" + moduleContext);
		actionReply = "回复";
		actionZoom = "缩放";
		mController = controller;
		String selfName = "我";
		if (moduleContext != null) {
			try { selfName = moduleContext.getString(R.string.self_display_name); }
			catch (android.content.res.Resources.NotFoundException ignored) {}
		}
		if (selfName == null || selfName.isEmpty()) selfName = "我";
		mUserSelf = buildPersonFromProfile(selfName);

		{
			final IntentFilter filter = new IntentFilter(ACTION_REPLY); filter.addAction(ACTION_MENTION); filter.addDataScheme(SCHEME_ID);
			ContextCompat.registerReceiver(context, mReplyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
		}
		{
			final IntentFilter filter = new IntentFilter(ACTION_SYNTHETIC_REPLY); filter.addDataScheme(SCHEME_ID);
			ContextCompat.registerReceiver(context, mSyntheticReplyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
		}
		{
			final IntentFilter filter = new IntentFilter(ACTION_ZOOM); filter.addDataScheme(SCHEME_ID);
			ContextCompat.registerReceiver(context, mZoomReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
		}
	}

	private static Person buildPersonFromProfile(final String selfDisplayName) {
		return new Person.Builder().setName(selfDisplayName).setIcon(loadSelfIcon()).build();
	}

	// M5: 头像缓存（进程内只解析一次；像素可复用，无需定时刷新）
	private static IconCompat sCachedSelfIcon = null;

	/**
	 * 动态加载自己的微信头像
	 * 1. 从 SharedPreferences 读取 wxid
	 * 2. 计算 MD5 得到头像文件路径
	 * 3. 从 /data/data/com.tencent.mm/ 加载头像
	 */
	@Nullable
	private static IconCompat loadSelfIcon() {
		if (sCachedSelfIcon != null) return sCachedSelfIcon;
		try {
			// 获取微信应用的 Context
			Context wechatContext = NevoDecoratorService.getAppContext();
			if (wechatContext == null) {
				Log.w(TAG, "loadSelfIcon: appContext is null");
				return null;
			}

			// 创建微信的 Context 来读取 SharedPreferences
			Context wechatPkgCtx = wechatContext.createPackageContext("com.tencent.mm",
					Context.CONTEXT_IGNORE_SECURITY);

			// 从 account_history 读取 wxid
			SharedPreferences accountPrefs = wechatPkgCtx.getSharedPreferences(
					"com.tencent.mm_preferences_account_history", Context.MODE_PRIVATE);
			if (accountPrefs == null) {
				Log.w(TAG, "loadSelfIcon: account prefs is null");
				return null;
			}

			// 获取所有 key，找到 wxid 开头的
			String wxid = null;
			Map<String, ?> allPrefs = accountPrefs.getAll();
			for (String key : allPrefs.keySet()) {
				if (key.startsWith("wxid_")) {
					wxid = key;
					break;
				}
			}

			if (wxid == null) {
				Log.w(TAG, "loadSelfIcon: wxid not found");
				return null;
			}

			// 计算 MD5
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] digest = md.digest(wxid.getBytes());
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			String md5 = sb.toString();

			// 构建头像路径: /data/data/com.tencent.mm/MicroMsg/{user_hash}/avatar/{md5[0:2]}/{md5[2:4]}/user_{md5}.png
			// L4: 使用动态路径
			File wechatDataDir = null;
			try { wechatDataDir = wechatPkgCtx.getFilesDir().getParentFile(); } catch (Exception ignored) {}
			if (wechatDataDir == null) wechatDataDir = new File("/data/data/com.tencent.mm");
			File microMsgDir = new File(wechatDataDir, "MicroMsg/");
			if (!microMsgDir.exists()) {
				Log.w(TAG, "loadSelfIcon: MicroMsg dir not found");
				return null;
			}

			File userHashDir = null;
			for (File dir : microMsgDir.listFiles()) {
				if (dir.isDirectory() && dir.getName().length() > 20) {
					// 检查是否有 avatar 子目录
					File avatarDir = new File(dir, "avatar");
					if (avatarDir.exists()) {
						userHashDir = dir;
						break;
					}
				}
			}

			if (userHashDir == null) {
				Log.w(TAG, "loadSelfIcon: user hash dir not found");
				return null;
			}

			// 构建完整路径
			String avatarPath = userHashDir.getAbsolutePath() + "/avatar/"
					+ md5.substring(0, 2) + "/" + md5.substring(2, 4)
					+ "/user_" + md5 + ".png";

			File avatarFile = new File(avatarPath);
			if (!avatarFile.exists()) {
				Log.w(TAG, "loadSelfIcon: avatar file not found");
				return null;
			}

			if (BuildConfig.DEBUG) Log.d(TAG, "loadSelfIcon: loading avatar");

			// 加载并缩放头像
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = 1;
			Bitmap bitmap = BitmapFactory.decodeFile(avatarPath, options);
			if (bitmap == null) {
				Log.w(TAG, "loadSelfIcon: failed to decode bitmap");
				return null;
			}

			// 缩放到 48dp
			int size = (int) (48 * wechatContext.getResources().getDisplayMetrics().density);
			Bitmap scaled = Bitmap.createScaledBitmap(bitmap, size, size, true);
			if (BuildConfig.DEBUG) Log.d(TAG, "loadSelfIcon: success, size=" + scaled.getWidth() + "x" + scaled.getHeight());
			IconCompat icon = IconCompat.createWithBitmap(scaled);
			sCachedSelfIcon = icon;
			return icon;

		} catch (Exception e) {
			Log.w(TAG, "loadSelfIcon failed: " + e.getMessage());
			return null;
		}
	}

	@Nullable private MessagingStyle buildWithSyntheticReply(final Conversation conversation, final int id, final Notification n, final CharSequence title, final List<Notification> archive) {
		final PendingIntent contentIntent = n.contentIntent;
		if (contentIntent == null) {
			if (BuildConfig.DEBUG) Log.d(TAG, "No contentIntent for synthetic reply");
			return null;
		}
		if (BuildConfig.DEBUG) Log.d(TAG, "Building synthetic reply action for notification " + id);
		final MessagingStyle messaging = new MessagingStyle(mUserSelf);

		// 用通知里已有的消息、归档通知或当前文本重建消息列表，并补上输入历史里的用户回复
		appendKnownMessages(messaging, conversation, n, archive);

		if (!MainHook.isSyntheticReplyAvailable()) {
			logReply("action_synthetic_skipped", "notificationId=" + id + " reason=wechat_receiver_unavailable");
			return messaging;
		}

		// Reply is only exposed when the target receiver was verified in this process.
		final Intent replyIntent = new Intent(ACTION_SYNTHETIC_REPLY)
				.setData(Uri.fromParts(SCHEME_ID, Integer.toString(id), null))
				.setPackage(mContext.getPackageName());
		final PendingIntent replyPendingIntent = PendingIntent.getBroadcast(mContext, id, replyIntent, pendingIntentFlags());
		final RemoteInput.Builder remoteInputBuilder = new RemoteInput.Builder("synthetic_reply_result_key")
				.setAllowFreeFormInput(true);
		if (SDK_INT >= N) remoteInputBuilder.setLabel(actionReply);
		final Action.Builder replyActionBuilder = new Action.Builder(null, actionReply, replyPendingIntent)
				.addRemoteInput(remoteInputBuilder.build())
				.setAllowGeneratedReplies(true);
		if (SDK_INT >= P) replyActionBuilder.setSemanticAction(Action.SEMANTIC_ACTION_REPLY);

		final List<Action> actions = new ArrayList<>();
		actions.add(replyActionBuilder.build());
		setActions(n, actions.toArray(new Action[0]));
		logReply("action_synthetic", "notificationId=" + id);
		return messaging;
	}

	private final BroadcastReceiver mSyntheticReplyReceiver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent proxy_intent) {
		final Uri data = proxy_intent.getData();
		final Bundle results = RemoteInput.getResultsFromIntent(proxy_intent);
		if (data == null || results == null) {
			logReply("synthetic_drop", "reason=missing_data_or_results");
			return;
		}
		final String part = data.getSchemeSpecificPart();
		final int notif_id;
		try { notif_id = Integer.parseInt(part); } catch (final NumberFormatException e) { return; }
		String reply_text = null;
		for (String key : results.keySet()) {
			final CharSequence val = results.getCharSequence(key);
			if (val != null && val.length() > 0) { reply_text = val.toString(); break; }
		}
		if (reply_text == null) {
			logReply("synthetic_drop", "notificationId=" + notif_id + " reason=missing_input keys=" + results.keySet());
			return;
		}
		logReply("synthetic_receiver", "notificationId=" + notif_id + " inputLength=" + reply_text.length());
		final boolean dispatched;
		try {
			// 直接调用 MMAutoMessageReplyReceiver.onReceive，绕过广播系统
			final Intent reply_intent = new Intent(MainHook.WECHAT_AUTO_REPLY_ACTION);
			reply_intent.setPackage("com.tencent.mm");
			reply_intent.putExtra("reply_content", reply_text);
			reply_intent.putExtra("notification_id", notif_id);
			// 设置 RemoteInput 结果
			final Bundle remoteInputResults = new Bundle();
			remoteInputResults.putCharSequence(DEFAULT_AUTO_REPLY_RESULT_KEY, reply_text);
			RemoteInput.addResultsToIntent(new RemoteInput[]{ new RemoteInput.Builder(DEFAULT_AUTO_REPLY_RESULT_KEY).build() }, reply_intent, remoteInputResults);
			dispatched = MainHook.invokeMMAutoReply(context, reply_intent);
		} catch (final RuntimeException e) {
			Log.w(TAG, "NX_REPLY stage=synthetic_dispatch_failed notificationId=" + notif_id, e);
			return;
		}
		if (!dispatched) return;
		logReply("synthetic_dispatched", "notificationId=" + notif_id);
		final String finalReplyText = reply_text;
		mController.recastNotification(notif_id, n -> {
			// 清除 pre-applied 标记，允许重新处理
			com.oasisfeng.nevo.xposed.compat.XposedHelpers.setAdditionalInstanceField(n, "pre-applied", null);
			com.oasisfeng.nevo.xposed.compat.XposedHelpers.setAdditionalInstanceField(n, "applied", null);
			final Bundle extras = n.extras;
			// 按女娲石源码逻辑添加用户回复
			Bundle[] messages;
			if (extras.getParcelableArray(EXTRA_MESSAGES) != null) {
				messages = (Bundle[]) extras.getParcelableArray(EXTRA_MESSAGES);
			} else {
				messages = new Bundle[0];
			}
			Bundle[] new_messages = new Bundle[messages.length + 1];
			System.arraycopy(messages, 0, new_messages, 0, messages.length);
			Bundle userMessage = new Bundle();
			userMessage.putCharSequence(KEY_TEXT, finalReplyText);
			userMessage.putLong(KEY_TIMESTAMP, System.currentTimeMillis());
			// 不设置 KEY_SENDER，让系统自动识别为自己的消息
			new_messages[messages.length] = userMessage;
			extras.putParcelableArray(EXTRA_MESSAGES, new_messages);
			// 添加到输入历史
			final CharSequence[] history = extras.getCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY);
			final CharSequence[] new_history;
			if (history != null) {
				new_history = new CharSequence[history.length + 1];
				System.arraycopy(history, 0, new_history, 0, history.length);
				new_history[history.length] = finalReplyText;
			} else new_history = new CharSequence[] { finalReplyText };
			extras.putCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY, new_history);
		});
	}};

	// M4: 跟踪注册状态，防止重复注销或泄漏
	private volatile boolean mClosed = false;

	void close() {
		if (mClosed) return;
		mClosed = true;
		try { mContext.unregisterReceiver(mReplyReceiver); } catch (final RuntimeException ignored) {}
		try { mContext.unregisterReceiver(mSyntheticReplyReceiver); } catch (final RuntimeException ignored) {}
		try { mContext.unregisterReceiver(mZoomReceiver); } catch (final RuntimeException ignored) {}
		mMarkReadPendingIntents.clear();
		mPendingReplies.clear();
	}

	private final Context mContext;
	private final String actionReply, actionZoom;
	// private final SharedPreferences mPreferences;
	private final Controller mController;
	private final Person mUserSelf;
	private final Map<Integer, PendingIntent> mMarkReadPendingIntents = new ArrayMap<>();
	private static final String TAG = WeChatDecorator.TAG;
}
