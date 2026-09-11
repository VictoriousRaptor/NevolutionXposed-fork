package com.oasisfeng.nevo.xposed.compat;

public final class PackageHookContext {
	public final String packageName;
	public final String processName;
	public final ClassLoader classLoader;

	public PackageHookContext(String packageName, String processName, ClassLoader classLoader) {
		this.packageName = packageName;
		this.processName = processName;
		this.classLoader = classLoader;
	}
}
