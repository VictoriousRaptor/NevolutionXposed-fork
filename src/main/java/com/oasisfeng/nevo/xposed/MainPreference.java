package com.oasisfeng.nevo.xposed;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

public class MainPreference extends PreferenceFragmentCompat {
	@Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		final PreferenceManager manager = getPreferenceManager();
		if (SDK_INT >= N) manager.setStorageDeviceProtected();
		setPreferencesFromResource(R.xml.main_preference, rootKey);
	}
}
