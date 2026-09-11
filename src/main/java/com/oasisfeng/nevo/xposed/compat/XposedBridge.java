package com.oasisfeng.nevo.xposed.compat;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.error.XposedFrameworkError;

public final class XposedBridge {
	private static final String TAG = "NevolutionXposed";
	private static volatile XposedModule module;

	private XposedBridge() {}

	public static void attach(XposedModule module) {
		XposedBridge.module = module;
	}

	public static void log(String message) {
		XposedModule current = requireModule();
		current.log(Log.INFO, TAG, message);
	}

	public static void rethrowFrameworkError(Throwable failure) {
		if (failure instanceof XposedFrameworkError) throw (XposedFrameworkError) failure;
	}

	public static XposedInterface.HookHandle hookMethod(Member member, XC_MethodHook callback) {
		if (!(member instanceof Executable)) throw new IllegalArgumentException("Not executable: " + member);
		Executable executable = (Executable) member;
		XposedModule current = requireModule();
		XposedInterface.HookHandle handle = current.hook(executable)
				.setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
				.intercept(chain -> intercept(current, callback, chain));
		return handle;
	}

	public static Set<XposedInterface.HookHandle> hookAllMethods(Class<?> clazz, String methodName, XC_MethodHook callback) {
		Set<XposedInterface.HookHandle> handles = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Method method : clazz.getDeclaredMethods()) {
			if (methodName.equals(method.getName())) {
				method.setAccessible(true);
				handles.add(hookMethod(method, callback));
			}
		}
		return handles;
	}

	public static Set<XposedInterface.HookHandle> hookAllConstructors(Class<?> clazz, XC_MethodHook callback) {
		Set<XposedInterface.HookHandle> handles = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
			constructor.setAccessible(true);
			handles.add(hookMethod(constructor, callback));
		}
		return handles;
	}

	static Object intercept(XposedModule current, XC_MethodHook callback, XposedInterface.Chain chain) throws Throwable {
		XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
		param.method = chain.getExecutable();
		param.thisObject = chain.getThisObject();
		param.args = chain.getArgs().toArray();
		try {
			callback.beforeHookedMethod(param);
		} catch (XposedFrameworkError failure) {
			throw failure;
		} catch (Throwable failure) {
			current.log(Log.ERROR, TAG, "before hook failed for " + chain.getExecutable(), failure);
		}
		if (!param.shouldReturnEarly()) {
			try {
				param.setResultFromOriginal(chain.proceed(param.args));
			} catch (Throwable originalFailure) {
				param.setThrowableFromOriginal(originalFailure);
			}
		}
		try {
			callback.afterHookedMethod(param);
		} catch (XposedFrameworkError failure) {
			throw failure;
		} catch (Throwable failure) {
			current.log(Log.ERROR, TAG, "after hook failed for " + chain.getExecutable(), failure);
		}
		if (param.hasThrowable()) throw param.getThrowable();
		return param.getResult();
	}

	private static XposedModule requireModule() {
		XposedModule current = module;
		if (current == null) throw new IllegalStateException("Xposed module is not attached");
		return current;
	}
}
