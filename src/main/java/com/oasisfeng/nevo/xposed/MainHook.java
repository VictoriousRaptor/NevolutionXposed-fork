package com.oasisfeng.nevo.xposed;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.ContextWrapper;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.oasisfeng.nevo.sdk.HookSupport;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.sdk.NevoDecoratorService.LocalDecorator;
import com.oasisfeng.nevo.sdk.NevoDecoratorService.SystemUIDecorator;
import com.oasisfeng.nevo.xposed.compat.PackageHookContext;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;
import com.oasisfeng.nevo.xposed.compat.XposedHelpers;
import com.oasisfeng.nevo.xposed.BuildConfig;

import io.github.libxposed.api.XposedModule;

/**
 * hook and manupinate notifications.
 * 
 * @author notXX
 */
public class MainHook extends XposedModule {
	private static final String TAG = "MainHook";
	public static final String WECHAT_AUTO_REPLY_ACTION = "com.tencent.mm.permission.MM_AUTO_REPLY_MESSAGE";
	public static final String WECHAT_AUTO_REPLY_RESULT_KEY = "key_voice_reply_text";

	private android.content.SharedPreferences pref;
	private String processName;
	private final NevoDecoratorService wechat = new com.oasisfeng.nevo.decorators.wechat.WeChatDecorator();
	private final NevoDecoratorService miui = new com.oasisfeng.nevo.decorators.MIUIDecorator();
	private final NevoDecoratorService media = new com.oasisfeng.nevo.decorators.media.MediaDecorator();

	private static Class<?> sMMAutoMessageReplyReceiverClass = null;
	private static volatile com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile.Resolved sReplyProfile = null;
	// H2: 改用 ConcurrentHashMap 避免多条回复并发覆盖，以通知 ID 为 key
	private static final ConcurrentHashMap<Integer, String> sPendingReplies = new ConcurrentHashMap<>();
	private static volatile String sPendingReplyTextFallback = null; // 仅用于无 ID 场景的兜底

	private static void logReply(String stage, String detail) {
		String message = "NX_REPLY stage=" + stage + (detail == null || detail.isEmpty() ? "" : " " + detail);
		Log.d(TAG, message);
		XposedBridge.log(message);
	}

	/**
	 * Resolves the reply profile for the installed WeChat build and validates every
	 * descriptor by signature inside the WeChat class loader. Runs once per process.
	 */
	private static synchronized void resolveReplyProfile(Context context) {
		if (context == null || sReplyProfile != null) return;
		try {
			final android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo("com.tencent.mm", 0);
			final String versionName = info.versionName;
			final long versionCode = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
			final com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile profile =
					com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile.forPackage(versionName, versionCode);
			final com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile.Resolved resolved =
					profile.resolve(context.getClassLoader(), versionName, versionCode);
			sReplyProfile = resolved;
			logReply(resolved.isUsable() ? "profile_ready" : "profile_rejected", resolved.describe());
		} catch (Throwable e) {
			XposedBridge.rethrowFrameworkError(e);
			logReply("profile_resolution_failed", Log.getStackTraceString(e));
		}
	}

	public static boolean isSyntheticReplyAvailable() {
		final com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile.Resolved resolved = sReplyProfile;
		return resolved != null && resolved.isUsable();
	}

	public static void setPendingReplyText(String text) {
		sPendingReplyTextFallback = text;
	}

	public static void setPendingReplyText(int notificationId, String text) {
		sPendingReplies.put(notificationId, text);
	}

	private static String consumePendingReplyText(int notificationId) {
		String text = sPendingReplies.remove(notificationId);
		if (text == null) {
			text = sPendingReplyTextFallback;
			sPendingReplyTextFallback = null;
		}
		return text;
	}

