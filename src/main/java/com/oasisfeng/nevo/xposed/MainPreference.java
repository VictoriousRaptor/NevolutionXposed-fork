package com.oasisfeng.nevo.xposed;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;


public class MainPreference extends PreferenceFragmentCompat {
	@Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		final PreferenceManager manager = getPreferenceManager();
		manager.setStorageDeviceProtected();
		setPreferencesFromResource(R.xml.main_preference, rootKey);
	}
}
