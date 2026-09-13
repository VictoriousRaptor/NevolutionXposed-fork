package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

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
		assertEquals("wechat-8.0.78", WeChatReplyProfile.forPackage("8.0.78", 3180L).label);
		assertEquals("wechat-8.0.72", WeChatReplyProfile.forPackage("8.0.72", 3085L).label);
		assertEquals("wechat-legacy", WeChatReplyProfile.forPackage("8.0.76", 0L).label);
	}

	@Test public void mapsByVersionCodeWhenTheVersionNameDiffers() {
		assertEquals("wechat-8.0.78", WeChatReplyProfile.forPackage("8.0.78.dev", 3180L).label);
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

	@Test public void pinsWeChat8072Descriptors() throws Exception {
		final WeChatReplyProfile profile = WeChatReplyProfile.forPackage("8.0.72", 3085L);
		assertEquals(Arrays.asList("z2.s1"), candidates(profile, "helperCandidates"));
		assertEquals(Arrays.asList("dn1.a"), candidates(profile, "gateCandidates"));
		assertEquals(Arrays.asList("f", "h", "c"), candidates(profile, "gateMethods"));
	}

	private static List<String> candidates(final WeChatReplyProfile profile, final String field) throws Exception {
		final Field declared = WeChatReplyProfile.class.getDeclaredField(field);
		declared.setAccessible(true);
		return Arrays.asList((String[]) declared.get(profile));
	}
}
