package com.oasisfeng.nevo.sdk;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Keep;

import java.util.List;
import java.lang.ref.WeakReference;
import java.util.function.Predicate;

import com.oasisfeng.nevo.xposed.compat.XposedHelpers;

import com.oasisfeng.nevo.xposed.BuildConfig;

public abstract class NevoDecoratorService {
	private static final int MAX_NUM_ARCHIVED = 20;

	public static final String EXTRAS_BIG_CONTENT_VIEW_OVERRIDE = "nevo.bigContentView";
	public static final String EXTRAS_CONTENT_VIEW_OVERRIDE = "nevo.contentView";
	/** Valid constant values for {@link android.app.Notification#EXTRA_TEMPLATE} */
	public static final String TEMPLATE_BIG_TEXT	= "android.app.Notification$BigTextStyle";
	public static final String TEMPLATE_BIG_PICTURE	= "android.app.Notification$BigPictureStyle";
	public static final String TEMPLATE_CUSTOM		= "android.app.Notification$DecoratedCustomViewStyle";
	public static final String TEMPLATE_INBOX		= "android.app.Notification$InboxStyle";
	public static final String TEMPLATE_MEDIA		= "android.app.Notification$MediaStyle";
	public static final String TEMPLATE_MESSAGING	= "android.app.Notification$MessagingStyle";

	private static final String TAG = "NevoDecoratorService";

	// Application/package contexts are process-scoped and never point to an Activity.
	@SuppressLint("StaticFieldLeak")
	private static volatile Context appContext, packageContext;

	public static Context getAppContext() {
		return appContext;
	}

	public static void setAppContext(Context context) {
		Context application = context == null ? null : context.getApplicationContext();
		appContext = application != null ? application : context;
		packageContext = null;
	}

	protected static Context getPackageContext() {
		if (packageContext == null)
			packageContext = getPackageContext(BuildConfig.APPLICATION_ID);
		return packageContext;
	}

	protected static Context getPackageContext(String packageName) {
		try {
			return getAppContext().createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
		} catch (PackageManager.NameNotFoundException ig) { return null; }
	}

	protected static PackageManager getPackageManager() {
		return getAppContext().getPackageManager();
	}

	protected static String getString(int key) {
		return getPackageContext().getString(key);
	}

	/**
	 * 在应用进程中执行的通知预处理，某些功能（NotificationChannel等）在此实现。
	 */
	public static class LocalDecorator {
		private static volatile NotificationManager mNM;

		public static void setNM(NotificationManager nm) {
			mNM = nm;
		}

		// M2: 按通知条数计费，避免每个会话都保留 MAX_NUM_ARCHIVED 条而总量失控
		private static final int MAX_CACHED_NOTIFICATIONS = 120;
		private static final NotificationArchive<Integer, Notification> cache =
				new NotificationArchive<>(MAX_CACHED_NOTIFICATIONS, MAX_NUM_ARCHIVED);
	
		protected static void cache(final int id, final Notification n) {
			if (BuildConfig.DEBUG) Log.d(TAG, "cache id " + id);
			cache.add(id, n);
		}
	
		protected static List<Notification> getArchivedNotifications(int key) {
			return cache.snapshot(key);
		}
	
		protected static Notification getArchivedNotification(int key) {
			return cache.latest(key);
		}
	
		protected static boolean hasArchivedNotifications(int key) {
			return cache.latest(key) != null;
		}

		protected static void clearArchivedNotifications(int key) { cache.remove(key); }

		protected static boolean clearArchivedNotificationsIf(int key, Predicate<Notification> matches) {
			return cache.removeIfLatest(key, matches);
		}

		private static final String KEY_ACTIONS_SERIALIZED = "nevo.actionsSerialized";

		/** Marks a notification whose RemoteInput actions are already parceled by a full rebuild. */
		public static void markActionsSerialized(final Notification n) {
			XposedHelpers.setAdditionalInstanceField(n, KEY_ACTIONS_SERIALIZED, Boolean.TRUE);
		}

		/** True when the notification came out of {@code Notification.Builder.recoverBuilder()} already. */
		public static boolean hasSerializedActions(final Notification n) {
			return XposedHelpers.getAdditionalInstanceField(n, KEY_ACTIONS_SERIALIZED) != null;
		}

		/**
		 * Swaps the most recently cached notification of a conversation for its rebuilt copy, so later
		 * recasts reuse the notification whose actions are already properly serialized.
		 */
		public static void replaceCachedNotification(final int id, final Notification original, final Notification replacement) {
			cache.replace(id, original, replacement);
		}
	
		public static RemoteViews overrideBigContentView(Notification n, RemoteViews remoteViews) {
			n.extras.putParcelable(EXTRAS_BIG_CONTENT_VIEW_OVERRIDE, remoteViews);
			return remoteViews;
		}
	
		public static RemoteViews overridedBigContentView(Notification n) {
			return n.extras.getParcelable(EXTRAS_BIG_CONTENT_VIEW_OVERRIDE);
		}
	
		public static RemoteViews overrideContentView(Notification n, RemoteViews remoteViews) {
			n.extras.putParcelable(EXTRAS_CONTENT_VIEW_OVERRIDE, remoteViews);
			return remoteViews;
		}
	
		public static RemoteViews overridedContentView(Notification n) {
			return (RemoteViews)n.extras.getParcelable(EXTRAS_CONTENT_VIEW_OVERRIDE);
		}
	
		public static void setChannelId(Notification n, String channelId) {
			XposedHelpers.setObjectField(n, "mChannelId", channelId);
		}
	
