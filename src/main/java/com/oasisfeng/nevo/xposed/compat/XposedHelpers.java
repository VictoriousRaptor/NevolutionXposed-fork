package com.oasisfeng.nevo.xposed.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class XposedHelpers {
	private static final Map<Object, Map<String, Object>> ADDITIONAL_FIELDS = new WeakHashMap<>();
	/** Striped monitors so notification threads do not serialize on a single global lock. */
	private static final Object[] FIELD_LOCKS = new Object[16];
	static {
		for (int i = 0; i < FIELD_LOCKS.length; i++) FIELD_LOCKS[i] = new Object();
	}

	private XposedHelpers() {}

	private static Object fieldLock(Object receiver) {
		return FIELD_LOCKS[(System.identityHashCode(receiver) >>> 1) % FIELD_LOCKS.length];
	}

	public static Class<?> findClass(String className, ClassLoader classLoader) {
		try {
			return Class.forName(className, false, classLoader);
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundError(e);
		}
	}

	public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
		for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
			try {
				Method method = current.getDeclaredMethod(methodName, parameterTypes);
				method.setAccessible(true);
				return method;
			} catch (NoSuchMethodException ignored) {}
		}
		throw new NoSuchMethodError(clazz.getName() + "." + methodName);
	}

	public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
		Method best = null;
		int bestScore = Integer.MAX_VALUE;
		for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
			for (Method method : current.getDeclaredMethods()) {
				if (!methodName.equals(method.getName())) continue;
				int score = score(method.getParameterTypes(), parameterTypes);
				if (score < bestScore) {
					best = method;
					bestScore = score;
				}
			}
		}
		if (best == null) throw new NoSuchMethodError(clazz.getName() + "." + methodName);
		best.setAccessible(true);
		return best;
	}

	public static Object findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
		ParsedHook parsed = parseHook(parameterTypesAndCallback);
		return XposedBridge.hookMethod(findMethodExact(clazz, methodName, parsed.parameterTypes), parsed.callback);
	}

	public static Object callMethod(Object receiver, String methodName, Object... args) {
		if (receiver == null) throw new NullPointerException("receiver");
		Class<?>[] types = runtimeTypes(args);
		Method method = findMethodBestMatch(receiver.getClass(), methodName, types);
		try {
			return method.invoke(receiver, args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	public static Object newInstance(Class<?> clazz, Object... args) {
		Constructor<?> best = null;
		int bestScore = Integer.MAX_VALUE;
		Class<?>[] types = runtimeTypes(args);
		for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
			int score = score(constructor.getParameterTypes(), types);
			if (score < bestScore) {
				best = constructor;
				bestScore = score;
			}
		}
		if (best == null) throw new NoSuchMethodError(clazz.getName() + " constructor");
		try {
			best.setAccessible(true);
			return best.newInstance(args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	public static void setObjectField(Object receiver, String fieldName, Object value) {
		try {
			findField(receiver.getClass(), fieldName).set(receiver, value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public static void setIntField(Object receiver, String fieldName, int value) {
		try {
			findField(receiver.getClass(), fieldName).setInt(receiver, value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public static Object getAdditionalInstanceField(Object receiver, String key) {
		synchronized (fieldLock(receiver)) {
			Map<String, Object> fields = ADDITIONAL_FIELDS.get(receiver);
			return fields == null ? null : fields.get(key);
		}
	}

	public static Object setAdditionalInstanceField(Object receiver, String key, Object value) {
		synchronized (fieldLock(receiver)) {
			Map<String, Object> fields = ADDITIONAL_FIELDS.get(receiver);
			Object previous = fields == null ? null : fields.get(key);
			if (value == null) {
				if (fields != null) {
					fields.remove(key);
					if (fields.isEmpty()) ADDITIONAL_FIELDS.remove(receiver);
				}
				return previous;
			}
			if (fields == null) {
				fields = new HashMap<>();
				ADDITIONAL_FIELDS.put(receiver, fields);
			}
			fields.put(key, value);
			return previous;
		}
	}

	private static Field findField(Class<?> clazz, String fieldName) {
		for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
			try {
				Field field = current.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {}
		}
		throw new NoSuchFieldError(clazz.getName() + "." + fieldName);
	}

	private static ParsedHook parseHook(Object[] values) {
		if (values.length == 0 || !(values[values.length - 1] instanceof XC_MethodHook)) {
			throw new IllegalArgumentException("Last argument must be XC_MethodHook");
		}
		XC_MethodHook callback = (XC_MethodHook) values[values.length - 1];
		Class<?>[] parameterTypes;
		if (values.length == 2 && values[0] instanceof Class<?>[]) {
			parameterTypes = (Class<?>[]) values[0];
		} else {
			parameterTypes = new Class<?>[values.length - 1];
			for (int i = 0; i < parameterTypes.length; i++) {
				if (!(values[i] instanceof Class<?>)) throw new IllegalArgumentException("Not a parameter type: " + values[i]);
				parameterTypes[i] = (Class<?>) values[i];
			}
		}
		return new ParsedHook(parameterTypes, callback);
	}

	private static Class<?>[] runtimeTypes(Object[] args) {
		Class<?>[] types = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) types[i] = args[i] == null ? null : args[i].getClass();
		return types;
	}

	private static int score(Class<?>[] declared, Class<?>[] requested) {
		if (declared.length != requested.length) return Integer.MAX_VALUE;
		int score = 0;
		for (int i = 0; i < declared.length; i++) {
			Class<?> wanted = wrap(declared[i]);
			Class<?> actual = requested[i] == null ? null : wrap(requested[i]);
			if (actual == null) {
				if (declared[i].isPrimitive()) return Integer.MAX_VALUE;
				score += 2;
			} else if (wanted.equals(actual)) {
				// Exact match.
			} else if (wanted.isAssignableFrom(actual)) {
				score += 1;
			} else {
				return Integer.MAX_VALUE;
			}
		}
		return score;
	}

	private static Class<?> wrap(Class<?> type) {
		if (type == null || !type.isPrimitive()) return type;
		if (type == boolean.class) return Boolean.class;
		if (type == byte.class) return Byte.class;
		if (type == char.class) return Character.class;
		if (type == short.class) return Short.class;
		if (type == int.class) return Integer.class;
		if (type == long.class) return Long.class;
		if (type == float.class) return Float.class;
		if (type == double.class) return Double.class;
		return Void.class;
	}

	private static final class ParsedHook {
		final Class<?>[] parameterTypes;
		final XC_MethodHook callback;

		ParsedHook(Class<?>[] parameterTypes, XC_MethodHook callback) {
			this.parameterTypes = parameterTypes;
			this.callback = callback;
		}
	}

	public static final class ClassNotFoundError extends Error {
		ClassNotFoundError(Throwable cause) { super(cause); }
	}
}
