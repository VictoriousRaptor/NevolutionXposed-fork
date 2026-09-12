/*
 * Copyright (C) 2015 The Nevolution Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.File;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.graphics.drawable.IconCompat;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;
import static android.service.notification.NotificationListenerService.REASON_APP_CANCEL;
import static android.service.notification.NotificationListenerService.REASON_CANCEL;
import static android.service.notification.NotificationListenerService.REASON_CHANNEL_BANNED;

import com.oasisfeng.nevo.xposed.compat.PackageHookContext;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;
import com.oasisfeng.nevo.xposed.compat.XposedHelpers;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;
import com.oasisfeng.nevo.sdk.Decorating;
import com.oasisfeng.nevo.sdk.HookSupport;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.R;

/**
 * Bring state-of-art notification experience to WeChat.
 *
 * Created by Oasis on 2015/6/1.
 *
 * @class WeChatImageDecorator.
 * 
 */
public class WeChatDecorator extends NevoDecoratorService {
	
	public static final String WECHAT_PACKAGE = "com.tencent.mm";
	private static final long GROUP_CHAT_SORT_KEY_SHIFT = 24 * 60 * 60 * 1000L;			// Sort group chat like one day older message.
	private static final String CHANNEL_MESSAGE = "message_channel_new_id";				// Channel ID used by WeChat for all message notifications
	private static final String OLD_CHANNEL_MESSAGE = "message";						//   old name for migration
	private static final String CHANNEL_MISC = "reminder_channel_id";					// Channel ID used by WeChat for misc. notifications
	private static final String OLD_CHANNEL_MISC = "misc";								//   old name for migration
	private static final String CHANNEL_DND = "message_dnd_mode_channel_id";			// Channel ID used by WeChat for its own DND mode
	private static final String CHANNEL_GROUP_CONVERSATION = "group";					// WeChat has no separate group for group conversation
	private static final String RECALL_PATTERN = "(\"(?<recaller>[^\"]+)\" )?撤回了?一条消息";
	private static final Pattern pattern = Pattern.compile(RECALL_PATTERN);				// [2条]"🦉 " 撤回了一条消息 / [2条]撤回一条消息

	private static final @ColorInt int PRIMARY_COLOR = 0xFF33B332;
	private static final @ColorInt int LIGHT_COLOR = 0xFF00FF00;

	// 记录最近一次通话类型（从 id=41 通知获取）
	private static volatile String sLastCallType = null;  // "语音通话" 或 "视频通话"
	private static volatile long sLastCallTime = 0;
	static final String ACTION_SETTINGS_CHANGED = "SETTINGS_CHANGED";
	static final String ACTION_DEBUG_NOTIFICATION = "DEBUG";
	private static final String KEY_SILENT_REVIVAL = "nevo.wechat.revival";
	private static final String EXTRA_RECALL = "nevo.wechat.recall";
	private static final String EXTRA_RECALLER = "nevo.wechat.recaller";
	public static final String EXTRA_PICTURE_PATH = "nevo.wechat.picturePath";
	private static final String EXTRA_PICTURE = "nevo.wechat.picture";
	private static final String STORAGE_PREFIX = "/storage/emulated/0/";

	static final String TAG = "WeChatDecorator";

	public static interface ModifyNotification { void modify(Notification n); }

	private static long now() { return System.currentTimeMillis(); }

	@Override public LocalDecorator createLocalDecorator(String packageName) {
		return new Local(this.prefKey);
	}

	public static class Local extends LocalDecorator implements HookSupport {
		public Local(String prefKey) {
			super(prefKey);
		}
		
		private final WeChatImageEvents imageEvents = new WeChatImageEvents();
		private final ImagePreviewLoader imagePreviews = new ImagePreviewLoader(imageEvents);
		private static void imageLog(String stage, String details) {
			if (BuildConfig.DEBUG) Log.i(TAG, "NX_IMAGE stage=" + stage + " " + details);
		}
	
