package com.oasisfeng.nevo.xposed;

import android.app.Activity;
import android.os.Bundle;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings_title);
		setContentView(R.layout.activity_main);
		if (savedInstanceState == null) {
			getFragmentManager()
					.beginTransaction()
					.replace(R.id.main_fragment, new MainPreference())
					.commit();
		}
	}
}