		public static void setGroup(Notification n, String groupKey) {
			XposedHelpers.setObjectField(n, "mGroupKey", groupKey);
		}
	
		public static void setGroupAlertBehavior(Notification n, int behavior) {
			XposedHelpers.setIntField(n, "mGroupAlertBehavior", behavior);
		}
	
		public static void setSortKey(Notification n, String sortKey) {
			XposedHelpers.setObjectField(n, "mSortKey", sortKey);
		}
	
		private static final java.lang.reflect.Field ACTIONS_FIELD = notificationActionsField();

		private static java.lang.reflect.Field notificationActionsField() {
			try {
				final java.lang.reflect.Field field = Notification.class.getDeclaredField("actions");
				field.setAccessible(true);
				return field;
			} catch (final NoSuchFieldException e) {
				return null;
			}
		}

		public static void setActions(Notification n, Action... actions) {
			final java.lang.reflect.Field field = ACTIONS_FIELD;
			if (field != null) {
				try {
					field.set(n, actions);
					return;
				} catch (final Exception ignored) {}
			}
			XposedHelpers.setObjectField(n, "actions", actions);
		}
	
		protected final String prefKey;
		private boolean disabled;

		protected LocalDecorator(String prefKey) { this.prefKey = prefKey; }

		public boolean isDisabled() { return disabled; }
		public void setDisabled(boolean disabled) { this.disabled = disabled; }
	
		@Keep public void onCreate(SharedPreferences pref) {
			this.disabled = !pref.getBoolean(prefKey  + ".enabled", true);
			if (BuildConfig.DEBUG) Log.d(TAG, prefKey + ".disabled " + this.disabled);
		}
	
		@Keep public void onDestroy() {}
	
		@Keep public Decorating apply(NotificationManager nm, String tag, int id, Notification n) {
			return Decorating.Unprocessed;
		}
	
		protected final void cancelNotification(int id) {
			if (BuildConfig.DEBUG) Log.d(TAG, "cancelNotification " + mNM + " " + id);
			if (mNM != null) mNM.cancel(null, id);
		}
	
		protected final void recastNotification(final int id, final Notification n) {
			if (BuildConfig.DEBUG) Log.d(TAG, "recastNotification " + mNM + " " + n.extras.getCharSequence(Notification.EXTRA_TITLE));
			if (mNM != null) mNM.notify(null, id, n);
		}
	}

	/**
	 * 在系统UI（SystemUI）中执行的通知处理。
	 */
	public static class SystemUIDecorator {
		private static volatile WeakReference<NotificationListenerService> mNLS = new WeakReference<>(null);

		public static void setNLS(NotificationListenerService nls) {
			mNLS = new WeakReference<>(nls);
		}

		public static NotificationListenerService getNLS() {
			return mNLS.get();
		}

		// M2: 同样按条数计费，避免缓存总量随会话数线性增长
		private static final int MAX_CACHED_SBN = 120;
		private static final NotificationArchive<String, StatusBarNotification> cache =
				new NotificationArchive<>(MAX_CACHED_SBN, MAX_NUM_ARCHIVED);
	
		protected static void cache(StatusBarNotification sbn) {
			cache.add(sbn.getKey(), sbn);
		}
	
		protected static List<StatusBarNotification> getArchivedNotifications(String key) {
			return cache.snapshot(key);
		}
	
		protected static StatusBarNotification getArchivedNotification(String key) {
			return cache.latest(key);
		}
	
		protected static boolean hasArchivedNotifications(String key) {
			return cache.latest(key) != null;
		}
	
		protected final String prefKey;
		private boolean disabled;

		protected SystemUIDecorator(String prefKey) { this.prefKey = prefKey; }

		public boolean isDisabled() { return disabled; }
		public void setDisabled(boolean disabled) { this.disabled = disabled; }
	
		@Keep public void onCreate(SharedPreferences pref) {
			this.disabled = !pref.getBoolean(prefKey  + ".enabled", true);
			if (BuildConfig.DEBUG) Log.d(TAG, prefKey + ".disabled " + this.disabled);
		}
	
		@Keep public void onDestroy() {}
	
		@Keep public Decorating onNotificationPosted(final StatusBarNotification sbn) {
			if (BuildConfig.DEBUG) Log.d(TAG, "onNotificationPosted(" + sbn + ")");
			return Decorating.Unprocessed;
		}
		@Keep public void onNotificationRemoved(final StatusBarNotification evolving, final int reason) {
			if (BuildConfig.DEBUG) Log.d(TAG, "onNotificationRemoved(" + evolving + ", " + reason + ")");
		}

		protected final void cancelNotification(String key) {
			if (BuildConfig.DEBUG) Log.d(TAG, "cancelNotification " + key);
			final NotificationListenerService listener = getNLS();
			if (listener != null) listener.cancelNotification(key);
		}
	}

	protected final String prefKey;
	// private boolean disabled;

	public NevoDecoratorService() {
		this.prefKey = getClass().getSimpleName();
	}

	private LocalDecorator localDecorator;
	@Keep public LocalDecorator createLocalDecorator(String packageName) { return null; }
	@Keep public final LocalDecorator getLocalDecorator(String packageName) {
		if (localDecorator == null) localDecorator = createLocalDecorator(packageName); // 不同package在不同进程，无需映射packageName
		return localDecorator;
	}
	private SystemUIDecorator systemUIDecorator;
	@Keep public SystemUIDecorator createSystemUIDecorator() { return null; }
	@Keep public final SystemUIDecorator getSystemUIDecorator() {
		if (systemUIDecorator == null) systemUIDecorator = createSystemUIDecorator();
		return systemUIDecorator;
	}
}
