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
import java.lang.reflect.Modifier;
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

	void install(Context context, ClassLoader loader, WeChatProfileSelector selector, WeChatProfileSelector.Cache cache) {
		if (available) return;
		String stage = "version";
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo("com.tencent.mm", 0);
			WeChatProfileSelector.Selection<WeChatImageProfile> choice = selector.select("image_events",
					WeChatProfileSelector.BuildId.fromPackageInfo(info), WeChatImageProfile.CANDIDATES,
					p -> resolve(p, name -> Class.forName(name, false, loader)) != null, cache);
			WeChatImageProfile profile = choice.profile;
			if (profile == null) {
				log("events_unavailable", "reason=" + choice.kind.name().toLowerCase()); return;
			}
			stage = "signature";
			Resolved resolved = resolve(profile, name -> Class.forName(name, false, loader));
            stage = "hooks";
            if (!installResolved(resolved, XposedBridge::hookMethod)) {
                log("events_unavailable", "stage=hooks type=" + lastHookFailureType);
                return;
            }
			log("events_ready", "profile=" + profile.label + " match=" + choice.kind.name().toLowerCase() + " revision=image-events-11 scans=0 base="
					+ resolved.base.getDeclaringClass().getSimpleName() + "." + resolved.base.getName());
		} catch (Throwable failure) {
			XposedBridge.rethrowFrameworkError(failure);
			available = false;
			log("events_unavailable", "stage=" + stage + " type=" + failure.getClass().getSimpleName());
		}
	}

    private static final class HookState { volatile boolean ready; }
    private String lastHookFailureType;
    boolean installResolved(Resolved resolved, Hooker hooker) {
        if (available) return true;
        HookState state = new HookState();
        try {
			Class<?> simple = resolved.simple;
			Field base = resolved.base;
			Field msgSvrId = resolved.msgSvrId;
			Method stateGet = resolved.stateGet;
			Method stringAt = resolved.stringAt;
			Method longAt = resolved.longAt;
			Method intAt = resolved.intAt;
			Method talker = resolved.talker;
			Method id = resolved.id;
			Method created = resolved.created;
			Method type = resolved.type;
			Method sender = resolved.sender;
			Method serverId = resolved.serverId;
			Method pathMethod = resolved.pathMethod;
			Method realPath = resolved.realPath;
			Method initial = resolved.initial;
			Method remote = resolved.remote;
			Method local = resolved.local;
			this.realPath = realPath;

			XC_MethodHook pipeline = new XC_MethodHook() {
				@Override protected void afterHookedMethod(MethodHookParam param) {
					if (!state.ready || param.hasThrowable()) return;
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
						// simplemsginfo createTime is copied from message.getCreateTime(), in milliseconds.
						long createdMillis = ((Number) longAt.invoke(data, offset + 2)).longValue();
						index.putRanked((String) stringAt.invoke(data, offset + 3), ((Number) longAt.invoke(data, offset)).longValue(),
								0, createdMillis, paths, SystemClock.elapsedRealtime());
						log("event", "source=pipeline paths=" + paths.size() + " createdMs=" + createdMillis);
					} catch (Exception failure) { log("event_error", "type=" + failure.getClass().getSimpleName()); }
				}
			};
			hooker.hook(initial, pipeline);
			hooker.hook(remote, pipeline);
			hooker.hook(local, pipeline);
			hooker.hook(serverId, new XC_MethodHook() {
				@Override protected void afterHookedMethod(MethodHookParam param) {
					if (!state.ready || param.hasThrowable() || !(param.getResult() instanceof Number)) return;
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
			hooker.hook(pathMethod, new XC_MethodHook() {
				@Override protected void afterHookedMethod(MethodHookParam param) {
					if (!state.ready || param.hasThrowable() || param.args[0] == null || !(param.getResult() instanceof String)) return;
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
			state.ready = true;
			available = true;
			return true;

        } catch (Throwable failure) {
            state.ready = false;
            available = false;
            XposedBridge.rethrowFrameworkError(failure);
            lastHookFailureType = failure.getClass().getSimpleName();
            return false;
        }
    }

	boolean isAvailable() { return available; }
	String resolvePath(String path) throws Exception { return (String) realPath.invoke(null, path, false); }

	interface NameResolver { Class<?> find(String name) throws ClassNotFoundException; }
	interface Hooker { void hook(Method method, XC_MethodHook callback) throws Throwable; }
	static final class Resolved {
		final Class<?> simple;
		final Field base,msgSvrId;
		final Method stateGet,stringAt,longAt,intAt,talker,id,created,type,sender,serverId,pathMethod,realPath,initial,remote,local;
		Resolved(Class<?> simple, Field base, Field msgSvrId, Method stateGet, Method stringAt, Method longAt, Method intAt, Method talker, Method id, Method created, Method type, Method sender, Method serverId, Method pathMethod, Method realPath, Method initial, Method remote, Method local) {
			this.simple = simple;
			this.base = base;
			this.msgSvrId = msgSvrId;
			this.stateGet = stateGet;
			this.stringAt = stringAt;
			this.longAt = longAt;
			this.intAt = intAt;
			this.talker = talker;
			this.id = id;
			this.created = created;
			this.type = type;
			this.sender = sender;
			this.serverId = serverId;
			this.pathMethod = pathMethod;
			this.realPath = realPath;
			this.initial = initial;
			this.remote = remote;
			this.local = local;
		}
	}
	static Resolved resolve(WeChatImageProfile profile, NameResolver resolver) throws ReflectiveOperationException {

			Class<?> state = resolver.find(profile.state);
			Class<?> simple = resolver.find(profile.simple);
			Class<?> msg = resolver.find(profile.message);
			Class<?> flow = resolver.find(profile.flow);
			Method stateGet = method(state, profile.stateGet, Object.class, String.class);
			// Decompiled aliases such as f473340d exist only in JADX output; resolve the real obfuscated field.
			Field resolved = null;
			for (Class<?> current = simple; current != null && resolved == null; current = current.getSuperclass()) {
				try { resolved = current.getDeclaredField("d"); } catch (NoSuchFieldException ignored) {}
			}
			if (resolved == null || (resolved.getType() != int.class || Modifier.isStatic(resolved.getModifiers()))) throw new IllegalStateException("Invalid simple-message base");
			resolved.setAccessible(true);
			final Field base = resolved;
			Field resolvedMsgSvrId = null;
			for (Class<?> current = msg; current != null && resolvedMsgSvrId == null; current = current.getSuperclass()) {
				try { resolvedMsgSvrId = current.getDeclaredField("field_msgSvrId"); }
				catch (NoSuchFieldException ignored) {}
			}
			if (resolvedMsgSvrId == null || (resolvedMsgSvrId.getType() != long.class || Modifier.isStatic(resolvedMsgSvrId.getModifiers())))
				throw new IllegalStateException("Invalid msgSvrId field");
			resolvedMsgSvrId.setAccessible(true);
			final Field msgSvrId = resolvedMsgSvrId;
			Method stringAt = method(simple, "getString", String.class, int.class);
			Method longAt = method(simple, "getLong", long.class, int.class);
			Method intAt = method(simple, "getInteger", int.class, int.class);
			Method talker = method(msg, profile.talker, String.class);
			Method id = method(msg, "getMsgId", long.class);
			Method created = method(msg, "getCreateTime", long.class);
			Method type = method(msg, "getType", int.class);
			Method sender = method(msg, profile.sender, int.class);
			Method serverId = method(msg, profile.serverId, long.class);
			Method pathMethod = XposedHelpers.findMethodExact(resolver.find(profile.pathOwner), profile.pathMethod, msg, String.class, boolean.class);
            if (pathMethod.getReturnType() != String.class || Modifier.isAbstract(pathMethod.getModifiers())
                    || (!profile.label.startsWith("8.0.77/") && Modifier.isStatic(pathMethod.getModifiers())))
                throw new IllegalStateException("Invalid message path signature");
			Method realPath = staticMethod(resolver.find(profile.vfs), "i", String.class, String.class, boolean.class);
			Class<?> action = resolver.find(profile.result);
			Method initial = method(flow, "l", action, state);
			Method remote = method(flow, "handleDataFromRemote", action, state, resolver.find(profile.remote));
			Method local = method(flow, "handleDataFromFile", action, state, resolver.find(profile.local));

		return new Resolved(simple,base,msgSvrId,stateGet,stringAt,longAt,intAt,talker,id,created,type,sender,serverId,pathMethod,realPath,initial,remote,local);
	}

    private static Method staticMethod(Class<?> owner, String name, Class<?> returns, Class<?>... arguments) {
        Method result = XposedHelpers.findMethodExact(owner, name, arguments);
        if (!Modifier.isStatic(result.getModifiers()) || result.getReturnType() != returns)
            throw new IllegalStateException("Invalid static method: " + name);
        return result;
    }
	private static Method method(Class<?> owner, String name, Class<?> returns, Class<?>... arguments) {
		Method method = XposedHelpers.findMethodExact(owner, name, arguments);
		if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers()) || method.getReturnType() != returns) throw new IllegalStateException("Unexpected return type: " + name);
		return method;
	}
}