		/**
		 * 
		 * Created by Oasis on 2018-11-30.
		 * Modify by Kr328 on 2019-1-5
		 * Modify by notXX on 2019-8-5
		 * 
		 * @param loadPackageParam
		 */
		@Override public void hook(PackageHookContext loadPackageParam) {
			imageEvents.install(getAppContext(), loadPackageParam.classLoader);
		}

		private MessagingBuilder mMessagingBuilder;
		private String channelGroupMessage, channelMessage, channelMisc;
		private boolean mWeChatTargetingO;
		private final ConversationManager mConversationManager = new ConversationManager();

		@Override public void onCreate(SharedPreferences pref) {
			imageLog("process_init", "revision=image-events-1");
			super.onCreate(pref);

			mMessagingBuilder = new MessagingBuilder(getAppContext(), getPackageContext(), this::modifyNotification);		// Must be called after loadPreferences().
			channelGroupMessage = moduleString(R.string.channel_group_message, "群聊消息");
			channelMessage = moduleString(R.string.channel_message, "新消息");
			channelMisc = moduleString(R.string.channel_misc, "其他通知");
			imageLog("decorator_ready", "disabled=" + isDisabled());
		}

		private String moduleString(int resource, String fallback) {
			Context context = getPackageContext();
			if (context == null) return fallback;
			try { return context.getString(resource); }
			catch (android.content.res.Resources.NotFoundException ignored) { return fallback; }
		}

