package com.oasisfeng.nevo.decorators.wechat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oasisfeng.nevo.xposed.compat.XposedHelpers;

/**
 * Version specific WeChat reply mappings.
 *
 * <p>WeChat obfuscates the car-mode helper class between releases (8.0.76 used
 * {@code rn1.a}, 8.0.72 uses {@code dn1.a}), so a class name alone is never
 * trusted. A profile is usable only after its receiver, RemoteInput helper and
 * car-mode gates have all been validated against their full signatures at
 * runtime. When validation fails the module must not offer a synthetic reply
 * action, because the dispatch would silently do nothing.
 *
 * <p>Verified against the Google Play WeChat 8.0.72 APK (versionCode 3085):
 * <pre>
 * receiver : com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver
 * helper   : z2.s1.b(android.content.Intent) : android.os.Bundle
 * gates    : dn1.a.f(), dn1.a.h(), dn1.a.c()  (all static boolean)
 * result   : key_voice_reply_text, username extra key_username
 * actions  : com.tencent.mm.permission.MM_AUTO_REPLY_MESSAGE / ...MM_AUTO_HEARD_MESSAGE
 * </pre>
 */
public final class WeChatReplyProfile {

	public static final String REPLY_ACTION = "com.tencent.mm.permission.MM_AUTO_REPLY_MESSAGE";
	public static final String HEARD_ACTION = "com.tencent.mm.permission.MM_AUTO_HEARD_MESSAGE";
	public static final String RESULT_KEY = "key_voice_reply_text";
	public static final String USERNAME_EXTRA = "key_username";

	private static final String RECEIVER_CLASS = "com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver";

	private static final WeChatReplyProfile RELEASE_8_0_72 = new WeChatReplyProfile(
			"wechat-8.0.72",
			new String[] { RECEIVER_CLASS },
			new String[] { "z2.s1" },
			new String[] { "dn1.a" },
			new String[] { "f", "h", "c" });

	/** Mapping kept for WeChat 8.0.76 and older builds with the same layout. */
	private static final WeChatReplyProfile LEGACY = new WeChatReplyProfile(
			"wechat-legacy",
			new String[] { RECEIVER_CLASS },
			new String[] { "z2.s1", "com.tencent.mm.sdk.platformtools.RemoteInputHelper" },
			new String[] { "rn1.a", "com.tencent.mm.booter.auto.AutoLogic" },
			new String[] { "f", "g", "c" });

	public final String label;
	private final String[] receiverCandidates;
	private final String[] helperCandidates;
	private final String[] gateCandidates;
	private final String[] gateMethods;

	private WeChatReplyProfile(String label, String[] receiverCandidates, String[] helperCandidates,
			String[] gateCandidates, String[] gateMethods) {
		this.label = label;
		this.receiverCandidates = receiverCandidates;
		this.helperCandidates = helperCandidates;
		this.gateCandidates = gateCandidates;
		this.gateMethods = gateMethods;
	}

	public static WeChatReplyProfile forPackage(String versionName, long versionCode) {
		if ("8.0.72".equals(versionName) || versionCode == 3085L) return RELEASE_8_0_72;
		return LEGACY;
	}

	/** Result of validating one profile inside the WeChat process. */
	public static final class Resolved {
		public final WeChatReplyProfile profile;
		public final Class<?> receiverClass;
		public final Class<?> helperClass;
		public final Class<?> gateClass;
		public final List<String> gateMethods;
		public final String versionName;
		public final long versionCode;

		Resolved(WeChatReplyProfile profile, String versionName, long versionCode,
				Class<?> receiverClass, Class<?> helperClass, Class<?> gateClass, List<String> gateMethods) {
			this.profile = profile;
			this.versionName = versionName;
			this.versionCode = versionCode;
			this.receiverClass = receiverClass;
			this.helperClass = helperClass;
			this.gateClass = gateClass;
			this.gateMethods = gateMethods == null ? Collections.<String>emptyList() : gateMethods;
		}

		public boolean isUsable() {
			return receiverClass != null && gateClass != null && !gateMethods.isEmpty();
		}

		public String describe() {
			return "version=" + versionName + "/" + versionCode
					+ " profile=" + profile.label
					+ " receiver=" + (receiverClass == null ? "none" : receiverClass.getName())
					+ " helper=" + (helperClass == null ? "none" : helperClass.getName())
					+ " gates=" + (gateClass == null ? "none" : gateClass.getName() + gateMethods)
					+ " usable=" + isUsable();
		}
	}

	public Resolved resolve(ClassLoader cl, String versionName, long versionCode) {
		final Class<?> receiver = findReceiver(cl);
		final Class<?> helper = findHelper(cl);
		final Class<?> gate = findGateClass(cl);
		final List<String> methods = new ArrayList<>();
		if (gate != null) {
			for (String name : gateMethods) {
				if (hasStaticBooleanNoArg(gate, name)) methods.add(name);
			}
		}
		return new Resolved(this, versionName, versionCode, receiver, helper, gate, methods);
	}

	private Class<?> findReceiver(ClassLoader cl) {
		for (String name : receiverCandidates) {
			try {
				final Class<?> clazz = XposedHelpers.findClass(name, cl);
				if (!BroadcastReceiver.class.isAssignableFrom(clazz)) continue;
				clazz.getMethod("onReceive", Context.class, Intent.class);
				return clazz;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private Class<?> findHelper(ClassLoader cl) {
		for (String name : helperCandidates) {
			try {
				final Class<?> clazz = XposedHelpers.findClass(name, cl);
				final Method method = clazz.getDeclaredMethod("b", Intent.class);
				if (!Modifier.isStatic(method.getModifiers())) continue;
				if (!Bundle.class.isAssignableFrom(method.getReturnType())) continue;
				return clazz;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private Class<?> findGateClass(ClassLoader cl) {
		for (String name : gateCandidates) {
			try {
				final Class<?> clazz = XposedHelpers.findClass(name, cl);
				boolean complete = true;
				for (String method : gateMethods) {
					if (!hasStaticBooleanNoArg(clazz, method)) {
						complete = false;
						break;
					}
				}
				if (complete) return clazz;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static boolean hasStaticBooleanNoArg(Class<?> clazz, String name) {
		try {
			final Method method = clazz.getDeclaredMethod(name);
			return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class;
		} catch (Throwable ignored) {
			return false;
		}
	}

}
