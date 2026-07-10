package com.oasisfeng.nevo.xposed;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Map;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

@SuppressWarnings("deprecation")
public class MainPreference extends PreferenceFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PreferenceManager manager = getPreferenceManager();
		if (SDK_INT >= N) manager.setStorageDeviceProtected();
		addPreferencesFromResource(R.xml.main_preference);
	}
}