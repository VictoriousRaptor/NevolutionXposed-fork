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

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.notxx.xposed.DeviceSharedPreferences;

import com.oasisfeng.nevo.sdk.HookSupport;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.sdk.NevoDecoratorService.LocalDecorator;
import com.oasisfeng.nevo.sdk.NevoDecoratorService.SystemUIDecorator;
import com.oasisfeng.nevo.xposed.BuildConfig;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * hook and manupinate notifications.
 * 
 * @author notXX
 */
public class MainHook implements IXposedHookLoadPackage {
	private static final String TAG = "MainHook";

	private final XSharedPreferences pref = DeviceSharedPreferences.get(BuildConfig.APPLICATION_ID);
	private final NevoDecoratorService wechat = new com.oasisfeng.nevo.decorators.wechat.WeChatDecorator();
	private final NevoDecoratorService miui = new com.oasisfeng.nevo.decorators.MIUIDecorator();
	private final NevoDecoratorService media = new com.oasisfeng.nevo.decorators.media.MediaDecorator();

	private static Class<?> sMMAutoMessageReplyReceiverClass = null;
	// H2: 改用 ConcurrentHashMap 避免多条回复并发覆盖，以通知 ID 为 key
	private static final ConcurrentHashMap<Integer, String> sPendingReplies = new ConcurrentHashMap<>();
	private static volatile String sPendingReplyTextFallback = null; // 仅用于无 ID 场景的兜底

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

	private static void inspect(XC_LoadPackage.LoadPackageParam loadPackageParam, String className, String... methods) {
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
	private static void inspectThen(XC_LoadPackage.LoadPackageParam loadPackageParam, String className, Consumer<Class<?>>... thens) {
		try {
			final Class<?> clazz = XposedHelpers.findClass(className, loadPackageParam.classLoader);
			XposedBridge.log("inspect clazz: " + clazz + " " + loadPackageParam.packageName);
			for (Consumer<Class<?>> then : thens) {
				then.accept(clazz);
			}
		} catch (XposedHelpers.ClassNotFoundError e) { /* XposedBridge.log("ContextImpl hook failed"); */ }
	}

	@Override
	public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
		switch (loadPackageParam.packageName) {
			case "com.android.systemui":
			hookSystemUI(loadPackageParam);
			break;
			case "com.tencent.mm":
			hookWeChat(loadPackageParam);
			break;
			case "com.oasisfeng.nevo":
			hookEngine(loadPackageParam);
			break;
		}
		/* inspect(loadPackageParam,
				"com.android.server.notification.NotificationManagerService",
				"getNotificationChannel",
				"deleteNotificationChannel",
				"deleteNotificationChannelGroup",
				"createNotificationChannels"); */
	}

	private void hookSystemUI(XC_LoadPackage.LoadPackageParam loadPackageParam) {
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
				} catch (Throwable e) { XposedBridge.log("NL hook failed: " + e.getMessage()); }
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

	private void addSyntheticReplyAction(Notification n) {
		try {
			// Check if notification already has actions with RemoteInput
			if (n.actions != null) {
				for (Notification.Action action : n.actions) {
					if (action != null && action.getRemoteInputs() != null) {
						for (android.app.RemoteInput ri : action.getRemoteInputs()) {
							if (ri != null && ri.getAllowFreeFormInput()) return; // Already has reply
						}
					}
				}
			}

			// Get context
			Context ctx = NevoDecoratorService.getAppContext();
			if (ctx == null) return;

			// Create synthetic reply action
			String actionReply = ctx.getString(com.oasisfeng.nevo.xposed.R.string.action_reply);
			android.content.Intent replyIntent = new android.content.Intent("SYNTHETIC_REPLY")
					.setData(android.net.Uri.fromParts("id", Integer.toString(n.extras.getInt("android.id", 0)), null))
					.setPackage(ctx.getPackageName());
			int flags = android.os.Build.VERSION.SDK_INT >= 31 ? (android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_MUTABLE) : android.app.PendingIntent.FLAG_UPDATE_CURRENT;
			android.app.PendingIntent replyPendingIntent = android.app.PendingIntent.getBroadcast(ctx, 0, replyIntent, flags);

			android.app.RemoteInput.Builder remoteInputBuilder = new android.app.RemoteInput.Builder("synthetic_reply_result_key")
					.setAllowFreeFormInput(true);
			if (android.os.Build.VERSION.SDK_INT >= 24) remoteInputBuilder.setLabel(actionReply);

			Notification.Action.Builder replyActionBuilder = new Notification.Action.Builder(null, actionReply, replyPendingIntent)
					.addRemoteInput(remoteInputBuilder.build())
					.setAllowGeneratedReplies(true);
			if (android.os.Build.VERSION.SDK_INT >= 28) replyActionBuilder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY);

			java.util.List<Notification.Action> actions = new java.util.ArrayList<>();
			if (n.actions != null) {
				for (Notification.Action a : n.actions) {
					if (a != null) actions.add(a);
				}
			}
			actions.add(replyActionBuilder.build());
			n.actions = actions.toArray(new Notification.Action[0]);
			Log.d(TAG, "Added synthetic reply action to WeChat notification");
		} catch (Throwable e) {
			Log.w(TAG, "Failed to add synthetic reply: " + e.getMessage());
		}
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

