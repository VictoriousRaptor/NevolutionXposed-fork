package com.oasisfeng.nevo.decorators.wechat;

import android.app.PendingIntent;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.SparseArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import static java.util.Objects.requireNonNull;

/**
 * Manage all conversations.
 *
 * Created by Oasis on 2019-4-11.
 */
class ConversationManager {

	static class Conversation {

		static final int TYPE_UNKNOWN = 0;
		static final int TYPE_DIRECT_MESSAGE = 1;
		static final int TYPE_GROUP_CHAT = 2;
		static final int TYPE_BOT_MESSAGE = 3;
		static final int TYPE_DM_RECALL = 4;
		static final int TYPE_GC_RECALL = 5;
		@IntDef({ TYPE_UNKNOWN, TYPE_DIRECT_MESSAGE, TYPE_GROUP_CHAT, TYPE_BOT_MESSAGE, TYPE_DM_RECALL, TYPE_GC_RECALL }) @Retention(RetentionPolicy.SOURCE) @interface ConversationType {}

		private static final String SCHEME_ORIGINAL_NAME = "ON:";

		final int id;
		@Nullable volatile String key;
		@Nullable volatile String resolvedKey;
		ConversationTypePolicy.Decision typeEvidence;
		long resolutionGeneration;
		java.util.function.Consumer<String> onTalkerResolved = ignored -> {};
		String knownKey() { return resolvedKey != null ? resolvedKey : key; }
		CharSequence title;
		IconCompat icon;
		PendingIntent contentIntent;
		PendingIntent replyIntent;
		String notificationPeerId;

		int getType() { return mType; }

		void setType(final int type) {
			if (type == mType) return;
			mType = type;
			if (type != TYPE_GROUP_CHAT) mParticipants.clear();
		}

		static boolean isGroupChat(int mType) { return mType == TYPE_GROUP_CHAT || mType == TYPE_GC_RECALL; }
		boolean isGroupChat() { return isGroupChat(mType); }

		Person getGroupParticipant(final String key, final String name) {
			if (! isGroupChat()) throw new IllegalStateException("Not group chat");
			Person.Builder builder = null;
			Person participant = mParticipants.get(key);
			if (participant == null) builder = new Person.Builder().setKey(key);
			else if (! TextUtils.equals(name, requireNonNull(participant.getUri()).substring(SCHEME_ORIGINAL_NAME.length())))	// Original name is changed
				builder = participant.toBuilder();
			if (builder != null) {
				final CharSequence n = EmojiTranslator.translate(name);
				builder.setUri(SCHEME_ORIGINAL_NAME + name).setName(n);
				mParticipants.put(key, participant = builder.build());
			}
			return participant;
		}

		Conversation(final int id) { this.id = id; }

		@ConversationType private int mType;
		private final Map<String, Person> mParticipants = new ArrayMap<>();
	}

	synchronized Conversation getConversation(final int id) {
		Conversation conversation = mConversations.get(id);
		if (conversation == null) mConversations.put(id, conversation = new Conversation(id));
		return conversation;
	}

	synchronized void resetConversation(final int id) { mConversations.remove(id); }

	synchronized @Nullable String knownKeyForId(final int id) {
		Conversation conversation = mConversations.get(id);
		return conversation == null ? null : conversation.knownKey();
	}

	synchronized List<Integer> idsForTalker(final String talker) {
		List<Integer> ids = new ArrayList<>();
		if (talker == null || talker.isEmpty()) return ids;
		for (int i = 0; i < mConversations.size(); i++)
			if (talker.equals(mConversations.valueAt(i).knownKey())) ids.add(mConversations.keyAt(i));
		return ids;
	}

	synchronized boolean acceptTalker(Conversation expected, String key) {
		if (key == null || key.trim().isEmpty() || mConversations.get(expected.id) != expected) return false;
		String known = expected.knownKey();
		if (known != null && !known.equals(key)) return false;
		expected.resolvedKey = key;
		return true;
	}

	/** Render-local fields cannot be changed by the asynchronous identity callback. */
	synchronized Conversation snapshot(Conversation cached, android.app.Notification notification) {
		ConversationTypePolicy.Decision decision = ConversationClassification.resolve(cached, notification);
		if (cached.notificationPeerId == null) {
			cached.notificationPeerId = notification.extras.getBoolean(NotificationMessages.STORED)
					? notification.extras.getString(NotificationMessages.PEER_ID) : null;
			if (cached.notificationPeerId == null) cached.notificationPeerId = "nevo:peer:" + java.util.UUID.randomUUID();
		}
		Conversation view = new Conversation(cached.id);
		view.key = cached.knownKey();
		view.title = cached.title;
		view.icon = cached.icon;
		view.contentIntent = cached.contentIntent;
		view.notificationPeerId = cached.notificationPeerId;
		view.typeEvidence = decision;
		view.setType(decision.type);
		final long generation = ++cached.resolutionGeneration;
		view.onTalkerResolved = key -> acceptTalker(cached, generation, key);
		return view;
	}

	private synchronized void acceptTalker(Conversation expected, long generation, String key) {
		boolean accepted = expected.resolutionGeneration == generation && acceptTalker(expected, key);
		if (com.oasisfeng.nevo.xposed.BuildConfig.DEBUG) android.util.Log.d("WeChat.Identity", "source=talker_callback accepted=" + accepted);
	}

	private final SparseArray<Conversation> mConversations = new SparseArray<>();
}
