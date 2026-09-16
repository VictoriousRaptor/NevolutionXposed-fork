package com.oasisfeng.nevo.decorators.wechat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.util.Log;
import com.oasisfeng.nevo.xposed.BuildConfig;
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
		if (BuildConfig.DEBUG) Log.i("WeChatDecorator", "NX_IMAGE stage=" + stage + " " + details);
	}

	void install(Context context, ClassLoader loader) {
		String stage = "version";
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo("com.tencent.mm", 0);
			if (!"8.0.72".equals(info.versionName) || info.versionCode != 3085) {
				log("events_unavailable", "reason=unsupported_version"); return;
			}
			stage = "classes";
			Class<?> state = Class.forName("v65.z", false, loader);
			Class<?> simple = Class.forName("yh3.f", false, loader);
			Class<?> msg = Class.forName("com.tencent.mm.storage.f9", false, loader);
			Class<?> flow = Class.forName("b80.m", false, loader);
			stage = "state_accessor";
			Method stateGet = method(state, "d", Object.class, String.class);
			stage = "message_base";
			// Decompiled aliases such as f473340d exist only in JADX output; resolve the real obfuscated field.
			Field resolved = null;
			for (Class<?> current = simple; current != null && resolved == null; current = current.getSuperclass()) {
				try { resolved = current.getDeclaredField("d"); } catch (NoSuchFieldException ignored) {}
			}
			if (resolved == null || resolved.getType() != int.class) throw new IllegalStateException("Invalid simple-message base");
			final Field base = resolved;
			Field resolvedMsgSvrId = null;
			for (Class<?> current = msg; current != null && resolvedMsgSvrId == null; current = current.getSuperclass()) {
				try { resolvedMsgSvrId = current.getDeclaredField("field_msgSvrId"); }
				catch (NoSuchFieldException ignored) {}
			}
			if (resolvedMsgSvrId == null || resolvedMsgSvrId.getType() != long.class)
				throw new IllegalStateException("Invalid msgSvrId field");
			resolvedMsgSvrId.setAccessible(true);
			final Field msgSvrId = resolvedMsgSvrId;
			stage = "message_accessors";
			Method stringAt = method(simple, "getString", String.class, int.class);
			Method longAt = method(simple, "getLong", long.class, int.class);
			Method intAt = method(simple, "getInteger", int.class, int.class);
			stage = "message_methods";
			Method talker = method(msg, "O0", String.class);
			Method id = method(msg, "getMsgId", long.class);
			Method created = method(msg, "getCreateTime", long.class);
			Method type = method(msg, "getType", int.class);
			Method sender = method(msg, "C0", int.class);
			Method serverId = method(msg, "I0", long.class);
			stage = "path_methods";
			Method pathMethod = method(Class.forName("m90.b", false, loader), "oi", String.class, msg, String.class, boolean.class);
			realPath = method(Class.forName("com.tencent.mm.vfs.w6", false, loader), "i", String.class, String.class, boolean.class);
			stage = "pipeline_methods";
			Class<?> action = Class.forName("y65.b", false, loader);
			Method initial = method(flow, "l", action, state);
			Method remote = method(flow, "handleDataFromRemote", action, state, Class.forName("x01.e", false, loader));
			Method local = method(flow, "handleDataFromFile", action, state, Class.forName("p70.d", false, loader));
			stage = "hooks";
			XC_MethodHook pipeline = new XC_MethodHook() {
				@Override protected void afterHookedMethod(MethodHookParam param) {
					if (!available || param.hasThrowable()) return;
					try {
						Object data = stateGet.invoke(param.args[0], "key_msg_info");
						if (!simple.isInstance(data)) return;
						int offset = base.getInt(data);
						if (offset < 0 || offset > 256 || ((Number) intAt.invoke(data, offset + 9)).intValue() != 0
								|| ((Number) intAt.invoke(data, offset + 4)).intValue() != 3) return;
						List<ImageEventIndex.Path> paths = new ArrayList<>();
						for (String key : new String[]{"key_write_hd_thumb_path", "key_hd_thumb_path", "key_write_thumb_path", "key_thumb_path"}) {
							Object value = stateGet.invoke(param.args[0], key);
							if (BuildConfig.DEBUG) log("path_field", "field=" + key + " present=" + (value instanceof String)
									+ " empty=" + (value instanceof String && ((String) value).isEmpty()));
							if (value instanceof String) paths.add(new ImageEventIndex.Path((String) value,
									key.indexOf("hd_thumb") >= 0 ? ImagePreviewPolicy.QUALITY_HD : ImagePreviewPolicy.QUALITY_THUMBNAIL));
						}
						// Empirically millisecond-based in 8.0.72/3085, matching Notification.when.
						long createdMillis = ((Number) longAt.invoke(data, offset + 2)).longValue();
						index.putRanked((String) stringAt.invoke(data, offset + 3), ((Number) longAt.invoke(data, offset)).longValue(),
								0, createdMillis, paths, SystemClock.elapsedRealtime());
						log("event", "source=pipeline paths=" + paths.size() + " createdMs=" + createdMillis);
					} catch (Exception failure) { log("event_error", "type=" + failure.getClass().getSimpleName()); }
				}
			};
			XposedBridge.hookMethod(initial, pipeline);
			XposedBridge.hookMethod(remote, pipeline);
			XposedBridge.hookMethod(local, pipeline);
			XposedBridge.hookMethod(serverId, new XC_MethodHook() {
				@Override protected void afterHookedMethod(MethodHookParam param) {
					if (!available || param.hasThrowable() || !(param.getResult() instanceof Number)) return;
					try {
						Object data = param.thisObject;
						long value = ((Number) param.getResult()).longValue();
						if (value <= 0 || ((Number) type.invoke(data)).intValue() != 3
								|| ((Number) sender.invoke(data)).intValue() != 0) return;
						index.putIdentity((String) talker.invoke(data), ((Number) id.invoke(data)).longValue(),
								value, ((Number) created.invoke(data)).longValue(), SystemClock.elapsedRealtime());
						log("identity", "serverId=true");
					} catch (Exception failure) { log("identity_error", "type=" + failure.getClass().getSimpleName()); }
				}
			});
			XposedBridge.hookMethod(pathMethod, new XC_MethodHook() {
				@Override protected void afterHookedMethod(MethodHookParam param) {
					if (!available || param.hasThrowable() || param.args[0] == null || !(param.getResult() instanceof String)) return;
					try {
						Object data = param.args[0];
						if (((Number) type.invoke(data)).intValue() != 3 || ((Number) sender.invoke(data)).intValue() != 0) return;
						index.put((String) talker.invoke(data), ((Number) id.invoke(data)).longValue(),
								((Number) id.invoke(data)).longValue() > 0 ? msgSvrId.getLong(data) : 0,
								((Number) created.invoke(data)).longValue(), Arrays.asList((String) param.getResult()), SystemClock.elapsedRealtime());
						log("event", "source=message_path serverId=" + (msgSvrId.getLong(data) > 0));
					} catch (Exception failure) { log("event_error", "type=" + failure.getClass().getSimpleName()); }
				}
			});
			available = true;
			log("events_ready", "profile=8.0.72/3085 revision=image-events-9 scans=0 base="
					+ base.getDeclaringClass().getSimpleName() + "." + base.getName());
		} catch (Throwable failure) {
			XposedBridge.rethrowFrameworkError(failure);
			available = false;
			log("events_unavailable", "stage=" + stage + " type=" + failure.getClass().getSimpleName());
		}
	}

	boolean isAvailable() { return available; }
	String resolvePath(String path) throws Exception { return (String) realPath.invoke(null, path, false); }

	private static Method method(Class<?> owner, String name, Class<?> returns, Class<?>... arguments) {
		Method method = XposedHelpers.findMethodExact(owner, name, arguments);
		if (method.getReturnType() != returns) throw new IllegalStateException("Unexpected return type: " + name);
		return method;
	}
}
