package com.oasisfeng.nevo.decorators.wechat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Notification.Action;
import android.app.Notification.CarExtender;
import android.app.NotificationManager;
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
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Profile;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LongSparseArray;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;
import com.oasisfeng.nevo.xposed.MainHook;

import java.util.ArrayList;
import java.util.HashSet;
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
import static android.os.Build.VERSION_CODES.TIRAMISU;
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
			Log.d(TAG, "No history");
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
				Log.d(TAG, "Skip other conversation with the same key in archive: " + its_title);	// ID reset by WeChat due to notification removal in previous evolving
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
		for (final Message message : messages) messaging.addMessage(message);

		final PendingIntent on_read = convs.getReadPendingIntent();
		if (on_read != null) mMarkReadPendingIntents.put(id, on_read);	// Mapped by evolved key,

		final List<Action> actions = new ArrayList<>();
		// 回复
		final RemoteInput remote_input;
		if (SDK_INT >= N && on_reply != null && (remote_input = convs.getRemoteInput()) != null) {
			final CharSequence[] input_history = n.extras.getCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY);
			final PendingIntent proxy = proxyDirectReply(id, n, on_reply, remote_input, input_history, null);
			final RemoteInput.Builder reply_remote_input = new RemoteInput.Builder(remote_input.getResultKey()).addExtras(remote_input.getExtras())
					.setAllowFreeFormInput(true);
			final String participant = convs.getParticipant();	// No need to getParticipants() due to actually only one participant at most, see CarExtender.Builder().
			if (participant != null) reply_remote_input.setLabel(participant);

			final Action.Builder reply_action = new Action.Builder(null, actionReply, proxy)
					.addRemoteInput(reply_remote_input.build()).setAllowGeneratedReplies(true);
			if (SDK_INT >= P) reply_action.setSemanticAction(Action.SEMANTIC_ACTION_REPLY);
			actions.add(reply_action.build());
		}
		// 放大
		if (n.extras.containsKey(WeChatDecorator.EXTRA_PICTURE_PATH)) {
			final Intent intent = new Intent(ACTION_ZOOM).setData(Uri.fromParts(SCHEME_ID, Integer.toString(id), null));
			final Action.Builder zoom_action = new Action.Builder(null, actionZoom, PendingIntent.getBroadcast(mContext, 0, intent.setPackage(mContext.getPackageName()), pendingIntentFlags()));
			actions.add(zoom_action.build());
		}
		// 从 EXTRA_REMOTE_INPUT_HISTORY 补充用户回复
		final CharSequence[] carInputHistory = n.extras.getCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY);
		if (carInputHistory != null && carInputHistory.length > 0) {
			for (final CharSequence reply : carInputHistory) {
				if (reply != null && reply.length() > 0) {
					messaging.addMessage(new Message(reply, System.currentTimeMillis(), (Person) null));
				}
			}
		}

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
			Log.d(TAG, "Failed to parse: " + text);
			return - 1;
		}
	}

	/** @return appropriate PendingIntent flags, including FLAG_MUTABLE on Android 12+ for RemoteInput. */
	private static int pendingIntentFlags() {
		return SDK_INT >= S ? (FLAG_UPDATE_CURRENT | FLAG_MUTABLE) : FLAG_UPDATE_CURRENT;
	}

	/** Build reply action directly from notification actions when CarExtender is absent (new WeChat versions). */
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
			Log.d(TAG, "No reply action found in notification actions");
			return null;
		}

		Log.d(TAG, "Found reply action via notification actions fallback");

		final PendingIntent onRead = n.deleteIntent;
		if (onRead != null) mMarkReadPendingIntents.put(id, onRead);

		final MessagingStyle messaging = new MessagingStyle(mUserSelf);

		// 检查通知是否已有 EXTRA_MESSAGES（用户回复后重建时）
		final Bundle[] existingMessages = n.extras.getParcelableArray(EXTRA_MESSAGES) != null ?
			(Bundle[]) n.extras.getParcelableArray(EXTRA_MESSAGES) : null;

		if (existingMessages != null && existingMessages.length > 0) {
			// 使用已有的消息（包括用户回复）
			for (final Bundle msgBundle : existingMessages) {
				final CharSequence text = msgBundle.getCharSequence(KEY_TEXT);
				final long timestamp = msgBundle.getLong(KEY_TIMESTAMP, 0);
				final CharSequence sender = msgBundle.getCharSequence(KEY_SENDER);
				if (text != null) {
					final Person person;
					if (sender != null && sender.length() == 0) {
						person = null;  // 自己发的消息（KEY_SENDER 为空字符串）
					} else if (sender != null && sender.length() > 0) {
						// 有明确的发送者名称
						if (conversation.isGroupChat()) {
							person = conversation.getGroupParticipant(sender.toString(), sender.toString());
						} else {
							person = new Person.Builder().setName(sender.toString()).build();
						}
					} else {
						// KEY_SENDER 为 null，判断是否是朋友发的
						person = new Person.Builder().setName(conversation.title != null ? conversation.title.toString() : " ").build();
					}
					messaging.addMessage(new Message(text, timestamp, person));
				}
			}
		} else if (archive != null && ! archive.isEmpty()) {
			for (final Notification archived : archive) {
				final CharSequence text = archived.extras.getCharSequence(Notification.EXTRA_TEXT);
				if (text != null) {
					final String sender = extractSenderFromText(text);
					final CharSequence msgText = sender != null ? text.subSequence(sender.length() + SENDER_MESSAGE_SEPARATOR.length(), text.length()) : text;
					// 始终用 conversation.title 作为发送者（朋友名字），不返回 null
				final Person person = new Person.Builder().setName(conversation.title != null ? conversation.title.toString() : " ").build();
					messaging.addMessage(new Message(msgText, archived.when, person));
				}
			}
		} else {
			final CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
			if (text != null) {
				final String sender = extractSenderFromText(text);
				final CharSequence msgText = sender != null ? text.subSequence(sender.length() + SENDER_MESSAGE_SEPARATOR.length(), text.length()) : text;
				// 始终用 conversation.title 作为发送者（朋友名字），不返回 null
				final Person person = new Person.Builder().setName(conversation.title != null ? conversation.title.toString() : " ").build();
				messaging.addMessage(new Message(msgText, n.when, person));
			}
		}

		final List<Action> newActions = new ArrayList<>();
		final CharSequence[] input_history = n.extras.getCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY);
		final PendingIntent proxy = proxyDirectReply(id, n, onReply, replyRemoteInput, input_history, null);
		final RemoteInput.Builder reply_remote_input = new RemoteInput.Builder(replyRemoteInput.getResultKey())
				.addExtras(replyRemoteInput.getExtras()).setAllowFreeFormInput(true);
		final Action.Builder reply_action_builder = new Action.Builder(null, actionReply, proxy)
				.addRemoteInput(reply_remote_input.build()).setAllowGeneratedReplies(true);
		if (SDK_INT >= P) reply_action_builder.setSemanticAction(Action.SEMANTIC_ACTION_REPLY);
		newActions.add(reply_action_builder.build());

		if (n.extras.containsKey(WeChatDecorator.EXTRA_PICTURE_PATH)) {
			final Intent intent = new Intent(ACTION_ZOOM).setData(Uri.fromParts(SCHEME_ID, Integer.toString(id), null));
			final Action.Builder zoom_action = new Action.Builder(null, actionZoom,
					PendingIntent.getBroadcast(mContext, 0, intent.setPackage(mContext.getPackageName()), pendingIntentFlags()));
			newActions.add(zoom_action.build());
		}
		// 从 EXTRA_REMOTE_INPUT_HISTORY 获取用户回复并添加到 MessagingStyle
		if (input_history != null && input_history.length > 0) {
			for (final CharSequence reply : input_history) {
				if (reply != null && reply.length() > 0) {
					messaging.addMessage(new Message(reply, System.currentTimeMillis(), (Person) null));  // null person = 自己发的
				}
			}
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
		final CharSequence input = results != null ? results.getCharSequence(result_key) : null;
		if (data == null || reply_action == null || result_key == null || input == null) return;	// Should never happen
		// 防止无限循环
		final String replyKey = data.getSchemeSpecificPart() + ":" + input.hashCode();
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

			reply_action.send(mContext, 0, input_data, (pendingIntent, intent, _result_code, _result_data, _result_extras) -> {
				if (BuildConfig.DEBUG) Log.d(TAG, "Reply sent: " + intent.toUri(0));
				if (SDK_INT >= N) {
					final CharSequence[] inputs;
					if (input_history != null) {
						input_history.add(0, text);
						inputs = input_history.toArray(new CharSequence[0]);
					} else inputs = new CharSequence[] { text };
					final int id = Integer.parseInt(part);
					mController.recastNotification(id, n -> {
						// 清除 pre-applied 标记，允许重新处理
						de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField(n, "pre-applied", null);
						de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField(n, "applied", null);
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
			Log.w(TAG, "Reply action is already cancelled: " + part);
			abortBroadcast();
		} finally {
			// 延迟清除防循环标志
			final String finalReplyKey = replyKey;
			mContext.getMainExecutor().execute(() -> mPendingReplies.remove(finalReplyKey));
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
		Log.d(TAG, "pkgCtx=" + pkgCtx + " moduleContext=" + moduleContext);
		actionReply = "回复";
		actionZoom = "缩放";
		mController = controller;
		String selfName = pkgCtx.getString(R.string.self_display_name);
		if (selfName == null || selfName.isEmpty()) selfName = "我";
		mUserSelf = buildPersonFromProfile(selfName);

		{
			final IntentFilter filter = new IntentFilter(ACTION_REPLY); filter.addAction(ACTION_MENTION); filter.addDataScheme(SCHEME_ID);
			if (SDK_INT >= TIRAMISU) context.registerReceiver(mReplyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
			else context.registerReceiver(mReplyReceiver, filter);
		}
		{
			final IntentFilter filter = new IntentFilter(ACTION_SYNTHETIC_REPLY); filter.addDataScheme(SCHEME_ID);
			if (SDK_INT >= TIRAMISU) context.registerReceiver(mSyntheticReplyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
			else context.registerReceiver(mSyntheticReplyReceiver, filter);
		}
		{
			final IntentFilter filter = new IntentFilter(ACTION_ZOOM); filter.addDataScheme(SCHEME_ID);
			if (SDK_INT >= TIRAMISU) context.registerReceiver(mZoomReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
			else context.registerReceiver(mZoomReceiver, filter);
		}
	}

	private static Person buildPersonFromProfile(final String selfDisplayName) {
		return new Person.Builder().setName(selfDisplayName).build();
	}

	@Nullable private MessagingStyle buildWithSyntheticReply(final Conversation conversation, final int id, final Notification n, final CharSequence title, final List<Notification> archive) {
		final PendingIntent contentIntent = n.contentIntent;
		if (contentIntent == null) {
			Log.d(TAG, "No contentIntent for synthetic reply");
			return null;
		}
		Log.d(TAG, "Building synthetic reply action for notification " + id);
		final MessagingStyle messaging = new MessagingStyle(mUserSelf);

		// 检查通知是否已有 EXTRA_MESSAGES（用户回复后重建时）
		final Bundle[] existingMessages = n.extras.getParcelableArray(EXTRA_MESSAGES) != null ?
			(Bundle[]) n.extras.getParcelableArray(EXTRA_MESSAGES) : null;

		if (existingMessages != null && existingMessages.length > 0) {
			// 使用已有的消息（包括用户回复）
			for (final Bundle msgBundle : existingMessages) {
				final CharSequence text = msgBundle.getCharSequence(KEY_TEXT);
				final long timestamp = msgBundle.getLong(KEY_TIMESTAMP, 0);
				final CharSequence sender = msgBundle.getCharSequence(KEY_SENDER);
				if (text != null) {
					final Person person;
					if (sender != null && sender.length() == 0) {
						person = null;  // 自己发的消息（KEY_SENDER 为空字符串）
					} else if (sender != null && sender.length() > 0) {
						// 有明确的发送者名称
						if (conversation.isGroupChat()) {
							person = conversation.getGroupParticipant(sender.toString(), sender.toString());
						} else {
							person = new Person.Builder().setName(sender.toString()).build();
						}
					} else {
						// KEY_SENDER 为 null，判断是否是朋友发的
						person = new Person.Builder().setName(conversation.title != null ? conversation.title.toString() : " ").build();
					}
					messaging.addMessage(new Message(text, timestamp, person));
				}
			}
		} else if (archive != null && ! archive.isEmpty()) {
			for (final Notification archived : archive) {
				final CharSequence text = archived.extras.getCharSequence(Notification.EXTRA_TEXT);
				if (text != null) {
					final String sender = extractSenderFromText(text);
					final CharSequence msgText = sender != null ? text.subSequence(sender.length() + SENDER_MESSAGE_SEPARATOR.length(), text.length()) : text;
					// 始终用 conversation.title 作为发送者（朋友名字），不返回 null
				final Person person = new Person.Builder().setName(conversation.title != null ? conversation.title.toString() : " ").build();
					messaging.addMessage(new Message(msgText, archived.when, person));
				}
			}
		} else {
			final CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
			if (text != null) {
				final String sender = extractSenderFromText(text);
				final CharSequence msgText = sender != null ? text.subSequence(sender.length() + SENDER_MESSAGE_SEPARATOR.length(), text.length()) : text;
				// 始终用 conversation.title 作为发送者（朋友名字），不返回 null
				final Person person = new Person.Builder().setName(conversation.title != null ? conversation.title.toString() : " ").build();
				messaging.addMessage(new Message(msgText, n.when, person));
			}
		}
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
		// 从 EXTRA_REMOTE_INPUT_HISTORY 获取用户回复并添加到 MessagingStyle
		final CharSequence[] input_history = n.extras.getCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY);
		if (input_history != null && input_history.length > 0) {
			for (final CharSequence reply : input_history) {
				if (reply != null && reply.length() > 0) {
					messaging.addMessage(new Message(reply, System.currentTimeMillis(), (Person) null));  // null person = 自己发的
				}
			}
		}

		final List<Action> actions = new ArrayList<>();
		actions.add(replyActionBuilder.build());
		setActions(n, actions.toArray(new Action[0]));
		Log.d(TAG, "Synthetic reply action added with " + actions.size() + " actions");
		return messaging;
	}

	private final BroadcastReceiver mSyntheticReplyReceiver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent proxy_intent) {
		final Uri data = proxy_intent.getData();
		final Bundle results = RemoteInput.getResultsFromIntent(proxy_intent);
		if (data == null || results == null) return;
		final String part = data.getSchemeSpecificPart();
		final int notif_id;
		try { notif_id = Integer.parseInt(part); } catch (final NumberFormatException e) { return; }
		String reply_text = null;
		for (String key : results.keySet()) {
			final CharSequence val = results.getCharSequence(key);
			if (val != null && val.length() > 0) { reply_text = val.toString(); break; }
		}
		if (reply_text == null) return;
		Log.d(TAG, "Synthetic reply: " + reply_text + " for id=" + notif_id);
		try {
			// 直接调用 MMAutoMessageReplyReceiver.onReceive，绕过广播系统
			final Intent reply_intent = new Intent("com.tencent.mm.permission.MM_AUTO_REPLY_MESSAGE");
			reply_intent.setPackage("com.tencent.mm");
			reply_intent.putExtra("reply_content", reply_text);
			reply_intent.putExtra("notification_id", notif_id);
			// 设置 RemoteInput 结果
			final Bundle remoteInputResults = new Bundle();
			remoteInputResults.putCharSequence("key_voice_reply_text", reply_text);
			RemoteInput.addResultsToIntent(new RemoteInput[]{ new RemoteInput.Builder("key_voice_reply_text").build() }, reply_intent, remoteInputResults);
			Log.d(TAG, "Directly invoking MMAutoMessageReplyReceiver with reply: " + reply_text);
			MainHook.invokeMMAutoReply(context, reply_intent);
		} catch (final Exception e) {
			Log.w(TAG, "Auto-reply API failed: " + e.getMessage());
		}
		final String finalReplyText = reply_text;
		mController.recastNotification(notif_id, n -> {
			// 清除 pre-applied 标记，允许重新处理
			de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField(n, "pre-applied", null);
			de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField(n, "applied", null);
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

	void close() {
		try { mContext.unregisterReceiver(mReplyReceiver); } catch (final RuntimeException ignored) {}
		try { mContext.unregisterReceiver(mSyntheticReplyReceiver); } catch (final RuntimeException ignored) {}
		try { mContext.unregisterReceiver(mZoomReceiver); } catch (final RuntimeException ignored) {}
	}

	private final Context mContext;
	private final String actionReply, actionZoom;
	// private final SharedPreferences mPreferences;
	private final Controller mController;
	private final Person mUserSelf;
	private final Map<Integer/* notification id */, PendingIntent> mMarkReadPendingIntents = new ArrayMap<>();
	private static final String TAG = WeChatDecorator.TAG;
}
