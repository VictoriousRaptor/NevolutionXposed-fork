package com.oasisfeng.nevo.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class RemotePreferenceStoreTest {
	@Test public void localValueWinsOverRemoteValue() {
		Map<String, Boolean> local = Collections.singletonMap("WeChatDecorator.enabled", false);
		Map<String, Boolean> remote = Collections.singletonMap("WeChatDecorator.enabled", true);

		assertEquals(Boolean.FALSE, RemotePreferenceStore.merge(local, remote).get("WeChatDecorator.enabled"));
	}

	@Test public void remoteValueIsKeptWhenLocalValueIsMissing() {
		Map<String, Boolean> remote = Collections.singletonMap("MediaDecorator.enabled", false);

		assertEquals(Boolean.FALSE,
				RemotePreferenceStore.merge(Collections.emptyMap(), remote).get("MediaDecorator.enabled"));
	}

	@Test public void missingValuesUseSafeFeatureDefaults() {
		Map<String, Boolean> merged = RemotePreferenceStore.merge(Collections.emptyMap(), Collections.emptyMap());

		assertEquals(RemotePreferenceStore.BOOLEAN_KEYS.length, merged.size());
		assertEquals(Boolean.TRUE, merged.get("WeChatDecorator.enabled"));
		assertEquals(Boolean.FALSE, merged.get(RemotePreferenceStore.KEY_IMAGE_PREVIEW));
		assertEquals(Boolean.FALSE, merged.get("MediaDecorator.enabled"));
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
		values.put("MediaDecorator.enabled", true);

		RemotePreferenceStore.applySchemaMigration(values, 0);

		assertEquals(Boolean.FALSE, values.get("WeChatDecorator.enabled"));
		assertEquals(Boolean.FALSE, values.get("MediaDecorator.enabled"));
		assertNull(values.get("MIUIDecorator.enabled"));	// The MIUI decorator was removed.
	}

	@Test public void currentSchemaKeepsExplicitUserChoices() {
		Map<String, Boolean> values = new LinkedHashMap<>();
		values.put("WeChatDecorator.enabled", false);
		values.put("MediaDecorator.enabled", true);

		RemotePreferenceStore.applySchemaMigration(values, RemotePreferenceStore.CURRENT_SCHEMA_VERSION);

		assertEquals(Boolean.FALSE, values.get("WeChatDecorator.enabled"));
		assertEquals(Boolean.TRUE, values.get("MediaDecorator.enabled"));
	}

	@Test public void mergeReturnsOnlyKnownKeysInStableOrder() {
		Map<String, Boolean> local = new LinkedHashMap<>();
		local.put("unknown", false);

		assertEquals(java.util.Arrays.asList(RemotePreferenceStore.BOOLEAN_KEYS),
				new java.util.ArrayList<>(RemotePreferenceStore.merge(local, Collections.emptyMap()).keySet()));
	}
}