	private static void inspect(PackageHookContext loadPackageParam, String className, String... methods) {
		try {
			final Class<?> clazz = XposedHelpers.findClass(className, loadPackageParam.classLoader);
			XposedBridge.log("inspect clazz: " + clazz + " " + loadPackageParam.packageName);
			Consumer<String> inspect = method -> {
				XposedBridge.hookAllMethods(clazz, method, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						Log.d("inspect.method", loadPackageParam.packageName + " " + param.method.getName());
						for (Object arg : param.args) {
							Log.d("inspect.method", loadPackageParam.packageName + " arg " + arg);
						}
						if (BuildConfig.DEBUG) Log.d(TAG, loadPackageParam.packageName + " " + Log.getStackTraceString(new Exception()));
					}
				});
			};
			for (String method : methods) {
				inspect.accept(method);
			}
		} catch (XposedHelpers.ClassNotFoundError e) { /* XposedBridge.log("ContextImpl hook failed"); */ }
	}

	@Keep
	private static void inspectThen(PackageHookContext loadPackageParam, String className, Consumer<Class<?>>... thens) {
		try {
			final Class<?> clazz = XposedHelpers.findClass(className, loadPackageParam.classLoader);
			XposedBridge.log("inspect clazz: " + clazz + " " + loadPackageParam.packageName);
			for (Consumer<Class<?>> then : thens) {
				then.accept(clazz);
			}
		} catch (XposedHelpers.ClassNotFoundError e) { /* XposedBridge.log("ContextImpl hook failed"); */ }
	}

	@Override
	public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
		XposedBridge.attach(this);
		processName = param.getProcessName();
		if (!"com.android.systemui".equals(processName) && !"com.tencent.mm".equals(processName)) {
			detach();
			return;
		}
		pref = getRemotePreferences(RemotePreferenceStore.GROUP);
		log(Log.INFO, TAG, "Loaded in " + processName + " with " + getFrameworkName()
				+ " " + getFrameworkVersion() + " API " + getApiVersion());
	}

	@Override
	public void onPackageReady(@NonNull PackageReadyParam param) {
		if (!param.isFirstPackage()) return;
		PackageHookContext loadPackageParam = new PackageHookContext(
				param.getPackageName(), processName, param.getClassLoader());
		switch (loadPackageParam.packageName) {
			case "com.android.systemui":
				if (!"com.android.systemui".equals(loadPackageParam.processName)) return;
			hookSystemUI(loadPackageParam);
			break;
			case "com.tencent.mm":
				if (!"com.tencent.mm".equals(loadPackageParam.processName)) return;
			hookWeChat(loadPackageParam);
			break;
		}
		/* inspect(loadPackageParam,
				"com.android.server.notification.NotificationManagerService",
				"getNotificationChannel",
				"deleteNotificationChannel",
				"deleteNotificationChannelGroup",
				"createNotificationChannels"); */
	}

	private void hookSystemUI(PackageHookContext loadPackageParam) {
		AtomicReference<NotificationListenerService> nlsRef = new AtomicReference<>();
		final XC_MethodHook onNotificationPosted = new XC_MethodHook() { // 捕获通知到达
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				StatusBarNotification sbn = (StatusBarNotification)param.args[0];
				// RankingMap rankingMap = (RankingMap)param.args[1];
				Log.d(TAG, "onNotificationPosted");
				onNotificationPosted(sbn);
			}
		}, onNotificationRemoved = new XC_MethodHook() { // 捕获通知移除
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				StatusBarNotification sbn = (StatusBarNotification)param.args[0];
				// Adaptive reason extraction: handle both old (with NotificationStats) and new (without) signatures
				int reason;
				try {
					if (param.args.length >= 4 && param.args[3] instanceof Integer) {
						reason = (int) param.args[3]; // Old: (sbn, rankingMap, notificationStats, reason)
					} else if (param.args.length >= 3 && param.args[2] instanceof Integer) {
						reason = (int) param.args[2]; // New: (sbn, rankingMap, reason)
					} else {
						reason = 0;
					}
				} catch (Exception e) {
					reason = 0;
				}
				Log.d(TAG, "onNotificationRemoved");
				onNotificationRemoved(sbn, reason);
			}
		}, nls = new XC_MethodHook() { // 捕获NotificationListenerService的具体实现
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				// XposedBridge.log("nls constructor " + param.thisObject);
				NotificationListenerService nls = (NotificationListenerService)param.thisObject;
				if (nlsRef.compareAndSet(null, nls)) {
					SystemUIDecorator.setNLS(nls);
				}
				try {
					final Class<?> clazz = nls.getClass();
					XposedBridge.log("NL clazz: " + clazz + " " + loadPackageParam.packageName);
					Method method = XposedHelpers.findMethodExact(clazz, "onNotificationPosted", 
							StatusBarNotification.class, RankingMap.class);
					Log.d(TAG, "method " + method);
					XposedBridge.hookMethod(method, onNotificationPosted);
					// Try Android 16+ signature first, then fall back to older signature
					try {
						method = XposedHelpers.findMethodBestMatch(clazz, "onNotificationRemoved", StatusBarNotification.class, RankingMap.class,
								XposedHelpers.findClass("android.service.notification.NotificationStats", loadPackageParam.classLoader), int.class);
					} catch (XposedHelpers.ClassNotFoundError e2) {
						// Android 16 may have changed NotificationStats or its location
						XposedBridge.log("NotificationStats not found, trying without it");
						try {
							method = XposedHelpers.findMethodBestMatch(clazz, "onNotificationRemoved", StatusBarNotification.class, RankingMap.class, int.class);
						} catch (Throwable e3) {
							XposedBridge.rethrowFrameworkError(e3);
							XposedBridge.log("onNotificationRemoved hook failed: " + e3.getMessage());
							method = null;
						}
					}
					if (method != null) {
						Log.d(TAG, "method " + method);
						XposedBridge.hookMethod(method, onNotificationRemoved);
					} else {
						XposedBridge.log("WARNING: onNotificationRemoved hook completely failed, notification removal tracking disabled");
					}
				} catch (Throwable e) {
					XposedBridge.rethrowFrameworkError(e);
					XposedBridge.log("NL hook failed: " + e.getMessage());
				}
			}
		};
		try {
			XposedBridge.hookAllConstructors(NotificationListenerService.class, nls);
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log("NotificationListenerService hook failed "); }
		try {
			final Class<?> clazz = XposedHelpers.findClass("android.app.ContextImpl", loadPackageParam.classLoader);
			XposedBridge.log("CI clazz: " + clazz);
			AtomicReference<Context> ref = new AtomicReference<>();
			XposedBridge.hookAllMethods(clazz, "createAppContext", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					Context context = (Context)param.getResult();
					if (ref.compareAndSet(null, context)) {
						XposedBridge.log("onCreate " + context);
						onCreate(context);
					}
				}
			});
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log("ContextImpl hook failed"); }
		try {
			SystemUIDecorator media = this.media.getSystemUIDecorator();
			if ((media instanceof HookSupport)) { ((HookSupport)media).hook(loadPackageParam); }
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log(this.media + " hook failed"); }
	}
	
	private void onCreate(Context context) {
		NevoDecoratorService.setAppContext(context);

		SystemUIDecorator miui = this.miui.getSystemUIDecorator(),
				media = this.media.getSystemUIDecorator();
		miui.onCreate(pref);
		media.onCreate(pref);
	}

	private void onNotificationPosted(StatusBarNotification sbn) {
		if (XposedHelpers.getAdditionalInstanceField(sbn, "applied") != null) {
			Log.d(TAG, "skip " + sbn);
			return;
		}
		XposedHelpers.setAdditionalInstanceField(sbn, "applied", true);

		SystemUIDecorator miui = this.miui.getSystemUIDecorator(),
				media = this.media.getSystemUIDecorator();
		switch (sbn.getPackageName()) {
			case "com.xiaomi.xmsf":
			if (!miui.isDisabled()) miui.onNotificationPosted(sbn);
			break;
		}
		if (!media.isDisabled()) media.onNotificationPosted(sbn);
	}

	private void onNotificationRemoved(StatusBarNotification sbn, int reason) {
		SystemUIDecorator miui = this.miui.getSystemUIDecorator(),
				media = this.media.getSystemUIDecorator();
		switch (sbn.getPackageName()) {
			case "com.xiaomi.xmsf":
			if (!miui.isDisabled()) miui.onNotificationRemoved(sbn, reason);
			break;
		}
		if (!media.isDisabled()) media.onNotificationRemoved(sbn, reason);
	}

	private void hookWeChat(PackageHookContext loadPackageParam) {
		if (!"com.tencent.mm".equals(loadPackageParam.processName)) return;
		try {
			final Class<?> clazz = XposedHelpers.findClass("android.app.NotificationManager", loadPackageParam.classLoader);
			XposedBridge.log("NM clazz: " + clazz);
			Method method = XposedHelpers.findMethodExact(clazz, "notify", String.class, int.class, Notification.class);
			XposedBridge.log("NM.notify: " + method);
			XposedBridge.hookMethod(method, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					NotificationManager nm = (NotificationManager)param.thisObject;
					String tag = (String)param.args[0];
					int id = (int)param.args[1];
					Notification n = (Notification)param.args[2];
					Log.d(TAG, "before apply " + nm + " " + tag + " " + id);
					applyLocally(nm, tag, id, n);
					// 用 Notification.Builder 重建通知，确保 actions 被正确序列化
					Log.d(TAG, "after apply, actions=" + (n.actions != null ? n.actions.length : "null"));
					try {
						Context ctx = NevoDecoratorService.getAppContext();
						if (ctx != null && n.actions != null) {
							Notification.Builder builder = Notification.Builder.recoverBuilder(ctx, n);
							Notification rebuilt = builder.build();
							if (rebuilt.actions != null) {
								boolean hasRI = false;
								for (Notification.Action a : rebuilt.actions) {
									if (a != null && a.getRemoteInputs() != null) {
										for (android.app.RemoteInput ri : a.getRemoteInputs()) {
											if (ri != null && ri.getAllowFreeFormInput()) { hasRI = true; break; }
										}
									}
									if (hasRI) break;
								}
								if (hasRI) {
									param.args[2] = rebuilt;
									Log.d(TAG, "Rebuilt notification with RemoteInput");
								}
							}
						}
					} catch (Exception e) {
						Log.w(TAG, "Failed to rebuild: " + e.getMessage());
					}
				}
			});
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log(this.wechat + " NotificationManager hook failed"); }
		try {
			AtomicReference<Context> ref = new AtomicReference<>();
			XposedBridge.log("hookWeChat: registering Application.onCreate hook");
			hookWeChatMessageSending(loadPackageParam);
			hookAutoReplyReceiver(loadPackageParam);
			XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					Application app = (Application) param.thisObject;
					Context context = app.getApplicationContext();
					if (context != null && ref.compareAndSet(null, context)) {
						XposedBridge.log("hookWeChat: Application.onCreate context=" + context);
						NevoDecoratorService.setAppContext(context);
						resolveReplyProfile(context);
						// Install the car-mode gate bypass before WeChat builds its
						// notifications, so WeChat itself creates the car conversation
						// with the reply PendingIntent that carries key_username. The
						// synthetic path cannot provide that username by itself.
						MainHook.this.hookCarModeBypass(context.getClassLoader());
						LocalDecorator wechat = MainHook.this.wechat.getLocalDecorator("com.tencent.mm");
						wechat.onCreate(pref);
						if (!wechat.isDisabled() && (wechat instanceof HookSupport)) ((HookSupport)wechat).hook(loadPackageParam);
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.rethrowFrameworkError(e);
			XposedBridge.log(this.wechat + " Application.onCreate hook failed: " + e.getMessage());
		}
		/* inspect(loadPackageParam,
				"com.android.server.notification.NotificationManagerService",
				"getNotificationChannel",
				"deleteNotificationChannel",
				"deleteNotificationChannelGroup",
				"createNotificationChannels"); */
	}

	// TODO
	private void applyLocally(NotificationManager nm, String tag, int id, Notification n) {
		if (NevoDecoratorService.getAppContext() == null) {
			XposedBridge.log("applyLocally: application context is not ready; skipping notification");
			return;
		}
		if (XposedHelpers.getAdditionalInstanceField(n, "pre-applied") != null) {
			Log.d(TAG, "skip " + n);
			return;
		}
		XposedHelpers.setAdditionalInstanceField(n, "pre-applied", true);
		LocalDecorator.setNM(nm);
		LocalDecorator wechat = this.wechat.getLocalDecorator("com.tencent.mm");
		if (!wechat.isDisabled()) wechat.apply(nm, tag, id, n);
	}


	private void hookWeChatMessageSending(PackageHookContext loadPackageParam) {
		XposedBridge.log("hookWeChatMessageSending: searching for CarExtender handling code");
		final ClassLoader cl = loadPackageParam.classLoader;
		// Search for classes related to CarExtender or CarNotification
		String[] candidates = {
			"android.app.Notification$CarExtender",
			"android.app.Notification$CarExtender$UnreadConversation",
			"android.support.v4.app.NotificationCompat$CarExtender",
			"androidx.core.app.NotificationCompat$CarExtender",
		};
		for (String className : candidates) {
			try {
				Class<?> clazz = XposedHelpers.findClass(className, cl);
				XposedBridge.log("hookWeChatMessageSending: found " + className);
				for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
					XposedBridge.log("hookWeChatMessageSending: " + className + "." + m.getName());
				}
			} catch (Throwable e) {
				XposedBridge.rethrowFrameworkError(e);
				XposedBridge.log("hookWeChatMessageSending: " + className + " not found");
			}
		}
		// Search for classes that might handle reply PendingIntents
		String[] replyCandidates = {
			"com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver",
			"com.tencent.mm.booter.notification.NotificationItem",
		};
		for (String className : replyCandidates) {
			try {
				Class<?> clazz = XposedHelpers.findClass(className, cl);
				XposedBridge.log("hookWeChatMessageSending: found " + className);
				for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
					XposedBridge.log("hookWeChatMessageSending: " + className + "." + m.getName() + " params=" + java.util.Arrays.toString(m.getParameterTypes()));
				}
			} catch (Throwable e) {
				XposedBridge.rethrowFrameworkError(e);
				XposedBridge.log("hookWeChatMessageSending: " + className + " not found");
			}
		}
	}

	private void hookAutoReplyReceiver(PackageHookContext loadPackageParam) {
		try {
			final Class<?> receiverClass = XposedHelpers.findClass("com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver", loadPackageParam.classLoader);
			sMMAutoMessageReplyReceiverClass = receiverClass;
			XposedBridge.log("hookAutoReplyReceiver: found class " + receiverClass);
			// Hook onReceive and trace ALL method calls from within
			final ClassLoader finalCl = loadPackageParam.classLoader;
			XposedHelpers.findAndHookMethod(receiverClass, "onReceive", android.content.Context.class, android.content.Intent.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					// Debug: log RemoteInput.getResultsFromIntent
					try {
						android.os.Bundle riResults = android.app.RemoteInput.getResultsFromIntent((android.content.Intent) param.args[1]);
						logReply("wechat_receiver_enter", "remoteInputKeys=" + (riResults == null ? "none" : riResults.keySet()));
					} catch (Throwable e) {
						XposedBridge.rethrowFrameworkError(e);
						logReply("wechat_receiver_remote_input_failed", Log.getStackTraceString(e));
					}
					// Try to hook car mode bypass if not already done
					hookCarModeBypass(finalCl);
					android.content.Intent intent = (android.content.Intent) param.args[1];
					logReply("wechat_receiver_action", "action=" + intent.getAction());
					if (intent.getExtras() != null) {
						logReply("wechat_receiver_extras", "keys=" + intent.getExtras().keySet());
					}
				}
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					logReply("wechat_receiver_complete", "throwable=" + (param.getThrowable() != null));
					if (param.getThrowable() != null) {
						logReply("wechat_receiver_exception", Log.getStackTraceString(param.getThrowable()));
					}
				}
			});
			// Hook ALL methods in the receiver class
			for (java.lang.reflect.Method method : receiverClass.getDeclaredMethods()) {
				if (!"onReceive".equals(method.getName())) {
					XposedBridge.log("hookAutoReplyReceiver: found method " + method.getName() + " params=" + java.util.Arrays.toString(method.getParameterTypes()));
					final String methodName = method.getName();
					try {
						XposedHelpers.findAndHookMethod(receiverClass, method.getName(), method.getParameterTypes(), new XC_MethodHook() {
							@Override
							protected void beforeHookedMethod(MethodHookParam param) {
								logReply("wechat_receiver_method_enter", "method=" + methodName + " argCount=" + param.args.length);
							}
							@Override
							protected void afterHookedMethod(MethodHookParam param) {
								logReply("wechat_receiver_method_exit", "method=" + methodName + " throwable=" + (param.getThrowable() != null));
								if (param.getThrowable() != null) {
									logReply("wechat_receiver_method_exception", "method=" + methodName + " " + Log.getStackTraceString(param.getThrowable()));
								}
							}
						});
					} catch (Throwable e) {
						XposedBridge.rethrowFrameworkError(e);
						XposedBridge.log("hookAutoReplyReceiver: failed to hook " + methodName + ": " + e.getMessage());
					}
				}
			}
			// Also hook the parent class methods
			Class<?> superClass = receiverClass.getSuperclass();
			while (superClass != null && !superClass.equals(Object.class)) {
				XposedBridge.log("hookAutoReplyReceiver: parent class " + superClass.getName());
				for (java.lang.reflect.Method method : superClass.getDeclaredMethods()) {
					XposedBridge.log("hookAutoReplyReceiver: parent method " + superClass.getName() + "." + method.getName());
				}
				superClass = superClass.getSuperclass();
			}
		} catch (Throwable e) {
			XposedBridge.rethrowFrameworkError(e);
			XposedBridge.log("hookAutoReplyReceiver failed: " + e.getMessage());
		}
	}

	public static boolean invokeMMAutoReply(android.content.Context context, android.content.Intent intent) {
		if (sMMAutoMessageReplyReceiverClass == null) {
			logReply("synthetic_unavailable", "reason=receiver_not_hooked");
			return false;
		}
		resolveReplyProfile(context);
		final com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile.Resolved resolved = sReplyProfile;
		if (resolved == null || !resolved.isUsable()) {
			// Without a validated car-mode gate the receiver returns at
			// "not open car mode" and the reply is lost silently.
			logReply("synthetic_unavailable", "reason=profile_unusable version="
					+ (resolved == null ? "unknown" : resolved.versionName));
			return false;
		}
		try {
			final Class<?> gateClass = resolved.gateClass;
			for (final String methodName : resolved.gateMethods) {
				try {
					XposedHelpers.findAndHookMethod(gateClass, methodName, new XC_MethodHook() {
						@Override
						protected void beforeHookedMethod(MethodHookParam param) { param.setResult(true); }
					});
				} catch (Throwable failure) {
					XposedBridge.rethrowFrameworkError(failure);
				} // hooks may already be added
			}
			logReply("car_mode_bypass_ready", "class=" + gateClass.getName() + " methods=" + resolved.gateMethods);
		} catch (Throwable th) {
			XposedBridge.rethrowFrameworkError(th);
			logReply("car_mode_bypass_failed", Log.getStackTraceString(th));
		}
		try {
			// H2: 设置 pendingReplyText 供 RemoteInput.getResultsFromIntent hook 使用
			String replyText = intent.getStringExtra("reply_content");
			int notifId = intent.getIntExtra("notification_id", -1);
			if (replyText != null) {
				if (notifId >= 0) {
					sPendingReplies.put(notifId, replyText);
				}
				sPendingReplyTextFallback = replyText;
				logReply("synthetic_prepare", "notificationId=" + notifId + " inputLength=" + replyText.length());

				// 使用 RemoteInput.addResultsToIntent 设置回复文本
				android.app.RemoteInput[] remoteInputs = new android.app.RemoteInput[]{
					new android.app.RemoteInput.Builder(WECHAT_AUTO_REPLY_RESULT_KEY)
						.setAllowFreeFormInput(true)
						.build()
				};
				android.os.Bundle remoteInputResults = new android.os.Bundle();
				remoteInputResults.putCharSequence(WECHAT_AUTO_REPLY_RESULT_KEY, replyText);
				android.app.RemoteInput.addResultsToIntent(remoteInputs, intent, remoteInputResults);
				logReply("synthetic_remote_input_attached", "resultKey=" + WECHAT_AUTO_REPLY_RESULT_KEY);
			}

			// 直接调用 onReceive
			Object instance = sMMAutoMessageReplyReceiverClass.getDeclaredConstructor().newInstance();
			java.lang.reflect.Method onReceive = sMMAutoMessageReplyReceiverClass.getMethod("onReceive", android.content.Context.class, android.content.Intent.class);
			onReceive.invoke(instance, context, intent);
			logReply("synthetic_dispatch_complete", "receiver=" + sMMAutoMessageReplyReceiverClass.getName());
			return true;
		} catch (Throwable e) {
			XposedBridge.rethrowFrameworkError(e);
			logReply("synthetic_dispatch_failed", Log.getStackTraceString(e));
			return false;
		}
	}

	private boolean carModeBypassHooked = false;

	// H1: 动态搜索特征方法的辅助方法，避免硬编码混淆类名
	/**
	 * 返回当前进程已验证的版本画像；未解析时尝试用已捕获的 Application Context 解析。
	 * 之前的实现按模糊方法名猜混淆类名，8.0.72 把 dn1.a 换成 rn1.a 后就失效了。
	 */
	private static com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile.Resolved currentProfile() {
		com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile.Resolved resolved = sReplyProfile;
		if (resolved == null) {
			resolveReplyProfile(NevoDecoratorService.getAppContext());
			resolved = sReplyProfile;
		}
		return resolved;
	}

	private void hookCarModeBypass(ClassLoader cl) {
		if (carModeBypassHooked) return;
		try {
			final com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile.Resolved resolved = currentProfile();
			if (resolved == null || !resolved.isUsable()) {
				logReply("car_mode_bypass_skipped", "reason=profile_unusable");
				return;
			}
			hookAutoLogicMethods(resolved.gateClass, resolved.gateMethods);

			// 版本画像中已通过签名校验的 RemoteInput 辅助类
			try {
				final Class<?> remoteInputHelper = resolved.helperClass;
				if (remoteInputHelper != null) {
					XposedHelpers.findAndHookMethod(remoteInputHelper, "b", android.content.Intent.class, new XC_MethodHook() {
						@Override
						protected void afterHookedMethod(MethodHookParam param) {
							android.os.Bundle result = (android.os.Bundle) param.getResult();
							logReply("helper_result", "keys=" + (result == null ? "none" : result.keySet()));
							if (result == null) {
								String replyText = sPendingReplyTextFallback;
								if (replyText != null) {
									result = new android.os.Bundle();
									result.putCharSequence(WECHAT_AUTO_REPLY_RESULT_KEY, replyText);
									param.setResult(result);
									logReply("helper_result_injected", "resultKey=" + WECHAT_AUTO_REPLY_RESULT_KEY + " inputLength=" + replyText.length());
									sPendingReplyTextFallback = null;
								}
							}
						}
					});
					XposedBridge.log("hookCarModeBypass: RemoteInputHelper.b() hook added");
				}
			} catch (Throwable e) {
				XposedBridge.rethrowFrameworkError(e);
				XposedBridge.log("hookCarModeBypass: RemoteInputHelper hook failed: " + e.getMessage());
			}

			// Hook RemoteInput.getResultsFromIntent to return synthetic results
			try {
				XposedHelpers.findAndHookMethod(android.app.RemoteInput.class, "getResultsFromIntent", android.content.Intent.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						android.os.Bundle result = (android.os.Bundle) param.getResult();
						if (result == null) {
							String replyText = sPendingReplyTextFallback;
							if (replyText != null) {
								result = new android.os.Bundle();
								result.putCharSequence(WECHAT_AUTO_REPLY_RESULT_KEY, replyText);
								param.setResult(result);
								logReply("platform_result_injected", "resultKey=" + WECHAT_AUTO_REPLY_RESULT_KEY + " inputLength=" + replyText.length());
								sPendingReplyTextFallback = null;
							}
						}
					}
				});
				XposedBridge.log("hookCarModeBypass: RemoteInput.getResultsFromIntent hook added");
			} catch (Throwable e) {
				XposedBridge.rethrowFrameworkError(e);
				XposedBridge.log("hookCarModeBypass: failed to hook RemoteInput: " + e.getMessage());
			}
			carModeBypassHooked = true;
			XposedBridge.log("hookCarModeBypass: car mode bypass hooks added");
		} catch (Throwable e) {
			XposedBridge.rethrowFrameworkError(e);
			XposedBridge.log("hookCarModeBypass: failed: " + e.getMessage());
		}
	}

	private void hookAutoLogicMethods(final Class<?> autoLogicClass, java.util.List<String> bypassMethods) {
		XposedBridge.log("hookAutoLogicMethods: hooking " + autoLogicClass.getName() + " " + bypassMethods);
		for (final String methodName : bypassMethods) {
			try {
				XposedHelpers.findAndHookMethod(autoLogicClass, methodName, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) {
						param.setResult(true);
						XposedBridge.log("Bypassed " + autoLogicClass.getSimpleName() + "." + methodName + "() -> true");
					}
				});
			} catch (Throwable e) {
				XposedBridge.rethrowFrameworkError(e);
				XposedBridge.log("hookAutoLogicMethods: " + methodName + " hook failed: " + e.getMessage());
			}
		}
	}
}