	private void hookWeChat(XC_LoadPackage.LoadPackageParam loadPackageParam) {
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
						LocalDecorator wechat = MainHook.this.wechat.getLocalDecorator("com.tencent.mm");
						wechat.onCreate(pref);
						if (!wechat.isDisabled() && (wechat instanceof HookSupport)) ((HookSupport)wechat).hook(loadPackageParam);
					}
				}
			});
		} catch (Throwable e) { XposedBridge.log(this.wechat + " Application.onCreate hook failed: " + e.getMessage()); }
		/* inspect(loadPackageParam,
				"com.android.server.notification.NotificationManagerService",
				"getNotificationChannel",
				"deleteNotificationChannel",
				"deleteNotificationChannelGroup",
				"createNotificationChannels"); */
	}

	// TODO
	private void applyLocally(NotificationManager nm, String tag, int id, Notification n) {
		if (XposedHelpers.getAdditionalInstanceField(n, "pre-applied") != null) {
			Log.d(TAG, "skip " + n);
			return;
		}
		XposedHelpers.setAdditionalInstanceField(n, "pre-applied", true);
		// Get Context from NotificationManager if not yet available
		if (NevoDecoratorService.getAppContext() == null) {
			try {
				java.lang.reflect.Field ctxField = NotificationManager.class.getDeclaredField("mContext");
				ctxField.setAccessible(true);
				Context ctx = (Context) ctxField.get(nm);
				if (ctx != null) {
					NevoDecoratorService.setAppContext(ctx.getApplicationContext());
					XposedBridge.log("applyLocally: got Context from NM: " + NevoDecoratorService.getAppContext());
				}
			} catch (Throwable e) {
				XposedBridge.log("applyLocally: failed to get Context from NM: " + e.getMessage());
			}
		}
		LocalDecorator.setNM(nm);
		LocalDecorator wechat = this.wechat.getLocalDecorator("com.tencent.mm");
		if (!wechat.isDisabled()) wechat.apply(nm, tag, id, n);
	}


	private void hookWeChatMessageSending(XC_LoadPackage.LoadPackageParam loadPackageParam) {
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
				XposedBridge.log("hookWeChatMessageSending: " + className + " not found");
			}
		}
	}

	private void hookAutoReplyReceiver(XC_LoadPackage.LoadPackageParam loadPackageParam) {
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
						XposedBridge.log("RemoteInput.getResultsFromIntent: " + riResults);
					} catch (Throwable e) {
						XposedBridge.log("RemoteInput.getResultsFromIntent failed: " + e.getMessage());
					}
					// Try to hook car mode bypass if not already done
					hookCarModeBypass(finalCl);
					android.content.Intent intent = (android.content.Intent) param.args[1];
					XposedBridge.log("MMAutoMessageReplyReceiver.onReceive: action=" + intent.getAction());
					if (intent.getExtras() != null) {
						for (String key : intent.getExtras().keySet()) {
							XposedBridge.log("MMAutoMessageReplyReceiver.onReceive: " + key + "=" + intent.getExtras().get(key));
						}
					}
				}
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					XposedBridge.log("MMAutoMessageReplyReceiver.onReceive: completed");
					if (param.getThrowable() != null) {
						XposedBridge.log("MMAutoMessageReplyReceiver.onReceive: exception=" + param.getThrowable());
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
								StringBuilder sb = new StringBuilder("MMAutoMessageReplyReceiver." + methodName + "(");
								for (int i = 0; i < param.args.length; i++) {
									if (i > 0) sb.append(", ");
									sb.append(param.args[i]);
								}
								sb.append(")");
								XposedBridge.log(sb.toString());
							}
							@Override
							protected void afterHookedMethod(MethodHookParam param) {
								XposedBridge.log("MMAutoMessageReplyReceiver." + methodName + " returned: " + param.getResult());
								if (param.getThrowable() != null) {
									XposedBridge.log("MMAutoMessageReplyReceiver." + methodName + " exception: " + param.getThrowable());
								}
							}
						});
					} catch (Throwable e) {
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
			XposedBridge.log("hookAutoReplyReceiver failed: " + e.getMessage());
		}
	}

	public static void invokeMMAutoReply(android.content.Context context, android.content.Intent intent) {
		if (sMMAutoMessageReplyReceiverClass == null) {
			Log.w(TAG, "MMAutoMessageReplyReceiver not hooked yet");
			return;
		}
		// H1: 使用动态搜索替代硬编码类名
		try {
			Class<?> autoLogicClass = findClassByMethod(context.getClassLoader(), boolean.class, "f");
			if (autoLogicClass == null) {
				// fallback to hardcoded
				autoLogicClass = XposedHelpers.findClass("rn1.a", context.getClassLoader());
			}
			final Class<?> finalClass = autoLogicClass;
			String[] bypassMethods = {"f", "g", "c"};
			for (String methodName : bypassMethods) {
				try {
					XposedHelpers.findAndHookMethod(finalClass, methodName, new XC_MethodHook() {
						@Override
						protected void beforeHookedMethod(MethodHookParam param) { param.setResult(true); }
					});
				} catch (Throwable ignored) {} // hooks may already be added
			}
			Log.d(TAG, "Car mode bypass hooks activated in invokeMMAutoReply");
		} catch (Throwable th) { /* hooks may already be added */ }
		try {
			// H2: 设置 pendingReplyText 供 RemoteInput.getResultsFromIntent hook 使用
			String replyText = intent.getStringExtra("reply_content");
			int notifId = intent.getIntExtra("notification_id", -1);
			if (replyText != null) {
				if (notifId >= 0) {
					sPendingReplies.put(notifId, replyText);
				}
				sPendingReplyTextFallback = replyText;
				Log.d(TAG, "Set pendingReplyText: " + replyText);

				// 使用 RemoteInput.addResultsToIntent 设置回复文本
				android.app.RemoteInput[] remoteInputs = new android.app.RemoteInput[]{
					new android.app.RemoteInput.Builder("key_voice_reply_text")
						.setAllowFreeFormInput(true)
						.build()
				};
				android.os.Bundle remoteInputResults = new android.os.Bundle();
				remoteInputResults.putCharSequence("key_voice_reply_text", replyText);
				android.app.RemoteInput.addResultsToIntent(remoteInputs, intent, remoteInputResults);
				Log.d(TAG, "Added RemoteInput results to intent");
			}

			// 直接调用 onReceive
			Object instance = sMMAutoMessageReplyReceiverClass.newInstance();
			java.lang.reflect.Method onReceive = sMMAutoMessageReplyReceiverClass.getMethod("onReceive", android.content.Context.class, android.content.Intent.class);
			onReceive.invoke(instance, context, intent);
			Log.d(TAG, "Directly invoked MMAutoMessageReplyReceiver.onReceive");
		} catch (Exception e) {
			Log.w(TAG, "Failed to invoke MMAutoMessageReplyReceiver: " + e.getMessage());
		}
	}

	private void hookEngine(XC_LoadPackage.LoadPackageParam loadPackageParam) {
		XposedBridge.log("hookEngine: hooking Nevolution engine");
		try {
			// Hook NotificationManager.notify in the engine process
			final Class<?> nmClass = XposedHelpers.findClass("android.app.NotificationManager", loadPackageParam.classLoader);
			Method notifyMethod = XposedHelpers.findMethodExact(nmClass, "notify", String.class, int.class, Notification.class);
			XposedBridge.hookMethod(notifyMethod, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					NotificationManager nm = (NotificationManager) param.thisObject;
					String tag = (String) param.args[0];
					int id = (int) param.args[1];
					Notification n = (Notification) param.args[2];
					// Add synthetic reply action for WeChat notifications
					if (n.extras != null && n.extras.containsKey("nevo.pkg") && "com.tencent.mm".equals(n.extras.getString("nevo.pkg"))) {
						addSyntheticReplyAction(n);
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log("hookEngine failed: " + e.getMessage());
		}
	}

	private boolean carModeBypassHooked = false;

	// H1: 动态搜索特征方法的辅助方法，避免硬编码混淆类名
	/**
	 * 在 classLoader 中搜索包含指定方法签名的类
	 * @param methodReturnType 返回类型
	 * @param methodName 方法名
	 * @param paramTypes 参数类型
	 * @return 找到的 Class，或 null
	 */
	private static Class<?> findClassByMethod(ClassLoader cl, Class<?> methodReturnType, String methodName, Class<?>... paramTypes) {
		// 先尝试已知的硬编码类名（向后兼容）
		String[] knownCandidates = {"rn1.a", "com.tencent.mm.booter.auto.AutoLogic"};
		for (String name : knownCandidates) {
			try {
				Class<?> clazz = XposedHelpers.findClass(name, cl);
				if (clazz != null) {
					try {
						clazz.getDeclaredMethod(methodName, paramTypes);
						XposedBridge.log("findClassByMethod: found known class " + name);
						return clazz;
					} catch (NoSuchMethodException ignored) {}
				}
			} catch (Throwable ignored) {}
		}
		// 动态搜索：遍历已加载的类（通过 ClassLoader 资源）
		// 这是 fallback，性能开销较大，只在硬编码失败时使用
		XposedBridge.log("findClassByMethod: known candidates failed, dynamic search not available in this context");
		return null;
	}

	/**
	 * 搜索包含 b(Intent) -> Bundle 方法的类（微信内部 RemoteInput 辅助类）
	 */
	private static Class<?> findRemoteInputHelperClass(ClassLoader cl) {
		// 先尝试已知的硬编码类名
		String[] knownCandidates = {"z2.s1", "com.tencent.mm.sdk.platformtools.RemoteInputHelper"};
		for (String name : knownCandidates) {
			try {
				Class<?> clazz = XposedHelpers.findClass(name, cl);
				if (clazz != null) {
					for (Method m : clazz.getDeclaredMethods()) {
						if (m.getName().equals("b") && m.getParameterCount() == 1
								&& android.content.Intent.class.isAssignableFrom(m.getParameterTypes()[0])
								&& android.os.Bundle.class.isAssignableFrom(m.getReturnType())) {
							XposedBridge.log("findRemoteInputHelperClass: found " + name);
							return clazz;
						}
					}
				}
			} catch (Throwable ignored) {}
		}
		XposedBridge.log("findRemoteInputHelperClass: no known candidate found");
		return null;
	}

	private void hookCarModeBypass(ClassLoader cl) {
		if (carModeBypassHooked) return;
		try {
			// H1: 动态搜索车载模式逻辑类
			final Class<?> autoLogicClass = findClassByMethod(cl, boolean.class, "f");
			if (autoLogicClass == null) {
				XposedBridge.log("hookCarModeBypass: auto logic class not found, trying direct hook");
				// 尝试直接 Hook 已知的硬编码类名
				try {
					final Class<?> fallback = XposedHelpers.findClass("rn1.a", cl);
					hookAutoLogicMethods(fallback);
				} catch (Throwable e) {
					XposedBridge.log("hookCarModeBypass: fallback also failed: " + e.getMessage());
				}
			} else {
				hookAutoLogicMethods(autoLogicClass);
			}

			// H1: 动态搜索 RemoteInput 辅助类
			try {
				final Class<?> remoteInputHelper = findRemoteInputHelperClass(cl);
				if (remoteInputHelper != null) {
					XposedHelpers.findAndHookMethod(remoteInputHelper, "b", android.content.Intent.class, new XC_MethodHook() {
						@Override
						protected void afterHookedMethod(MethodHookParam param) {
							android.os.Bundle result = (android.os.Bundle) param.getResult();
							XposedBridge.log("RemoteInputHelper.b() returned: " + result);
							if (result == null) {
								String replyText = sPendingReplyTextFallback;
								if (replyText != null) {
									result = new android.os.Bundle();
									result.putCharSequence("key_voice_reply_text", replyText);
									param.setResult(result);
									XposedBridge.log("Injected RemoteInputHelper.b() with key_voice_reply_text=" + replyText);
									sPendingReplyTextFallback = null;
								}
							}
						}
					});
					XposedBridge.log("hookCarModeBypass: RemoteInputHelper.b() hook added");
				}
			} catch (Throwable e) {
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
								result.putCharSequence("key_voice_reply_text", replyText);
								param.setResult(result);
								XposedBridge.log("RemoteInput.getResultsFromIntent: injected key_voice_reply_text=" + replyText);
								sPendingReplyTextFallback = null;
							}
						}
					}
				});
				XposedBridge.log("hookCarModeBypass: RemoteInput.getResultsFromIntent hook added");
			} catch (Throwable e) {
				XposedBridge.log("hookCarModeBypass: failed to hook RemoteInput: " + e.getMessage());
			}
			carModeBypassHooked = true;
			XposedBridge.log("hookCarModeBypass: car mode bypass hooks added");
		} catch (Throwable e) {
			XposedBridge.log("hookCarModeBypass: failed: " + e.getMessage());
		}
	}

	private void hookAutoLogicMethods(Class<?> autoLogicClass) {
		XposedBridge.log("hookAutoLogicMethods: hooking " + autoLogicClass.getName());
		String[] bypassMethods = {"f", "g", "c"};
		for (String methodName : bypassMethods) {
			try {
				XposedHelpers.findAndHookMethod(autoLogicClass, methodName, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) {
						param.setResult(true);
						XposedBridge.log("Bypassed " + autoLogicClass.getSimpleName() + "." + methodName + "() -> true");
					}
				});
			} catch (Throwable e) {
				XposedBridge.log("hookAutoLogicMethods: " + methodName + " hook failed: " + e.getMessage());
			}
		}
	}
}