		@Override public Decorating apply(NotificationManager nm, String tag, int id, Notification n) {
			if (ImagePreviewLoader.isPreview(n)) return Decorating.Processed;
			mWeChatTargetingO = isWeChatTargeting26OrAbove();
			if (BuildConfig.DEBUG) Log.d(TAG, "apply tag " + tag + " id " + id);

			// 排除通话通知（语音通话、视频通话）- 只排除通话状态通知，不排除普通消息
			if (id == 40 || id == 41) {
				final CharSequence textCheck = n.extras.getCharSequence(Notification.EXTRA_TEXT);
				if (textCheck != null) {
					final String textStr = textCheck.toString();
					// 记录通话类型（从 id=41 的邀请通知获取）
					if (id == 41 && textStr.contains("邀请你")) {
						if (textStr.contains("视频通话")) {
							sLastCallType = "视频通话";
							sLastCallTime = System.currentTimeMillis();
							Log.d(TAG, "Detected video call invitation");
						} else if (textStr.contains("语音通话")) {
							sLastCallType = "语音通话";
							sLastCallTime = System.currentTimeMillis();
							Log.d(TAG, "Detected voice call invitation");
						}
					}
					if (textStr.contains("邀请你") || textStr.contains("通话中") ||
						textStr.contains("calling") || textStr.contains("Voice call") || textStr.contains("Video call")) {
						Log.d(TAG, "Skipping voice/video call notification: " + textStr);
						return Decorating.Unprocessed;
					}
				}
			}

			cache(id, n);

			// Log.d(TAG, "deleteIntent " + n.deleteIntent);
			final Bundle extras = n.extras,
				extensions = extras.getBundle("android.car.EXTENSIONS");
			if (extensions != null) {
				Bundle conversation = extensions.getBundle("car_conversation");
				PendingIntent onRead = (PendingIntent)conversation.get("on_read");
				// Log.d(TAG, "on_read " + onRead);
				n.deleteIntent = onRead;
			}
			// Log.d(TAG, "deleteIntent " + n.deleteIntent);
			CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
			if (title == null || title.length() == 0) {
				Log.e(TAG, "Title is missing: " + n);
				return Decorating.Unprocessed;
			}
			if (title != (title = EmojiTranslator.translate(title))) extras.putCharSequence(Notification.EXTRA_TITLE, title);
			n.color = PRIMARY_COLOR;        // Tint the small icon

			String channel_id = SDK_INT >= O ? n.getChannelId() : null;
			if (CHANNEL_MISC.equals(channel_id)) return Decorating.Unprocessed;	// Misc. notifications on Android 8+.
			
			final CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
			String content = text != null ? text.toString() : null;
			// [2条]...
			if (content != null && content.startsWith("[")) {
				if (BuildConfig.DEBUG) Log.d(TAG, "content " + content);
				final int end = content.indexOf(']');
				if (content.charAt(end - 1) == '条') {
					n.number = Integer.parseInt(content.substring(1, end - 1));
					if (BuildConfig.DEBUG) Log.d(TAG, "n.number " + n.number);
					content = content.substring(end + 1);
				}
			}
			// 修正通话类型：微信 CarExtender 总是发送 [语音通话]，需要根据 id=41 通知修正为 [视频通话]
			if (content != null && content.contains("[语音通话]") && sLastCallType != null) {
				final long timeDiff = System.currentTimeMillis() - sLastCallTime;
				if (timeDiff < 30000) {  // 30秒内的通话通知有效
					if ("视频通话".equals(sLastCallType)) {
						content = content.replace("[语音通话]", "[视频通话]");
						extras.putCharSequence(Notification.EXTRA_TEXT, content);
						Log.d(TAG, "Corrected call type to video: " + content);
					}
				}
				// H3: 使用后立即清理，避免脏数据影响后续通知
				sLastCallType = null;
				sLastCallTime = 0;
			}
			// Post the text notification immediately. Resolve only message-associated paths off the UI thread.
			if (content != null && content.endsWith("[图片]")) {
				imagePreviews.request(getAppContext(), nm, tag, id, n);
			}
			// 表情包/视频/文件等消息不做处理，保留微信原始通知内容
			if (content != null) {
				if (content.contains("[表情]") || content.contains("[动画表情]") ||
					content.contains("[视频]") || content.contains("[文件]") || content.contains("[链接]") ||
					content.contains("[音乐]") || content.contains("[位置]") || content.contains("[红包]") ||
					content.contains("[转账]") || content.contains("[小程序]")) {
					Log.d(TAG, "Skipping media/sticker message, keeping original: " + content);
					return Decorating.Unprocessed;
				}
				// 转换微信内置表情标记为 Emoji（如 [得意] -> 😎）
				if (content.startsWith("[") && content.endsWith("]")) {
					CharSequence translated = EmojiTranslator.translate(content);
					if (!translated.equals(content)) {
						extras.putCharSequence(Notification.EXTRA_TEXT, translated);
						Log.d(TAG, "Translated emoji: " + content + " -> " + translated);
					}
				}
			}
			// 撤回...
			int type = Conversation.TYPE_UNKNOWN;
			String recaller = null;
			boolean is_recall = false;
			if (content != null && content.contains("撤回")) {
				if (BuildConfig.DEBUG) Log.d(TAG, "content " + content);
				if (CHANNEL_MISC.equals(channel_id)) {	// Misc. notifications on Android 8+.
					return Decorating.Unprocessed;
				} else if (n.tickerText == null) {		// Legacy misc. notifications.
					if (SDK_INT >= O && channel_id == null) setChannelId(n, CHANNEL_MISC);
					Matcher matcher = pattern.matcher(content);
					if (BuildConfig.DEBUG) Log.d(TAG, "matcher " + matcher.matches());
					if (matcher.matches()) {
						// 撤回
						// Log.d(TAG, matcher.group(0) + ", " + matcher.group(1) + ", " + matcher.group(2) + ", " + matcher.group(3));
						is_recall = true;
						recaller = matcher.group("recaller");
						extras.putBoolean(EXTRA_RECALL, true);
						extras.putString(EXTRA_RECALLER, recaller);
						if (BuildConfig.DEBUG) Log.d(TAG, "recaller " + recaller);
					} else {
						Log.d(TAG, "Skip further process for non-conversation notification: " + title);    // E.g. web login confirmation notification.
						return Decorating.Unprocessed;
					}
				}
				if (is_recall) type = (recaller == null) ? Conversation.TYPE_DM_RECALL : Conversation.TYPE_GC_RECALL;
			}
			if (content == null || content.isEmpty()) return Decorating.Unprocessed;

			extras.putCharSequence(Notification.EXTRA_TEXT, content);

			if (type == Conversation.TYPE_UNKNOWN) type = WeChatMessage.guessConversationType(content, n.tickerText != null ? n.tickerText != null ? n.tickerText.toString().trim() : "" : "", title);
			final boolean is_group_chat = Conversation.isGroupChat(type);
			if (SDK_INT >= O) {
				if (extras.containsKey(KEY_SILENT_REVIVAL)) {
					setGroup(n, "nevo.group.auto");	// Special group name to let Nevolution auto-group it as if not yet grouped. (To be standardized in SDK)
					setGroupAlertBehavior(n, Notification.GROUP_ALERT_SUMMARY);		// This trick makes notification silent
				}
				if (is_group_chat && ! CHANNEL_DND.equals(channel_id)) setChannelId(n, CHANNEL_GROUP_CONVERSATION);
				else if (channel_id == null) setChannelId(n, CHANNEL_MESSAGE);		// WeChat versions targeting O+ have its own channel for message
			}

			// WeChat previously uses dynamic counter starting from 4097 as notification ID, which is reused after cancelled by WeChat itself,
			//   causing conversation duplicate or overwritten notifications.
			final Conversation conversation = mConversationManager.getConversation(id);

			final Icon icon = n.getLargeIcon();
			conversation.icon = icon != null ? IconCompat.createFromIcon(getAppContext(), icon) : null;
			conversation.title = title;
			conversation.summary = content;
			conversation.ticker = n.tickerText;
			conversation.timestamp = n.when;
			if (is_recall)
				conversation.setType((recaller == null) ? Conversation.TYPE_DM_RECALL : Conversation.TYPE_GC_RECALL);
			else if (conversation.getType() == Conversation.TYPE_UNKNOWN)
				conversation.setType(WeChatMessage.guessConversationType(conversation));

			extras.putBoolean(Notification.EXTRA_SHOW_WHEN, true);
			// if (mPreferences.getBoolean(mPrefKeyWear, false)) n.flags &= ~ Notification.FLAG_LOCAL_ONLY; // TODO
			setSortKey(n, String.valueOf(Long.MAX_VALUE - n.when + (is_group_chat ? GROUP_CHAT_SORT_KEY_SHIFT : 0))); // Place group chat below other messages

			if (getAppContext() == null) {
				Log.w(TAG, "Application context is not ready; skipping notification");
				return Decorating.Unprocessed;
			}
			if (mMessagingBuilder == null) {
				try {
					Log.d(TAG, "mMessagingBuilder is null, initializing...");
					mMessagingBuilder = new MessagingBuilder(getAppContext(), getPackageContext(), this::modifyNotification);
				} catch (final Exception e) {
					Log.w(TAG, "Failed to init mMessagingBuilder: " + e.getMessage());
					return Decorating.Unprocessed;
				}
			}
			Log.d(TAG, "Calling buildFromExtender...");
			MessagingStyle messaging = mMessagingBuilder.buildFromExtender(conversation, id, n, title, getArchivedNotifications(id));
			Log.d(TAG, "buildFromExtender returned: " + (messaging != null ? "non-null" : "null")); // build message from android auto
			if (messaging == null)	// EXTRA_TEXT will be written in buildFromArchive()
				messaging = mMessagingBuilder.buildFromArchive(conversation, n, title, getArchivedNotifications(id));
			if (messaging == null) return Decorating.Unprocessed;
			final List<MessagingStyle.Message> messages = messaging.getMessages();
			if (messages.isEmpty()) return Decorating.Unprocessed;

			if (is_group_chat) messaging.setGroupConversation(true).setConversationTitle(title);
			MessagingBuilder.flatIntoExtras(messaging, extras);
			extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_MESSAGING);

