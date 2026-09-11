package com.oasisfeng.nevo.sdk;

import com.oasisfeng.nevo.xposed.compat.PackageHookContext;
import com.oasisfeng.nevo.xposed.compat.XposedHelpers;

public interface HookSupport {
	public void hook(PackageHookContext loadPackageParam) throws XposedHelpers.ClassNotFoundError;
}
