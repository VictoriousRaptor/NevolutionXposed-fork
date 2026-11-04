package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.os.Bundle;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the version -> profile mapping and the descriptors of the verified profiles.
 *
 * <p>The profile is the only guard that keeps an unusable WeChat build from exposing a
 * reply action that cannot dispatch, so a mapping or descriptor regression here silently
 * breaks the notification reply button on a supported version.
 *
 * <p>Adding a profile for a new WeChat version means adding its mapping assertion here,
 * see docs/wechat-version-adaptation-playbook.md.
 */
public class WeChatReplyProfileTest {

	@Test public void mapsKnownVersionsToTheirProfiles() {
		assertEquals("wechat-8.0.79/3182", WeChatReplyProfile.forPackage("8.0.79", 3182L).label);
		assertEquals("wechat-8.0.79/3183", WeChatReplyProfile.forPackage("8.0.79", 3183L).label);
		assertEquals("wechat-8.0.79/3200", WeChatReplyProfile.forPackage("8.0.79", 3200L).label);
		assertEquals("wechat-8.0.78", WeChatReplyProfile.forPackage("8.0.78", 3180L).label);
		assertEquals("wechat-8.0.77", WeChatReplyProfile.forPackage("8.0.77", 3160L).label);
		assertEquals("wechat-8.0.72", WeChatReplyProfile.forPackage("8.0.72", 3085L).label);
		assertEquals("wechat-8.0.72/3100", WeChatReplyProfile.forPackage("8.0.72", 3100L).label);
		assertEquals("wechat-legacy", WeChatReplyProfile.forPackage("8.0.76", 0L).label);
	}

	@Test public void mapsByVersionCodeWhenTheVersionNameDiffers() {
		assertEquals("wechat-8.0.78", WeChatReplyProfile.forPackage("8.0.78.dev", 3180L).label);
		assertEquals("wechat-legacy", WeChatReplyProfile.forPackage("8.0.77.dev", 3160L).label);
		assertEquals("wechat-8.0.72", WeChatReplyProfile.forPackage("unknown", 3085L).label);
	}

	@Test public void refusesUnknownVersionsWithTheLegacyProfile() {
		assertEquals("wechat-legacy", WeChatReplyProfile.forPackage("0.0.0", 1L).label);
		assertEquals("wechat-legacy", WeChatReplyProfile.forPackage("", 0L).label);
	}

	@Test public void pinsWeChat8078Descriptors() throws Exception {
		final WeChatReplyProfile profile = WeChatReplyProfile.forPackage("8.0.78", 3180L);
		assertEquals(Arrays.asList("z2.s1"), candidates(profile, "helperCandidates"));
		assertEquals(Arrays.asList("bs1.a"), candidates(profile, "gateCandidates"));
		assertEquals(Arrays.asList("c", "g", "b"), candidates(profile, "gateMethods"));
	}

	@Test public void requiresExactWeChat8079Build() throws Exception {
		final WeChatReplyProfile profile = WeChatReplyProfile.forPackage("8.0.79", 3182L);
		assertEquals(Arrays.asList("com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver"),
				candidates(profile, "receiverCandidates"));
		assertEquals(Arrays.asList("z2.s1"), candidates(profile, "helperCandidates"));
		assertEquals(Arrays.asList("lt1.a"), candidates(profile, "gateCandidates"));
		assertEquals(Arrays.asList("c", "d", "b"), candidates(profile, "gateMethods"));
		assertEquals(Arrays.asList("lt1.a"), candidates(WeChatReplyProfile.forPackage("8.0.79", 3183L), "gateCandidates"));
		assertEquals(Arrays.asList("os1.a"), candidates(WeChatReplyProfile.forPackage("8.0.79", 3200L), "gateCandidates"));
		for (long code : new long[]{0L, 3085L, 3141L, 3160L, 3180L, 3201L})
			assertEquals("wechat-legacy", WeChatReplyProfile.forPackage("8.0.79", code).label);
		for (String name : new String[]{"8.0.72", "8.0.77", "8.0.78", "8.0.79.dev", "unknown"})
			assertEquals("wechat-legacy", WeChatReplyProfile.forPackage(name, 3182L).label);
	}

	@Test public void separatesWeChat8077Builds() throws Exception {
		assertEquals("wechat-legacy", WeChatReplyProfile.forPackage("8.0.77", 0L).label);
		assertEquals("wechat-legacy", WeChatReplyProfile.forPackage("8.0.77", 3180L).label);
		final WeChatReplyProfile play = WeChatReplyProfile.forPackage("8.0.77", 3141L);
		assertEquals("wechat-8.0.77/3141", play.label);
		assertEquals(Arrays.asList("bs1.a"), candidates(play, "gateCandidates"));
		assertEquals(Arrays.asList("e", "g", "c"), candidates(play, "gateMethods"));
	}

