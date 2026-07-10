package com.oasisfeng.nevo.sdk;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public interface HookSupport {
	public void hook(XC_LoadPackage.LoadPackageParam loadPackageParam) throws XposedHelpers.ClassNotFoundError;
}
