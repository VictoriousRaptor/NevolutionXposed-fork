package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.util.Log;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;
import com.oasisfeng.nevo.xposed.compat.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Version-checked adapters for WeChat's thumbnail pipeline; never invokes a download. */
final class WeChatImageEvents {
	final ImageEventIndex index = new ImageEventIndex();
	private volatile boolean available;
	private Method realPath;

	static void log(String stage, String details) {
		Log.i("WeChatDecorator", "NX_IMAGE stage=" + stage + " " + details);
	}

	void install(Context context, ClassLoader loader) {
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo("com.tencent.mm", 0);
			if (!"8.0.72".equals(info.versionName) || info.versionCode != 3085) {
				log("events_unavailable", "reason=unsupported_version"); return;
			}
			Class<?> state = Class.forName("v65.z", false, loader);
			Class<?> simple = Class.forName("yh3.f", false, loader);
			Class<?> msg = Class.forName("com.tencent.mm.storage.f9", false, loader);
			Class<?> flow = Class.forName("b80.m", false, loader);
			Method stateGet = method(state, "d", Object.class, String.class);
			Field base = simple.getField("f473340d");
			if (base.getType() != int.class) throw new IllegalStateException("Invalid simple-message base");
			Method stringAt = method(simple, "getString", String.class, int.class);
			Method longAt = method(simple, "getLong", long.class, int.class);
			Method intAt = method(simple, "getInteger", int.class, int.class);
			Method talker = method(msg, "O0", String.class);
			Method id = method(msg, "getMsgId", long.class);
			Method created = method(msg, "getCreateTime", long.class);
			Method type = method(msg, "getType", int.class);
			Method sender = method(msg, "C0", int.class);
			Method pathMethod = method(Class.forName("m90.b", false, loader), "oi", String.class, msg, String.class, boolean.class);
			realPath = method(Class.forName("com.tencent.mm.vfs.w6", false, loader), "i", String.class, String.class, boolean.class);
			Class<?> action = Class.forName("y65.b", false, loader);
			Method initial = method(flow, "l", action, state);
			Method remote = method(flow, "handleDataFromRemote", action, state, Class.forName("x01.e", false, loader));
			Method local = method(flow, "handleDataFromFile", action, state, Class.forName("p70.d", false, loader));
			XC_MethodHook pipeline = new XC_MethodHook() {
				@Override protected void afterHookedMethod(MethodHookParam param) {
					if (!available || param.hasThrowable()) return;
					try {
						Object data = stateGet.invoke(param.args[0], "key_msg_info");
						if (!simple.isInstance(data)) return;
						int offset = base.getInt(data);
						if (offset < 0 || offset > 256 || ((Number) intAt.invoke(data, offset + 9)).intValue() != 0
								|| ((Number) intAt.invoke(data, offset + 4)).intValue() != 3) return;
						List<String> paths = new ArrayList<>();
						for (String key : new String[]{"key_write_thumb_path", "key_thumb_path", "key_write_hd_thumb_path", "key_hd_thumb_path"}) {
							Object value = stateGet.invoke(param.args[0], key);
							if (value instanceof String) paths.add((String) value);
						}
						index.put((String) stringAt.invoke(data, offset + 3), ((Number) longAt.invoke(data, offset)).longValue(),
								((Number) longAt.invoke(data, offset + 2)).longValue(), paths, SystemClock.elapsedRealtime());
						log("event", "source=pipeline paths=" + paths.size());
					} catch (Exception failure) { log("event_error", "type=" + failure.getClass().getSimpleName()); }
				}
			};
			XposedBridge.hookMethod(initial, pipeline);
			XposedBridge.hookMethod(remote, pipeline);
			XposedBridge.hookMethod(local, pipeline);
			XposedBridge.hookMethod(pathMethod, new XC_MethodHook() {
				@Override protected void afterHookedMethod(MethodHookParam param) {
					if (!available || param.hasThrowable() || param.args[0] == null || !(param.getResult() instanceof String)) return;
					try {
						Object data = param.args[0];
						if (((Number) type.invoke(data)).intValue() != 3 || ((Number) sender.invoke(data)).intValue() != 0) return;
						index.put((String) talker.invoke(data), ((Number) id.invoke(data)).longValue(),
								((Number) created.invoke(data)).longValue(), Arrays.asList((String) param.getResult()), SystemClock.elapsedRealtime());
						log("event", "source=message_path");
					} catch (Exception failure) { log("event_error", "type=" + failure.getClass().getSimpleName()); }
				}
			});
			available = true;
			log("events_ready", "profile=8.0.72/3085 revision=image-events-1 scans=0");
		} catch (Throwable failure) {
			XposedBridge.rethrowFrameworkError(failure);
			available = false;
			log("events_unavailable", "type=" + failure.getClass().getSimpleName());
		}
	}

	boolean isAvailable() { return available; }
	String resolvePath(String path) throws Exception { return (String) realPath.invoke(null, path, false); }

	static String notificationTalker(Notification notification) {
		List<PendingIntent> intents = new ArrayList<>();
		intents.add(notification.contentIntent);
		Notification.CarExtender.UnreadConversation conversation = new Notification.CarExtender(notification).getUnreadConversation();
		if (conversation != null) intents.add(conversation.getReplyPendingIntent());
		for (PendingIntent pending : intents) {
			if (pending == null || !"com.tencent.mm".equals(pending.getCreatorPackage())) continue;
			try {
				// Read the existing intent only; never send it to discover a username.
				Method getter = PendingIntent.class.getDeclaredMethod("getIntent");
				getter.setAccessible(true);
				Intent intent = (Intent) getter.invoke(pending);
				if (intent == null) continue;
				for (String key : new String[]{"key_username", "Chat_User"}) {
					String value = intent.getStringExtra(key);
					if (value != null && !value.isEmpty()) return value;
				}
			} catch (Exception ignored) {}
		}
		return null;
	}

	private static Method method(Class<?> owner, String name, Class<?> returns, Class<?>... arguments) {
		Method method = XposedHelpers.findMethodExact(owner, name, arguments);
		if (method.getReturnType() != returns) throw new IllegalStateException("Unexpected return type: " + name);
		return method;
	}
}
