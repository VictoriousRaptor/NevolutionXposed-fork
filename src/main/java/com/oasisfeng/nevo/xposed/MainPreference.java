package com.oasisfeng.nevo.xposed;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import com.oasisfeng.nevo.decorators.wechat.ImagePreviewPolicy;


public class MainPreference extends PreferenceFragmentCompat {
	@Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		final PreferenceManager manager = getPreferenceManager();
		manager.setStorageDeviceProtected();
		setPreferencesFromResource(R.xml.main_preference, rootKey);
		EditTextPreference wait = findPreference(ImagePreviewPolicy.PREF_WAIT_MS);
		if (wait == null) return;
		wait.setOnBindEditTextListener(editor -> editor.setInputType(InputType.TYPE_CLASS_NUMBER));
		wait.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> getString(R.string.decorator_wechat_image_wait_summary,
				ImagePreviewPolicy.waitMillis(preference.getText())));
		wait.setOnPreferenceChangeListener((preference, value) -> {
			if (value instanceof String && ImagePreviewPolicy.isValidWaitMillis((String) value)) return true;
			Toast.makeText(requireContext(), getString(R.string.decorator_wechat_image_wait_range,
					ImagePreviewPolicy.MIN_WAIT_MS, ImagePreviewPolicy.MAX_WAIT_MS), Toast.LENGTH_SHORT).show();
			return false;
		});
	}
}
