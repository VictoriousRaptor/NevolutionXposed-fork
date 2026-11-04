package com.oasisfeng.nevo.decorators.wechat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Resolve complete framework rendering contracts before installing either optional hook. */
final class NotificationAppearanceHookProfile {
    static final class Header {
        final Method render;
        final Field context, notification;

        Header(Method render, Field context, Field notification) {
            this.render = render;
            this.context = context;
            this.notification = notification;
        }
    }

    static final class Avatar {
        final Method render;
        final Field builder, context, notification, conversationType, shortcutIcon;

        Avatar(Method render, Field builder, Field context, Field notification,
               Field conversationType, Field shortcutIcon) {
            this.render = render;
            this.builder = builder;
            this.context = context;
            this.notification = notification;
            this.conversationType = conversationType;
            this.shortcutIcon = shortcutIcon;
        }
    }

    /** Stable image-rendering interfaces; a missing sub-capability does not disable the others. */
    static final class Image {
        final Method removeFromParent, conversationData, messagingData;
        final Reapply reapply;

        Image(Method removeFromParent, Reapply reapply, Method conversationData, Method messagingData) {
            this.removeFromParent = removeFromParent;
            this.reapply = reapply;
            this.conversationData = conversationData;
            this.messagingData = messagingData;
        }
    }

    static final class Reapply {
        final Method addFlags, hasFlags;
        final int disallowedFlag;

        Reapply(Method addFlags, Method hasFlags, int disallowedFlag) {
            this.addFlags = addFlags;
            this.hasFlags = hasFlags;
            this.disallowedFlag = disallowedFlag;
        }
    }

    static Image image(Class<?> views, Class<?> bundle, Class<?> conversation, Class<?> messaging) {
        Method remove = null;
        Reapply reapply = null;
        try { remove = method(views, "removeFromParent", void.class, int.class); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { /* Keep the system view. */ }
        try {
            Field flag = views.getDeclaredField("FLAG_REAPPLY_DISALLOWED");
            int modifiers = flag.getModifiers();
            if (flag.getType() != int.class || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers))
                throw new NoSuchFieldException("Unsupported RemoteViews reapply flag");
            flag.setAccessible(true);
            reapply = new Reapply(method(views, "addFlags", void.class, int.class),
                    method(views, "hasFlags", boolean.class, int.class), flag.getInt(null));
        } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Keep the placeholder. */ }
        return new Image(remove, reapply, messageData(conversation, bundle), messageData(messaging, bundle));
    }

    private static Method messageData(Class<?> layout, Class<?> bundle) {
        if (layout == null) return null;
        try {
            Method setter = layout.getMethod("setData", bundle);
            if (setter.getReturnType() != void.class || Modifier.isStatic(setter.getModifiers())) return null;
            for (Annotation annotation : setter.getDeclaredAnnotations())
                if ("android.view.RemotableViewMethod".equals(annotation.annotationType().getName())) return setter;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { /* This root cannot render message data. */ }
        return null;
    }

    static Header header(Class<?> builder, Class<?> context, Class<?> notification, Class<?> views)
            throws ReflectiveOperationException {
        String name;
        try {
            builder.getDeclaredMethod("createExpandedContentView");
            name = "createExpandedContentView";
        } catch (NoSuchMethodException legacy) {
            name = "createBigContentView";
        }
        return new Header(method(builder, name, views),
                field(builder, "mContext", context, false), field(builder, "mN", notification, false));
    }

    static Header collapsed(Class<?> builder, Class<?> context, Class<?> notification, Class<?> views)
            throws ReflectiveOperationException {
        return new Header(method(builder, "createContentView", views),
                field(builder, "mContext", context, false), field(builder, "mN", notification, false));
    }

    static Header compactHeadsUp(Class<?> builder, Class<?> context, Class<?> notification, Class<?> views)
            throws ReflectiveOperationException {
        return new Header(method(builder, "createCompactHeadsUpContentView", views),
                field(builder, "mContext", context, false), field(builder, "mN", notification, false));
    }

    static Avatar avatar(Class<?> style, Class<?> baseStyle, Class<?> builder, Class<?> context,
                         Class<?> notification, Class<?> icon, Class<?> views)
            throws ReflectiveOperationException {
        if (!baseStyle.isAssignableFrom(style)) throw new NoSuchFieldException("Unsupported style hierarchy");
        if (constant(style, "CONVERSATION_TYPE_LEGACY") != 0
                || constant(style, "CONVERSATION_TYPE_NORMAL") != 1)
            throw new NoSuchFieldException("Unsupported conversation type constants");
        return new Avatar(method(style, "makeMessagingView", views, int.class),
                field(baseStyle, "mBuilder", builder, false),
                field(builder, "mContext", context, false), field(builder, "mN", notification, false),
                field(style, "mConversationType", int.class, true),
                field(style, "mShortcutIcon", icon, true));
    }

    private static Method method(Class<?> type, String name, Class<?> returns, Class<?>... args)
            throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name, args);
        if (method.getReturnType() != returns || Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodException("Unsupported notification rendering method: " + name);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> type, String name, Class<?> expected, boolean writable)
            throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        int modifiers = field.getModifiers();
        if (field.getType() != expected || Modifier.isStatic(modifiers)
                || (writable && Modifier.isFinal(modifiers)))
            throw new NoSuchFieldException("Unsupported notification rendering field: " + name);
        field.setAccessible(true);
        return field;
    }

    private static int constant(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers()))
            throw new NoSuchFieldException("Unsupported conversation constant: " + name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private NotificationAppearanceHookProfile() {}
}