	@Test public void pinsWeChat8077Descriptors() throws Exception {
		final WeChatReplyProfile profile = WeChatReplyProfile.forPackage("8.0.77", 3160L);
		assertEquals(Arrays.asList("com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver"),
				candidates(profile, "receiverCandidates"));
		assertEquals(Arrays.asList("z2.s1"), candidates(profile, "helperCandidates"));
		assertEquals(Arrays.asList("lq1.a"), candidates(profile, "gateCandidates"));
		assertEquals(Arrays.asList("e", "h", "c"), candidates(profile, "gateMethods"));
	}

	@Test public void separatesWeChat8072Builds() throws Exception {
		assertEquals("wechat-legacy", WeChatReplyProfile.forPackage("8.0.72.dev", 3100L).label);
		assertEquals("wechat-legacy", WeChatReplyProfile.forPackage("unknown", 3100L).label);
		final WeChatReplyProfile profile = WeChatReplyProfile.forPackage("8.0.72", 3100L);
		assertEquals(Arrays.asList("z2.s1"), candidates(profile, "helperCandidates"));
		assertEquals(Arrays.asList("dm1.a"), candidates(profile, "gateCandidates"));
		assertEquals(Arrays.asList("f", "h", "c"), candidates(profile, "gateMethods"));
	}

	@Test public void pinsWeChat8072Descriptors() throws Exception {
		final WeChatReplyProfile profile = WeChatReplyProfile.forPackage("8.0.72", 3085L);
		assertEquals(Arrays.asList("z2.s1"), candidates(profile, "helperCandidates"));
		assertEquals(Arrays.asList("dn1.a"), candidates(profile, "gateCandidates"));
		assertEquals(Arrays.asList("f", "h", "c"), candidates(profile, "gateMethods"));
	}

	@Test public void build3183RequiresRealCompleteProfileResolution() {
		WeChatProfileSelector.Selection<WeChatReplyProfile> selected = select3183(
				ValidReceiver.class, ExactHelper.class, ValidGates.class);
		assertEquals(WeChatProfileSelector.MatchKind.EXACT, selected.kind);
		assertEquals("wechat-8.0.79/3183", selected.profile.label);
	}

	@Test public void unknown3183RejectsChangedOrMissingGateAndHelperOrReceiver() {
		for (Class<?> gates : Arrays.asList(ChangedGate.class, MissingGate.class))
			assertEquals(WeChatProfileSelector.MatchKind.NO_MATCH,
					select3183(ValidReceiver.class, ExactHelper.class, gates).kind);
		assertEquals(WeChatProfileSelector.MatchKind.NO_MATCH,
					select3183(ValidReceiver.class, WrongResultHelper.class, ValidGates.class).kind);
		assertEquals(WeChatProfileSelector.MatchKind.NO_MATCH,
					select3183(Object.class, ExactHelper.class, ValidGates.class).kind);
	}

	@Test public void rejectsReceiversThatCannotBeConstructedByDispatch() {
		for (Class<?> receiver : Arrays.asList(AbstractReceiver.class, NoDefaultReceiver.class,
				PrivateConstructorReceiver.class, HiddenReceiver.class))
			assertEquals(receiver.getSimpleName(), WeChatProfileSelector.MatchKind.NO_MATCH,
				    select3183(receiver, ExactHelper.class, ValidGates.class).kind);
	}

	@Test public void receiverHookFailureClosesSyntheticAvailabilityEvenWithReadyGates() throws Exception {
		Class<?> hook = Class.forName("com.oasisfeng.nevo.xposed.MainHook");
		Field profileField = hook.getDeclaredField("sReplyProfile");
		Field receiverField = hook.getDeclaredField("sMMAutoMessageReplyReceiverClass");
		Field gatesField = hook.getDeclaredField("sCarModeGates");
		profileField.setAccessible(true); receiverField.setAccessible(true); gatesField.setAccessible(true);
		Object oldProfile = profileField.get(null), oldReceiver = receiverField.get(null);
		WeChatReplyProfile.GateBatch gates = (WeChatReplyProfile.GateBatch) gatesField.get(null);
		Field ready = WeChatReplyProfile.GateBatch.class.getDeclaredField("ready");
		ready.setAccessible(true);
		boolean oldReady = ready.getBoolean(gates);
		try {
			profileField.set(null, new WeChatReplyProfile.Resolved(WeChatReplyProfile.forPackage("8.0.79", 3182),
				    "8.0.79", 3182, ValidReceiver.class, ExactHelper.class, ValidGates.class,
				    Arrays.asList("c", "d", "b")));
			ready.setBoolean(gates, true);
			receiverField.set(null, null); // Receiver hook threw before publishing success.
			assertFalse((Boolean) hook.getMethod("isSyntheticReplyAvailable").invoke(null));
			receiverField.set(null, ValidReceiver.class);
			assertTrue((Boolean) hook.getMethod("isSyntheticReplyAvailable").invoke(null));
		} finally {
			profileField.set(null, oldProfile); receiverField.set(null, oldReceiver);
			ready.setBoolean(gates, oldReady);
		}
	}