			if (SDK_INT >= N && extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) != null)
				n.flags |= Notification.FLAG_ONLY_ALERT_ONCE;		// No more alert for direct-replied notification.

			// 维护NotificationChannel
			channel_id = n.getChannelId();
			if (channel_id != null) { // 确保NotificationChannel存在
				NotificationChannel channel = nm.getNotificationChannel(channel_id);
				if (BuildConfig.DEBUG) Log.d(TAG, channel_id + " " + channel);
				if (channel != null) return Decorating.Processed;
				switch (channel_id) {
					case CHANNEL_GROUP_CONVERSATION:
					channel = makeChannel(CHANNEL_GROUP_CONVERSATION, channelGroupMessage, false);
					break;
					case CHANNEL_MESSAGE:
					channel = migrate(nm, OLD_CHANNEL_MESSAGE,	CHANNEL_MESSAGE,	channelMessage, false);
					break;
					case CHANNEL_MISC:
					channel = migrate(nm, OLD_CHANNEL_MISC,		CHANNEL_MISC,		channelMisc, true);
					break;
				}
				if (BuildConfig.DEBUG) Log.d(TAG, channel_id + " " + channel);
				if (channel != null) {
					try {
						nm.createNotificationChannel(channel);
					} catch (Exception e) {
						Log.w(TAG, "Failed to createNotificationChannel: " + channel_id + " " + e.getMessage());
					}
				}
				channel = nm.getNotificationChannel(channel_id);
				if (BuildConfig.DEBUG) Log.d(TAG, channel_id + " " + channel);
			}

