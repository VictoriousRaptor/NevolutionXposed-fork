package com.oasisfeng.nevo.xposed;

import com.oasisfeng.nevo.xposed.compat.XposedHelpers;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Resolves platform removal callbacks and owns successful installations for one process. */
final class NotificationRemovalHooks {
    private final Set<Method> installed = new HashSet<>();

    static Method findMethod(Class<?> listener, Class<?> notification, Class<?> ranking, Class<?> stats) {
        if (stats != null) {
            try { return callback(listener, notification, ranking, stats, int.class); }
            catch (NoSuchMethodError unavailable) { /* Try the older signature independently of type availability. */ }
        }
        return callback(listener, notification, ranking, int.class);
    }

    private static Method callback(Class<?> listener, Class<?>... parameters) {
        Method method = XposedHelpers.findMethodExact(listener, "onNotificationRemoved", parameters);
        if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodError("Invalid notification removal callback");
        return method;
    }

    synchronized boolean installOnce(Method method, Consumer<Method> installer) {
        if (installed.contains(method)) return false;
        installer.accept(method);
        installed.add(method);
        return true;
    }
}
