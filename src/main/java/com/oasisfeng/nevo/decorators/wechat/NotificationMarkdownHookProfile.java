package com.oasisfeng.nevo.decorators.wechat;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Exact framework cleanup signatures, kept together so callers from different layouts cannot mix. */
final class NotificationMarkdownHookProfile {
    final Method cleanup;
    final Method[] callers;
    final Field textField;

    private NotificationMarkdownHookProfile(Method cleanup, Method[] callers, Field textField) {
        this.cleanup = cleanup;
        this.callers = callers;
        this.textField = textField;
    }

    static NotificationMarkdownHookProfile resolve(Class<?> message, Class<?> style,
                                                    Class<?> bundle, Class<?> remoteViews)
            throws ReflectiveOperationException {
        try { return legacy(message, style, bundle, remoteViews); }
        catch (NoSuchMethodException unavailable) { /* Resolve the newer layout as a complete chain. */ }

        Method cleanup = method(message, "stripNonStyleSpans", void.class, false);
        Field text = message.getDeclaredField("mText");
        if (text.getType() != CharSequence.class || Modifier.isStatic(text.getModifiers())
                || Modifier.isFinal(text.getModifiers()))
            throw new NoSuchFieldException("Unsupported message text field");
        text.setAccessible(true);
        Method[] callers = {
                method(style, "getBundleArrayForMessages", Array.newInstance(bundle, 0).getClass(), true,
                        List.class, boolean.class),
                method(style, "addExtras", void.class, false, bundle, boolean.class),
                method(style, "makeMessagingView", remoteViews, false, int.class)
        };
        return new NotificationMarkdownHookProfile(cleanup, callers, text);
    }

    private static NotificationMarkdownHookProfile legacy(Class<?> message, Class<?> style,
                                                           Class<?> bundle, Class<?> remoteViews)
            throws NoSuchMethodException {
        Method cleanup = method(message, "stripNonStyleSpans", CharSequence.class, false, CharSequence.class);
        Method[] callers = {
                method(message, "ensureColorContrastOrStripStyling", void.class, false, int.class),
                method(style, "getBundleArrayForMessages", Array.newInstance(bundle, 0).getClass(), true,
                        List.class, boolean.class, int.class),
                method(style, "addExtras", void.class, false, bundle, boolean.class, int.class),
                method(style, "makeMessagingView", remoteViews, false, int.class)
        };
        return new NotificationMarkdownHookProfile(cleanup, callers, null);
    }

    private static Method method(Class<?> type, String name, Class<?> returns, boolean isStatic,
                                 Class<?>... parameters) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name, parameters);
        if (method.getReturnType() != returns || Modifier.isStatic(method.getModifiers()) != isStatic)
            throw new NoSuchMethodException("Unsupported message cleanup signature: " + name);
        method.setAccessible(true);
        return method;
    }
}
