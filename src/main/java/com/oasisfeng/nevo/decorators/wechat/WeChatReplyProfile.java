package com.oasisfeng.nevo.decorators.wechat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
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
 *
 * <p>Verified against the China WeChat 8.0.78 APK (versionCode 3180): the receiver and
 * the RemoteInput helper kept their names, but the car-mode gates moved to
 * {@code bs1.a.c()} (config flag), {@code bs1.a.g()} (car UI mode + AOAP device) and
 * {@code bs1.a.b()} (Android Auto package check), all static boolean.
 */
public final class WeChatReplyProfile {

	public static final String REPLY_ACTION = "com.tencent.mm.permission.MM_AUTO_REPLY_MESSAGE";
	public static final String HEARD_ACTION = "com.tencent.mm.permission.MM_AUTO_HEARD_MESSAGE";
	public static final String RESULT_KEY = "key_voice_reply_text";
	public static final String USERNAME_EXTRA = "key_username";

	private static final String RECEIVER_CLASS = "com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver";

	/** Device APK-verified WeChat 8.0.79/3182; do not borrow another build's layout. */
	private static final WeChatReplyProfile RELEASE_8_0_79_3182 = new WeChatReplyProfile(
			"wechat-8.0.79/3182", new String[] { RECEIVER_CLASS }, new String[] { "z2.s1" },
			new String[] { "lt1.a" }, new String[] { "c", "d", "b" });

	/** Device APK-verified 8.0.77/3141; distinct from the supplied 3160 build. */
	private static final WeChatReplyProfile RELEASE_8_0_77_3141 = new WeChatReplyProfile(
			"wechat-8.0.77/3141", new String[] { RECEIVER_CLASS }, new String[] { "z2.s1" },
			new String[] { "bs1.a" }, new String[] { "e", "g", "c" });

	/** APK-verified WeChat 8.0.77/3160: config, car mode, Android Auto package gates. */
	private static final WeChatReplyProfile RELEASE_8_0_77 = new WeChatReplyProfile(
			"wechat-8.0.77",
			new String[] { RECEIVER_CLASS },
			new String[] { "z2.s1" },
			new String[] { "lq1.a" },
			new String[] { "e", "h", "c" });

	private static final WeChatReplyProfile RELEASE_8_0_78 = new WeChatReplyProfile(
			"wechat-8.0.78",
			new String[] { RECEIVER_CLASS },
			new String[] { "z2.s1" },
			new String[] { "bs1.a" },
			new String[] { "c", "g", "b" });

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

	/** Only APK-verified profiles participate in same-name experimental matching. */
	public static List<WeChatProfileSelector.Candidate<WeChatReplyProfile>> candidates() {
		return Arrays.asList(
				new WeChatProfileSelector.Candidate<>(RELEASE_8_0_79_3182.label, "8.0.79", 3182L, RELEASE_8_0_79_3182),
				new WeChatProfileSelector.Candidate<>(RELEASE_8_0_77_3141.label, "8.0.77", 3141L, RELEASE_8_0_77_3141),
				new WeChatProfileSelector.Candidate<>(RELEASE_8_0_77.label, "8.0.77", 3160L, RELEASE_8_0_77),
				new WeChatProfileSelector.Candidate<>(RELEASE_8_0_78.label, "8.0.78", 3180L, RELEASE_8_0_78),
				new WeChatProfileSelector.Candidate<>(RELEASE_8_0_72.label, "8.0.72", 3085L, RELEASE_8_0_72));
	}

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
		if ("8.0.79".equals(versionName) && versionCode == 3182L) return RELEASE_8_0_79_3182;
		if ("8.0.79".equals(versionName) || versionCode == 3182L) return LEGACY;
		if ("8.0.77".equals(versionName) && versionCode == 3141L) return RELEASE_8_0_77_3141;
		if ("8.0.77".equals(versionName) && versionCode == 3160L) return RELEASE_8_0_77;
		// Other 8.0.77 builds must not reuse a different build's obfuscated layout.
		if ("8.0.77".equals(versionName)) return LEGACY;
		if ("8.0.78".equals(versionName) || versionCode == 3180L) return RELEASE_8_0_78;
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
			return receiverClass != null && helperClass != null && gateClass != null
					&& gateMethods.size() == profile.gateMethods.length;
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

