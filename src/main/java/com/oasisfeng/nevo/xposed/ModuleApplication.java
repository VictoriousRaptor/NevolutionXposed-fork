package com.oasisfeng.nevo.xposed;

import android.app.Application;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class ModuleApplication extends Application implements XposedServiceHelper.OnServiceListener {

	private RemotePreferenceStore preferences;

	@Override public void onCreate() {
		super.onCreate();
		preferences = new RemotePreferenceStore(this);
		XposedServiceHelper.registerListener(this);
	}

	@Override public void onServiceBind(XposedService service) {
		preferences.bind(service);
	}

	@Override public void onServiceDied(XposedService service) {
		preferences.unbind(service);
	}
}
