package com.notxx.xposed;

import android.util.Log;

import java.io.File;

import de.robv.android.xposed.XSharedPreferences;

import com.oasisfeng.nevo.xposed.BuildConfig;

public class DeviceSharedPreferences {
	public static XSharedPreferences get(String packageName) {
		File file = new File("/data/user_de/0/" + packageName + "/shared_prefs/" + packageName + "_preferences.xml");
		XSharedPreferences prefs = new XSharedPreferences(file);
		if (BuildConfig.DEBUG) Log.d("DeviceSharedPreferences", "file " + file + " " + file.exists());
		return prefs;
	}
}