	/** Keeps every gate callback inert until the complete batch is installed. */
	public static final class GateBatch {
		private volatile boolean ready;
		private volatile long generation;
		private volatile Throwable failure;
		public boolean ready() { return ready; }
		public Throwable failure() { return failure; }
		public synchronized boolean install(List<String> names, GateInstaller installer) {
			if (ready) return true;
			final long attempt = ++generation;
			final List<AutoCloseable> handles = new ArrayList<>();
			try {
				if (names == null || names.isEmpty()) return false;
				for (String name : names) handles.add(installer.install(name, new GateCallback(this, attempt)));
				ready = true;
				failure = null;
				return true;
			} catch (Throwable error) {
				ready = false;
				failure = error;
				for (int i = handles.size() - 1; i >= 0; i--) {
					try { handles.get(i).close(); } catch (Exception ignored) { /* Inert callback remains safe. */ }
				}
				return false;
			}
		}
	}
	public interface GateInstaller { AutoCloseable install(String name, GateCallback callback) throws Throwable; }
	public static final class GateCallback {
		private final GateBatch batch;
		private final long generation;
		GateCallback(GateBatch batch, long generation) { this.batch = batch; this.generation = generation; }
		public boolean active() { return batch.ready() && batch.generation == generation; }
	}

	public Resolved resolve(ClassLoader cl, String versionName, long versionCode) {
		return resolve(name -> XposedHelpers.findClass(name, cl), versionName, versionCode);
	}

	/** Allows a synthetic class map to exercise the same descriptor checks in host tests. */
	interface ClassResolver { Class<?> find(String name) throws Throwable; }
	Resolved resolve(ClassResolver classes, String versionName, long versionCode) {
		final Class<?> receiver = findReceiver(classes);
		final Class<?> helper = findHelper(classes);
		final Class<?> gate = findGateClass(classes);
		final List<String> methods = new ArrayList<>();
		if (gate != null) {
			for (String name : gateMethods) {
				if (hasStaticBooleanNoArg(gate, name)) methods.add(name);
			}
		}
		return new Resolved(this, versionName, versionCode, receiver, helper, gate, methods);
	}

	private Class<?> findReceiver(ClassResolver classes) {
		for (String name : receiverCandidates) {
			try {
				final Class<?> clazz = classes.find(name);
				if (!BroadcastReceiver.class.isAssignableFrom(clazz)
						|| !Modifier.isPublic(clazz.getModifiers()) || Modifier.isAbstract(clazz.getModifiers())
						|| !Modifier.isPublic(clazz.getDeclaredConstructor().getModifiers())) continue;
				Method onReceive = clazz.getMethod("onReceive", Context.class, Intent.class);
				if (onReceive.getReturnType() != void.class || Modifier.isStatic(onReceive.getModifiers())
						|| Modifier.isAbstract(onReceive.getModifiers())) continue;
				return clazz;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private Class<?> findHelper(ClassResolver classes) {
		for (String name : helperCandidates) {
			try {
				final Class<?> clazz = classes.find(name);
				if (!hasHelperSignature(clazz)) continue;
				return clazz;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	static boolean hasHelperSignature(Class<?> clazz) {
		try {
			final Method method = clazz.getDeclaredMethod("b", Intent.class);
			return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Bundle.class;
		} catch (NoSuchMethodException ignored) { return false; }
	}

	private Class<?> findGateClass(ClassResolver classes) {
		for (String name : gateCandidates) {
			try {
				final Class<?> clazz = classes.find(name);
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
