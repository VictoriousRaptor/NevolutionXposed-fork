package com.oasisfeng.nevo.xposed;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.libxposed.service.XposedService;

@SuppressWarnings("deprecation")
final class RemotePreferenceStore implements SharedPreferences.OnSharedPreferenceChangeListener {
	static final String GROUP = "settings";
	private static final String SCHEMA_VERSION_KEY = "settings_schema_version";
	private static final int CURRENT_SCHEMA_VERSION = 1;
	static final String[] BOOLEAN_KEYS = {
			"WeChatDecorator.enabled",
			"MIUIDecorator.enabled",
			"MediaDecorator.enabled"
	};

	private static final String TAG = "RemotePreferenceStore";
	private final SharedPreferences local;
	private volatile XposedService service;

	RemotePreferenceStore(Context context) {
		Context storage = context.createDeviceProtectedStorageContext();
		local = PreferenceManager.getDefaultSharedPreferences(storage);
		initializeLocalDefaults();
		local.registerOnSharedPreferenceChangeListener(this);
	}

	private void initializeLocalDefaults() {
		if (local.getInt(SCHEMA_VERSION_KEY, 0) >= CURRENT_SCHEMA_VERSION) return;
		SharedPreferences.Editor editor = local.edit();
		if (!local.contains("WeChatDecorator.enabled")) {
			editor.putBoolean("WeChatDecorator.enabled", true);
		}
		editor.putBoolean("MIUIDecorator.enabled", false);
		editor.putBoolean("MediaDecorator.enabled", false);
		editor.remove("WeChatDecorator.miui_fix");
		editor.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);
		editor.apply();
	}

	synchronized void bind(XposedService service) {
		this.service = service;
		try {
			synchronize(local, service.getRemotePreferences(GROUP));
		} catch (RuntimeException e) {
			Log.e(TAG, "Unable to synchronize remote preferences", e);
		}
	}

	synchronized void unbind(XposedService service) {
		if (this.service == service) this.service = null;
	}

	@Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (!isBooleanKey(key)) return;
		XposedService bound = service;
		if (bound == null) return;
		try {
			bound.getRemotePreferences(GROUP).edit()
					.putBoolean(key, sharedPreferences.getBoolean(key, defaultValue(key))).apply();
		} catch (RuntimeException e) {
			Log.e(TAG, "Unable to update remote preference " + key, e);
		}
	}

	static Map<String, Boolean> merge(Map<String, Boolean> local, Map<String, Boolean> remote) {
		Map<String, Boolean> merged = new LinkedHashMap<>();
		for (String key : BOOLEAN_KEYS) {
			Boolean value = local.containsKey(key) ? local.get(key) : remote.get(key);
			merged.put(key, value != null ? value : defaultValue(key));
		}
		return merged;
	}

	private static void synchronize(SharedPreferences local, SharedPreferences remote) {
		Map<String, Boolean> localValues = readKnownBooleans(local);
		Map<String, Boolean> remoteValues = readKnownBooleans(remote);
		Map<String, Boolean> merged = merge(localValues, remoteValues);
		int schemaVersion = Math.max(local.getInt(SCHEMA_VERSION_KEY, 0),
				remote.getInt(SCHEMA_VERSION_KEY, 0));
		applySchemaMigration(merged, schemaVersion);
		SharedPreferences.Editor localEditor = local.edit();
		SharedPreferences.Editor remoteEditor = remote.edit();
		for (Map.Entry<String, Boolean> entry : merged.entrySet()) {
			localEditor.putBoolean(entry.getKey(), entry.getValue());
			remoteEditor.putBoolean(entry.getKey(), entry.getValue());
		}
		localEditor.remove("WeChatDecorator.miui_fix").putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);
		remoteEditor.remove("WeChatDecorator.miui_fix").putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);
		localEditor.apply();
		remoteEditor.apply();
	}

	static void applySchemaMigration(Map<String, Boolean> values, int schemaVersion) {
		if (schemaVersion >= CURRENT_SCHEMA_VERSION) return;
		values.put("MIUIDecorator.enabled", false);
		values.put("MediaDecorator.enabled", false);
	}

	private static Map<String, Boolean> readKnownBooleans(SharedPreferences preferences) {
		Map<String, Boolean> values = new LinkedHashMap<>();
		for (String key : BOOLEAN_KEYS) {
			if (preferences.contains(key)) values.put(key, preferences.getBoolean(key, defaultValue(key)));
		}
		return values;
	}

	static boolean defaultValue(String key) {
		return "WeChatDecorator.enabled".equals(key);
	}

	private static boolean isBooleanKey(String key) {
		for (String candidate : BOOLEAN_KEYS) if (candidate.equals(key)) return true;
		return false;
	}
}
