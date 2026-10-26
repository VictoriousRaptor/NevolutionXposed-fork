package com.oasisfeng.nevo.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class RemotePreferenceStoreTest {
	@Test public void imageWaitDefaultsAndExistingRemoteChoiceAreSynchronized() {
		assertEquals("1500", RemotePreferenceStore.mergeImageWaitMillis(null, null));
		assertEquals("5000", RemotePreferenceStore.mergeImageWaitMillis(null, "5000"));
	}

	@Test public void imageWaitLocalChoiceWinsAndIsNormalized() {
		assertEquals("7500", RemotePreferenceStore.mergeImageWaitMillis(" 7500 ", "5000"));
		assertEquals("100", RemotePreferenceStore.mergeImageWaitMillis("100", "5000"));
	}

	@Test public void invalidImageWaitCannotEnterRemotePreferences() {
		assertEquals("1500", RemotePreferenceStore.mergeImageWaitMillis("invalid", "5000"));
		assertEquals("1500", RemotePreferenceStore.mergeImageWaitMillis(null, "30001"));
	}

	@Test public void preserveEmojiDefaultsOffAndKeepsExplicitChoicesAcrossUpgradeAndSync() {
		String key = RemotePreferenceStore.KEY_PRESERVE_EMOJI;
		assertEquals(Boolean.FALSE, RemotePreferenceStore.migratedLocalValues(Collections.emptyMap(), 6).get(key));
		assertEquals(Boolean.TRUE, RemotePreferenceStore.migratedLocalValues(Collections.singletonMap(key, true), 6).get(key));
		assertEquals(Boolean.TRUE, RemotePreferenceStore.merge(Collections.emptyMap(), Collections.singletonMap(key, true)).get(key));
		assertEquals(Boolean.FALSE, RemotePreferenceStore.merge(Collections.singletonMap(key, false), Collections.singletonMap(key, true)).get(key));
	}

	@Test public void lastReplyDefaultsOffAndPreservesExplicitChoiceAcrossUpgrade() {
		String key = RemotePreferenceStore.KEY_SHOW_LAST_REPLY;
		assertEquals(Boolean.FALSE, RemotePreferenceStore.merge(Collections.emptyMap(), Collections.emptyMap()).get(key));
		assertEquals(Boolean.FALSE, RemotePreferenceStore.migratedLocalValues(Collections.emptyMap(), 5).get(key));
		assertEquals(Boolean.TRUE, RemotePreferenceStore.migratedLocalValues(Collections.singletonMap(key, true), 5).get(key));
		assertEquals(Boolean.TRUE, RemotePreferenceStore.merge(Collections.emptyMap(), Collections.singletonMap(key, true)).get(key));
	}
	@Test public void localValueWinsOverRemoteValue() {
		Map<String, Boolean> local = Collections.singletonMap("WeChatDecorator.enabled", false);
		Map<String, Boolean> remote = Collections.singletonMap("WeChatDecorator.enabled", true);

		assertEquals(Boolean.FALSE, RemotePreferenceStore.merge(local, remote).get("WeChatDecorator.enabled"));
	}

	@Test public void remoteValueIsKeptWhenLocalValueIsMissing() {
		Map<String, Boolean> remote = Collections.singletonMap(RemotePreferenceStore.KEY_IMAGE_PREVIEW, true);

		assertEquals(Boolean.TRUE,
				RemotePreferenceStore.merge(Collections.emptyMap(), remote).get(RemotePreferenceStore.KEY_IMAGE_PREVIEW));
	}

	@Test public void missingValuesUseSafeFeatureDefaults() {
		Map<String, Boolean> merged = RemotePreferenceStore.merge(Collections.emptyMap(), Collections.emptyMap());

		assertEquals(RemotePreferenceStore.BOOLEAN_KEYS.length, merged.size());
		assertEquals(Boolean.TRUE, merged.get("WeChatDecorator.enabled"));
		assertEquals(Boolean.FALSE, merged.get(RemotePreferenceStore.KEY_IMAGE_PREVIEW));
		assertEquals(Boolean.FALSE, merged.get(RemotePreferenceStore.KEY_IMAGE_PREVIEW_LARGE));
		assertEquals(Boolean.FALSE, merged.get(RemotePreferenceStore.KEY_MARKDOWN));
		assertFalse(merged.containsKey("MIUIDecorator.enabled"));
	}

	@Test public void imagePreviewStaysOffUntilTheUserOptsIn() {
		Map<String, Boolean> values = new LinkedHashMap<>();

		RemotePreferenceStore.applySchemaMigration(values, 1);

		assertEquals(Boolean.FALSE, values.get(RemotePreferenceStore.KEY_IMAGE_PREVIEW));
		// An explicit user choice still wins over the default.
		assertEquals(Boolean.TRUE, RemotePreferenceStore.merge(
				Collections.singletonMap(RemotePreferenceStore.KEY_IMAGE_PREVIEW, true), Collections.emptyMap())
				.get(RemotePreferenceStore.KEY_IMAGE_PREVIEW));
	}

	@Test public void obsoleteMiuiFixPreferenceIsNotSynchronized() {
		Map<String, Boolean> local = Collections.singletonMap("WeChatDecorator.miui_fix", true);

		org.junit.Assert.assertFalse(RemotePreferenceStore.merge(local, Collections.emptyMap())
				.containsKey("WeChatDecorator.miui_fix"));
	}

	@Test public void legacyStoredDefaultsAreMigratedToSafeValues() {
		Map<String, Boolean> values = new LinkedHashMap<>();
		values.put("WeChatDecorator.enabled", false);
		values.put("MIUIDecorator.enabled", true);

		RemotePreferenceStore.applySchemaMigration(values, 0);

		assertEquals(Boolean.FALSE, values.get("WeChatDecorator.enabled"));
		assertNull(values.get("MIUIDecorator.enabled"));	// The MIUI decorator was removed.
	}

	@Test public void currentSchemaKeepsExplicitUserChoices() {
		Map<String, Boolean> values = new LinkedHashMap<>();
		values.put("WeChatDecorator.enabled", false);
		values.put(RemotePreferenceStore.KEY_IMAGE_PREVIEW, true);

		RemotePreferenceStore.applySchemaMigration(values, RemotePreferenceStore.CURRENT_SCHEMA_VERSION);

		assertEquals(Boolean.FALSE, values.get("WeChatDecorator.enabled"));
		assertEquals(Boolean.TRUE, values.get(RemotePreferenceStore.KEY_IMAGE_PREVIEW));
	}

	@Test public void schemaThreeUpgradeKeepsImagePreviewChoice() {
		Map<String, Boolean> stored = new LinkedHashMap<>();
		stored.put("WeChatDecorator.enabled", false);
		stored.put("WeChatDecorator.image_preview", true);
		Map<String, Boolean> values = RemotePreferenceStore.migratedLocalValues(stored, 3);

		assertEquals(Boolean.FALSE, values.get("WeChatDecorator.enabled"));
		assertEquals(Boolean.TRUE, values.get("WeChatDecorator.image_preview"));
		assertEquals(Boolean.FALSE, values.get(RemotePreferenceStore.KEY_IMAGE_PREVIEW_LARGE));
	}

	@Test public void removedPreferenceIsDiscardedDuringUpgradeAndSync() {
		Map<String, Boolean> stored = Collections.singletonMap("MediaDecorator.enabled", true);
		assertFalse(RemotePreferenceStore.migratedLocalValues(stored, 7).containsKey("MediaDecorator.enabled"));
		assertFalse(RemotePreferenceStore.merge(Collections.emptyMap(), stored).containsKey("MediaDecorator.enabled"));
	}

	@Test public void mergeReturnsOnlyKnownKeysInStableOrder() {
		Map<String, Boolean> local = new LinkedHashMap<>();
		local.put("unknown", false);

		assertEquals(java.util.Arrays.asList(RemotePreferenceStore.BOOLEAN_KEYS),
				new java.util.ArrayList<>(RemotePreferenceStore.merge(local, Collections.emptyMap()).keySet()));
	}

	@Test public void markdownUpgradeDefaultsOffAndKeepsOtherChoices() {
		Map<String, Boolean> stored = new LinkedHashMap<>();
		stored.put(RemotePreferenceStore.KEY_IMAGE_PREVIEW, true);
		stored.put(RemotePreferenceStore.KEY_IMAGE_PREVIEW_LARGE, true);
		Map<String, Boolean> values = RemotePreferenceStore.migratedLocalValues(stored, 4);
		assertEquals(Boolean.FALSE, values.get(RemotePreferenceStore.KEY_MARKDOWN));
		assertEquals(Boolean.TRUE, values.get(RemotePreferenceStore.KEY_IMAGE_PREVIEW_LARGE));
		assertEquals(Boolean.TRUE, values.get(RemotePreferenceStore.KEY_IMAGE_PREVIEW));
	}

	@Test public void markdownExplicitChoiceSurvivesCurrentSchemaAndRemoteMerge() {
		Map<String, Boolean> stored = Collections.singletonMap(RemotePreferenceStore.KEY_MARKDOWN, true);
		assertEquals(Boolean.TRUE, RemotePreferenceStore.migratedLocalValues(stored, 5).get(RemotePreferenceStore.KEY_MARKDOWN));
		assertEquals(Boolean.TRUE, RemotePreferenceStore.merge(Collections.emptyMap(), stored).get(RemotePreferenceStore.KEY_MARKDOWN));
		assertEquals(Boolean.FALSE, RemotePreferenceStore.merge(
				Collections.singletonMap(RemotePreferenceStore.KEY_MARKDOWN, false), stored).get(RemotePreferenceStore.KEY_MARKDOWN));
	}
}
