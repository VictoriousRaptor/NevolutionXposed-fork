package com.oasisfeng.nevo.sdk;

import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.util.LruCache;
import android.widget.RemoteViews;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.oasisfeng.nevo.xposed.compat.XposedBridge;
import com.oasisfeng.nevo.xposed.compat.XposedHelpers;

import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.R;

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

		// M2: 限制缓存大小，每个 key 最多缓存 MAX_NUM_ARCHIVED 条通知
		private static final int MAX_CACHE_ENTRIES = 50;
		private static final LruCache<Integer, LinkedList<Notification>> cache = new LruCache<Integer, LinkedList<Notification>>(MAX_CACHE_ENTRIES) {
			protected int sizeOf(Integer key, LinkedList<Notification> value) {
				return value != null ? 1 : 0;
			}

			protected void entryRemoved(boolean evicted, Integer key, LinkedList<Notification> oldValue, LinkedList<Notification> newValue) {
				if (evicted && oldValue != null) {
					oldValue.clear();
				}
			}
		};
	
		protected static void cache(final int id, final Notification n) {
			if (BuildConfig.DEBUG) Log.d(TAG, "cache id " + id);
			LinkedList<Notification> queue = cache.get(id);
			if (queue == null) {
				queue = new LinkedList<>();
				cache.put(id, queue);
			}
			queue.add(n);
			if (BuildConfig.DEBUG) Log.d(TAG, "cache queue " + queue);
			if (queue.size() > MAX_NUM_ARCHIVED) queue.remove();
		}
	
		protected static List<Notification> getArchivedNotifications(int key) {
			LinkedList<Notification> queue = cache.get(key);
			return queue != null ? new ArrayList<>(queue) : new ArrayList<>();
		}
	
		protected static Notification getArchivedNotification(int key) {
			LinkedList<Notification> queue = cache.get(key);
			return queue.getLast();
		}
	
		protected static boolean hasArchivedNotifications(int key) {
			return cache.get(key) != null;
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
	
		public static void setActions(Notification n, Action... actions) {
			try {
				java.lang.reflect.Field field = Notification.class.getDeclaredField("actions");
				field.setAccessible(true);
				field.set(n, actions);
			} catch (Exception e) {
				XposedHelpers.setObjectField(n, "actions", actions);
			}
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
		private static volatile NotificationListenerService mNLS;

		public static void setNLS(NotificationListenerService nls) {
			mNLS = nls;
		}

		public static NotificationListenerService getNLS() {
			return mNLS;
		}

		// M2: 限制缓存大小
		private static final int MAX_SBN_CACHE_ENTRIES = 50;
		private static final LruCache<String, LinkedList<StatusBarNotification>> cache = new LruCache<String, LinkedList<StatusBarNotification>>(MAX_SBN_CACHE_ENTRIES) {
			protected int sizeOf(String key, LinkedList<StatusBarNotification> value) {
				return value != null ? 1 : 0;
			}

			protected void entryRemoved(boolean evicted, String key, LinkedList<StatusBarNotification> oldValue, LinkedList<StatusBarNotification> newValue) {
				if (evicted && oldValue != null) {
					oldValue.clear();
				}
			}
		};
	
		protected static void cache(StatusBarNotification sbn) {
			final String key = sbn.getKey();
			LinkedList<StatusBarNotification> queue = cache.get(key);
			if (queue == null) {
				queue = new LinkedList<>();
				cache.put(key, queue);
			}
			queue.add(sbn);
			if (queue.size() > MAX_NUM_ARCHIVED) queue.remove();
		}
	
		protected static List<StatusBarNotification> getArchivedNotifications(String key) {
			LinkedList<StatusBarNotification> queue = cache.get(key);
			return queue != null ? new ArrayList<>(queue) : new ArrayList<>();
		}
	
		protected static StatusBarNotification getArchivedNotification(String key) {
			LinkedList<StatusBarNotification> queue = cache.get(key);
			return queue.getLast();
		}
	
		protected static boolean hasArchivedNotifications(String key) {
			return cache.get(key) != null;
		}
	
		public static void setId(StatusBarNotification sbn, int id) {
			XposedHelpers.setIntField(sbn, "id", id);
		}
	
		public static void setNotification(StatusBarNotification sbn, Notification n) {
			XposedHelpers.setObjectField(sbn, "notification", n);
		}
	
		public static int getOriginalId(StatusBarNotification sbn) {
			return (Integer)XposedHelpers.getAdditionalInstanceField(sbn, "originalId");
		}
	
		public static void setOriginalId(StatusBarNotification sbn, int id) {
			XposedHelpers.setAdditionalInstanceField(sbn, "originalId", (Integer)id);
		}
	
		public static String getOriginalKey(StatusBarNotification sbn) {
			return (String)XposedHelpers.getAdditionalInstanceField(sbn, "originalKey");
		}
	
		public static void setOriginalKey(StatusBarNotification sbn, String key) {
			XposedHelpers.setAdditionalInstanceField(sbn, "originalKey", key);
		}
	
		public static void setOriginalTag(StatusBarNotification sbn, String tag) {
			XposedHelpers.setAdditionalInstanceField(sbn, "originalTag", tag);
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
			Log.d(TAG, "onNotificationPosted(" + sbn + ")");
			return Decorating.Unprocessed;
		}
		@Keep public void onNotificationRemoved(final StatusBarNotification evolving, final int reason) {
			Log.d(TAG, "onNotificationRemoved(" + evolving + ", " + reason + ")");
		}

		protected final void cancelNotification(String key) {
			Log.d(TAG, "cancelNotification " + key);
			if (mNLS != null) mNLS.cancelNotification(key);
		}
	
		protected final void recastNotification(final StatusBarNotification sbn) {
			Log.d(TAG, "recastNotification " + sbn + " " + mNLS);
			if (mNLS != null) mNLS.onNotificationPosted(sbn, null);
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

	// public boolean isDisabled() { return disabled; }
	// public void setDisabled(boolean disabled) { this.disabled = disabled; }

	// @Keep public void onCreate(SharedPreferences pref) {
	// 	this.disabled = !pref.getBoolean(prefKey  + ".enabled", true);
	// 	if (BuildConfig.DEBUG) Log.d(TAG, prefKey + ".disabled " + this.disabled);
	// }

	// @Keep public void onDestroy() {}

	// /**
	//  * 在应用进程中执行的通知预处理，某些功能（NotificationChannel等）在此实现。
	//  */
	// @Keep public void preApply(NotificationManager nm, String tag, int id, Notification n) {}
	// @Keep public Decorating onNotificationPosted(final StatusBarNotification sbn) {
	// 	apply(sbn);
	// 	return Decorating.Processed;
	// }
	// /**
	//  * 在系统UI（SystemUI）中执行的通知处理。
	//  */
	// @Deprecated
	// @Keep public void apply(final StatusBarNotification evolving) {}
	// @Keep public void onNotificationRemoved(final StatusBarNotification evolving, final int reason) {
	// 	Log.d(TAG, "onNotificationRemoved(" + evolving + ", " + reason + ")");
	// 	onNotificationRemoved(evolving.getKey(), reason);
	// }
	// @Keep public void onNotificationRemoved(final String key, final int reason) {}
}