	public abstract static class AbstractReceiver extends ValidReceiver {}
	public static class NoDefaultReceiver extends ValidReceiver { public NoDefaultReceiver(int ignored) {} }
	public static class PrivateConstructorReceiver extends ValidReceiver { private PrivateConstructorReceiver() {} }
	static class HiddenReceiver extends ValidReceiver { public HiddenReceiver() {} }

	private static WeChatProfileSelector.Selection<WeChatReplyProfile> select3183(
			Class<?> receiver, Class<?> helper, Class<?> gates) {
		Map<String, Class<?>> classes = new HashMap<>();
		classes.put("com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver", receiver);
		classes.put("z2.s1", helper);
		classes.put("lt1.a", gates);
		WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.79", 3183, 1);
		return new WeChatProfileSelector(1).select("reply", build, WeChatReplyProfile.candidates(), profile ->
				profile.resolve(name -> {
					Class<?> resolved = classes.get(name);
					if (resolved == null) throw new ClassNotFoundException(name);
					return resolved;
				}, build.versionName, build.versionCode).isUsable(), new MemoryCache());
	}

	@Test public void gateInstallFailureLeavesEarlierCallbacksInert() throws Exception {
		WeChatReplyProfile.GateBatch batch = new WeChatReplyProfile.GateBatch();
		List<WeChatReplyProfile.GateCallback> callbacks = new ArrayList<>();
		List<Integer> removed = new ArrayList<>();
		assertFalse(batch.install(Arrays.asList("c", "d", "b"), (name, callback) -> {
			if ("d".equals(name)) throw new IllegalStateException("synthetic hook failure");
			callbacks.add(callback);
			return () -> removed.add(1);
		}));
		assertFalse(batch.ready());
		assertFalse(callbacks.get(0).active());
		assertEquals(1, removed.size());
	}

	@Test public void failedUnhookCannotReviveOldGateCallbackOnRetry() {
		WeChatReplyProfile.GateBatch batch = new WeChatReplyProfile.GateBatch();
		List<WeChatReplyProfile.GateCallback> oldCallbacks = new ArrayList<>();
		assertFalse(batch.install(Arrays.asList("c", "d", "b"), (name, callback) -> {
			if ("d".equals(name)) throw new IllegalStateException("synthetic install failure");
			oldCallbacks.add(callback);
			return () -> { throw new IllegalStateException("synthetic unhook failure"); };
		}));
		assertTrue(batch.install(Arrays.asList("c", "d", "b"), (name, callback) -> () -> {}));
		assertTrue(batch.ready());
		assertFalse(oldCallbacks.get(0).active());
	}

	@Test public void helperRequiresExactStaticBundleResult() {
		assertTrue(WeChatReplyProfile.hasHelperSignature(ExactHelper.class));
		assertFalse(WeChatReplyProfile.hasHelperSignature(WrongResultHelper.class));
		assertFalse(WeChatReplyProfile.hasHelperSignature(InstanceHelper.class));
	}

	public static class ExactHelper { public static Bundle b(Intent intent) { return null; } }
	public static class WrongResultHelper { public static Object b(Intent intent) { return null; } }
	public static class InstanceHelper { public Bundle b(Intent intent) { return null; } }
	public static class ValidReceiver extends BroadcastReceiver {
		@Override public void onReceive(Context context, Intent intent) {}
	}
	public static class ValidGates {
		public static boolean c() { return true; }
		public static boolean d() { return true; }
		public static boolean b() { return true; }
	}
	public static class ChangedGate {
		public static boolean c() { return true; }
		public static int d() { return 1; }
		public static boolean b() { return true; }
	}
	public static class MissingGate {
		public static boolean c() { return true; }
		public static boolean d() { return true; }
	}

	private static final class MemoryCache implements WeChatProfileSelector.Cache {
		public String read(String f, WeChatProfileSelector.BuildId b, int r) { return null; }
		public void write(String f, WeChatProfileSelector.BuildId b, int r, String id) {}
		public void remove(String f, WeChatProfileSelector.BuildId b, int r) {}
	}

	private static List<String> candidates(final WeChatReplyProfile profile, final String field) throws Exception {
		final Field declared = WeChatReplyProfile.class.getDeclaredField(field);
		declared.setAccessible(true);
		return Arrays.asList((String[]) declared.get(profile));
	}
}
