package com.oasisfeng.nevo.xposed;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

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
	/** A rejected profile is retried, but never more often than this. */
	private static final long PROFILE_RETRY_INTERVAL_MS = 5_000L;
	private static volatile long sLastProfileAttempt = 0L;
	/** Single-use synthetic reply text; expires so a stale reply cannot leak into a later one. */
	private static final long PENDING_REPLY_TTL_MS = 30_000L;
	private static volatile String sPendingReplyText = null;
	private static volatile long sPendingReplySetAt = 0L;

	private static void logReply(String stage, String detail) {
		String message = "NX_REPLY stage=" + stage + (detail == null || detail.isEmpty() ? "" : " " + detail);
		Log.d(TAG, message);
		XposedBridge.log(message);
	}

	/**
	 * Resolves the reply profile for the installed WeChat build and validates every
	 * descriptor by signature inside the WeChat class loader. A usable result is cached
	 * for the process; a rejected one is retried at most once per retry interval, so a
	 * transient failure does not disable synthetic replies until the process restarts.
	 */
	private static synchronized void resolveReplyProfile(Context context) {
		if (context == null) return;
		final com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile.Resolved cached = sReplyProfile;
		if (cached != null && cached.isUsable()) return;
		final long now = android.os.SystemClock.elapsedRealtime();
		if (now - sLastProfileAttempt < PROFILE_RETRY_INTERVAL_MS) return;
		sLastProfileAttempt = now;
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
			sReplyProfile = null;
			logReply("profile_resolution_failed", Log.getStackTraceString(e));
		}
	}

	public static boolean isSyntheticReplyAvailable() {
		final com.oasisfeng.nevo.decorators.wechat.WeChatReplyProfile.Resolved resolved = sReplyProfile;
		return resolved != null && resolved.isUsable();
	}

	/** Arms the single-use synthetic reply text. */
	private static void rememberPendingReply(String text) {
		sPendingReplyText = text;
		sPendingReplySetAt = android.os.SystemClock.elapsedRealtime();
	}

	private static boolean isSyntheticReplyIntent(Object candidate) {
		return candidate instanceof Intent && WECHAT_AUTO_REPLY_ACTION.equals(((Intent) candidate).getAction());
	}

	/**
	 * Consumes the armed text exactly once, and only for the synthetic reply intent we
	 * dispatched ourselves, so an unrelated RemoteInput lookup can never pick it up.
	 */
	private static String takePendingReplyText(Object intentArgument) {
		if (!isSyntheticReplyIntent(intentArgument)) return null;
		final String text = sPendingReplyText;
		if (text == null) return null;
		sPendingReplyText = null;
		if (android.os.SystemClock.elapsedRealtime() - sPendingReplySetAt > PENDING_REPLY_TTL_MS) return null;
		return text;
	}

	@Override
	public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
		XposedBridge.attach(this);
		processName = param.getProcessName();
		// Single line per process: proves whether the framework injected this module and lets the
		// device smoke test tell "not injected" apart from "injected but no notification yet".
		if (BuildConfig.DEBUG) Log.d(TAG, "onModuleLoaded: " + processName);
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
	}

	private void hookSystemUI(PackageHookContext loadPackageParam) {
		AtomicReference<NotificationListenerService> nlsRef = new AtomicReference<>();
		final XC_MethodHook onNotificationPosted = new XC_MethodHook() { // 捕获通知到达
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				StatusBarNotification sbn = (StatusBarNotification)param.args[0];
				// RankingMap rankingMap = (RankingMap)param.args[1];
				if (BuildConfig.DEBUG) Log.d(TAG, "onNotificationPosted");
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
				if (BuildConfig.DEBUG) Log.d(TAG, "onNotificationRemoved");
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
					if (BuildConfig.DEBUG) Log.d(TAG, "NL clazz: " + clazz + " " + loadPackageParam.packageName);
					Method method = XposedHelpers.findMethodExact(clazz, "onNotificationPosted", 
							StatusBarNotification.class, RankingMap.class);
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
			AtomicReference<Context> ref = new AtomicReference<>();
			XposedBridge.hookAllMethods(clazz, "createAppContext", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					Context context = (Context)param.getResult();
					if (ref.compareAndSet(null, context)) {
						if (BuildConfig.DEBUG) Log.d(TAG, "onCreate " + context);
						onCreate(context);
					}
				}
			});
		} catch (XposedHelpers.ClassNotFoundError e) { XposedBridge.log("ContextImpl hook failed"); }
		try {
			final SystemUIDecorator media = this.media.getSystemUIDecorator();
			if (media instanceof HookSupport && isDecoratorEnabled("MediaDecorator"))
				((HookSupport) media).hook(loadPackageParam);
		} catch (Throwable e) {
			XposedBridge.rethrowFrameworkError(e);
			XposedBridge.log(this.media + " hook failed: " + e.getMessage());
		}
	}

	/**
	 * SystemUI hooks are installed before {@code onCreate(pref)} runs, so the remote setting is
	 * read directly instead of relying on the decorator's flag; unknown state means "disabled".
	 */
	private boolean isDecoratorEnabled(String prefKey) {
		final android.content.SharedPreferences preferences = pref;
		return preferences != null && preferences.getBoolean(prefKey + ".enabled", false);
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
			if (BuildConfig.DEBUG) Log.d(TAG, "skip " + sbn);
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
			Method method = XposedHelpers.findMethodExact(clazz, "notify", String.class, int.class, Notification.class);
			if (BuildConfig.DEBUG) Log.d(TAG, "NM.notify: " + method);
			XposedBridge.hookMethod(method, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					NotificationManager nm = (NotificationManager)param.thisObject;
					String tag = (String)param.args[0];
					int id = (int)param.args[1];
					Notification n = (Notification)param.args[2];
					if (BuildConfig.DEBUG) Log.d(TAG, "before apply " + nm + " " + tag + " " + id);
					applyLocally(nm, tag, id, n);
					// 用 Notification.Builder 重建通知，确保 actions 被正确序列化
					if (BuildConfig.DEBUG) Log.d(TAG, "after apply, actions=" + (n.actions != null ? n.actions.length : "null"));
					// Rebuilding is only ever needed for free-form RemoteInput actions, and it costs a
					// full notification teardown/rebuild, so skip it when the notification has none.
					try {
						final Context ctx = NevoDecoratorService.getAppContext();
						if (ctx != null && hasFreeFormRemoteInput(n)) {
							Notification.Builder builder = Notification.Builder.recoverBuilder(ctx, n);
							Notification rebuilt = builder.build();
							if (hasFreeFormRemoteInput(rebuilt)) {
								param.args[2] = rebuilt;
								if (BuildConfig.DEBUG) Log.d(TAG, "Rebuilt notification with RemoteInput");
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
			hookAutoReplyReceiver(loadPackageParam);
			XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					Application app = (Application) param.thisObject;
					Context context = app.getApplicationContext();
					if (context != null && ref.compareAndSet(null, context)) {
						if (BuildConfig.DEBUG) Log.d(TAG, "Application.onCreate context=" + context);
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
	}

	// TODO
	private void applyLocally(NotificationManager nm, String tag, int id, Notification n) {
		if (NevoDecoratorService.getAppContext() == null) {
			if (BuildConfig.DEBUG) Log.d(TAG, "applyLocally: application context is not ready; skipping notification");
			return;
		}
		if (XposedHelpers.getAdditionalInstanceField(n, "pre-applied") != null) {
			if (BuildConfig.DEBUG) Log.d(TAG, "skip " + n);
			return;
		}
		XposedHelpers.setAdditionalInstanceField(n, "pre-applied", true);
		LocalDecorator.setNM(nm);
		LocalDecorator wechat = this.wechat.getLocalDecorator("com.tencent.mm");
		if (!wechat.isDisabled()) wechat.apply(nm, tag, id, n);
	}

	/** True when the notification carries an inline reply input, i.e. the rebuild workaround is needed. */
	private static boolean hasFreeFormRemoteInput(Notification n) {
		final Notification.Action[] actions = n.actions;
		if (actions == null) return false;
		for (final Notification.Action action : actions) {
			if (action == null) continue;
			final android.app.RemoteInput[] inputs = action.getRemoteInputs();
			if (inputs == null) continue;
			for (final android.app.RemoteInput input : inputs)
				if (input != null && input.getAllowFreeFormInput()) return true;
		}
		return false;
	}


	private void hookAutoReplyReceiver(PackageHookContext loadPackageParam) {
		try {
			final Class<?> receiverClass = XposedHelpers.findClass("com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver", loadPackageParam.classLoader);
			sMMAutoMessageReplyReceiverClass = receiverClass;
			if (BuildConfig.DEBUG) Log.d(TAG, "hookAutoReplyReceiver: found class " + receiverClass);
			// Only the public entry point is traced; hooking every declared method of the
			// receiver produced per-call logging without adding diagnostic value.
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
			final String replyText = intent.getStringExtra("reply_content");
			final int notifId = intent.getIntExtra("notification_id", -1);
			if (replyText != null) {
				// Single-use fallback, consumed only by the intent dispatched below.
				rememberPendingReply(replyText);
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
							if (result != null || param.args.length == 0) return;
							final String replyText = takePendingReplyText(param.args[0]);
							if (replyText == null) return;
							result = new android.os.Bundle();
							result.putCharSequence(WECHAT_AUTO_REPLY_RESULT_KEY, replyText);
							param.setResult(result);
							logReply("helper_result_injected", "resultKey=" + WECHAT_AUTO_REPLY_RESULT_KEY + " inputLength=" + replyText.length());
						}
					});
					if (BuildConfig.DEBUG) Log.d(TAG, "RemoteInputHelper.b() hook added");
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
						if (result != null || param.args.length == 0) return;
						final String replyText = takePendingReplyText(param.args[0]);
						if (replyText == null) return;
						result = new android.os.Bundle();
						result.putCharSequence(WECHAT_AUTO_REPLY_RESULT_KEY, replyText);
						param.setResult(result);
						logReply("platform_result_injected", "resultKey=" + WECHAT_AUTO_REPLY_RESULT_KEY + " inputLength=" + replyText.length());
					}
				});
				if (BuildConfig.DEBUG) Log.d(TAG, "RemoteInput.getResultsFromIntent hook added");
			} catch (Throwable e) {
				XposedBridge.rethrowFrameworkError(e);
				XposedBridge.log("hookCarModeBypass: failed to hook RemoteInput: " + e.getMessage());
			}
			carModeBypassHooked = true;
			if (BuildConfig.DEBUG) Log.d(TAG, "car mode bypass hooks added");
		} catch (Throwable e) {
			XposedBridge.rethrowFrameworkError(e);
			XposedBridge.log("hookCarModeBypass: failed: " + e.getMessage());
		}
	}

	private void hookAutoLogicMethods(final Class<?> autoLogicClass, java.util.List<String> bypassMethods) {
		if (BuildConfig.DEBUG) Log.d(TAG, "hooking " + autoLogicClass.getName() + " " + bypassMethods);
		for (final String methodName : bypassMethods) {
			try {
				XposedHelpers.findAndHookMethod(autoLogicClass, methodName, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) {
						param.setResult(true);
						if (BuildConfig.DEBUG) Log.d(TAG, "Bypassed " + autoLogicClass.getSimpleName() + "." + methodName + "() -> true");
					}
				});
			} catch (Throwable e) {
				XposedBridge.rethrowFrameworkError(e);
				XposedBridge.log("hookAutoLogicMethods: " + methodName + " hook failed: " + e.getMessage());
			}
		}
	}
}