			VoiceCall.tweakIfNeeded(getAppContext(), n);
			return Decorating.Processed;
		}

		private void reviveNotificationAfterChannelDeletion(final int id) {
			Log.d(TAG, ("Revive silently: ") + id);
			modifyNotification(id, n -> {
				n.extras.putBoolean(KEY_SILENT_REVIVAL, true);
			});
		}

		@RequiresApi(O) private NotificationChannel migrate(NotificationManager nm, final String old_id, final String new_id, final String new_name, final boolean silent) {
			final NotificationChannel channel_message = nm.getNotificationChannel(old_id);
			nm.deleteNotificationChannel(old_id);
			if (channel_message != null) return cloneChannel(channel_message, new_id, new_name);
			else return makeChannel(new_id, new_name, silent);
		}

		@RequiresApi(O) private NotificationChannel makeChannel(final String channel_id, final String name, final boolean silent) {
			final NotificationChannel channel = new NotificationChannel(channel_id, name, NotificationManager.IMPORTANCE_HIGH/* Allow heads-up (by default) */);
			if (silent) channel.setSound(null, null);
			else channel.setSound(getDefaultSound(), new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
					.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT).build());
			channel.enableLights(true);
			channel.setLightColor(LIGHT_COLOR);
			return channel;
		}

		@RequiresApi(O) private NotificationChannel cloneChannel(final NotificationChannel channel, final String id, final String new_name) {
			final NotificationChannel clone = new NotificationChannel(id, new_name, channel.getImportance());
			clone.setGroup(channel.getGroup());
			clone.setDescription(channel.getDescription());
			clone.setLockscreenVisibility(channel.getLockscreenVisibility());
			clone.setSound(Optional.ofNullable(channel.getSound()).orElse(getDefaultSound()), channel.getAudioAttributes());
			clone.setBypassDnd(channel.canBypassDnd());
			clone.setLightColor(channel.getLightColor());
			clone.setShowBadge(channel.canShowBadge());
			clone.setVibrationPattern(channel.getVibrationPattern());
			return clone;
		}

		@Nullable private Uri getDefaultSound() {	// Before targeting O, WeChat actually plays sound by itself (not via Notification).
			return mWeChatTargetingO ? Settings.System.DEFAULT_NOTIFICATION_URI : null;
		}

		private boolean isWeChatTargeting26OrAbove() {
			try {
				return getPackageManager().getApplicationInfo(WECHAT_PACKAGE, PackageManager.GET_UNINSTALLED_PACKAGES).targetSdkVersion >= O;
			} catch (final PackageManager.NameNotFoundException e) {
				return false;
			}
		}

		private void modifyNotification(final int id, final ModifyNotification... modifies) {
			if (hasArchivedNotifications(id)) {
				Notification n = getArchivedNotification(id);
				for (ModifyNotification modify : modifies) modify.modify(n);
				Log.d(TAG, "recast " + id + " " + n.extras.getCharSequence(Notification.EXTRA_TITLE));
				recastNotification(id, n);
			} else {
				Log.d(TAG, "can not recast " + id + ", so cancel it");
				cancelNotification(id);
			}
		}

	}
}
