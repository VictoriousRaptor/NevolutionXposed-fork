package com.oasisfeng.nevo.xposed;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		getFragmentManager()
			.beginTransaction()
			.add(R.id.main_fragment, new MainPreference())
			.commit();
	}
}
