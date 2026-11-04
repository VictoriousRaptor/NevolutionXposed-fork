package com.oasisfeng.nevo.decorators.wechat;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import com.oasisfeng.nevo.xposed.R;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;

/** Real framework capability checks; never posts a notification or accesses WeChat. */
@android.annotation.SuppressLint("BlockedPrivateApi") // Verify the optional interfaces used by the injected renderer.
final class ImageRenderCapabilitiesInstrumentation {
    static void remoteViewsRemovalAndCache(Instrumentation instrumentation) {
        NotificationAppearanceHookProfile.Image cached = capabilities();
        Method actual = instanceMethod(RemoteViews.class, "removeFromParent", void.class, int.class);
        if (actual == null) {
            check(cached.removeFromParent == null, "unsupported removal interface was cached as available");
            throw new MessageIdentityInstrumentation.Skipped("RemoteViews.removeFromParent(int): void is unavailable");
        }
        check(actual.equals(cached.removeFromParent), "supported removal interface was not cached");
        Context context = instrumentation.getTargetContext();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                RemoteViews source = views(context);
                View before = source.apply(context, new FrameLayout(context));
                check(before.findViewById(android.R.id.text1) != null
                        && before.findViewById(android.R.id.text2) != null, "removal fixture lost its children");
                RemoteViews changed = source.clone();
                cached.removeFromParent.invoke(changed, android.R.id.text1);
                View after = changed.apply(context, new FrameLayout(context));
                check(after.findViewById(android.R.id.text1) == null
                        && after.findViewById(android.R.id.text2) != null, "cached removal did not remove only its target");
                check(before.findViewById(android.R.id.text1) != null, "removal mutated the original view hierarchy");
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Cached framework removal failed", failure.get());
    }

    static void remoteViewsReapplyFlag(Context context) {
        NotificationAppearanceHookProfile.Image cached = capabilities();
        Method add = instanceMethod(RemoteViews.class, "addFlags", void.class, int.class);
        Method has = instanceMethod(RemoteViews.class, "hasFlags", boolean.class, int.class);
        Integer flag = reapplyFlag();
        if (add == null || has == null || flag == null) {
            check(cached.reapply == null, "unsupported reapply interfaces were cached as available");
            throw new MessageIdentityInstrumentation.Skipped("RemoteViews reapply flag/addFlags/hasFlags signature is unavailable");
        }
        check(cached.reapply != null && add.equals(cached.reapply.addFlags)
                && has.equals(cached.reapply.hasFlags) && flag == cached.reapply.disallowedFlag,
                "supported reapply interfaces were not cached");
        RemoteViews views = views(context);
        check(ImagePreviewPresentation.disallowReapply(views), "cached reapply flag could not be set and verified");
        check(ImagePreviewPresentation.hasReapplyDisallowed(views), "cached reapply flag was not retained");
        try {
            check((Boolean) has.invoke(views, flag), "framework did not observe the cached reapply flag");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Framework reapply verification failed", failure);
        }
    }

    static void conversationMessageData() {
        messageData("com.android.internal.widget.ConversationLayout", capabilities().conversationData);
    }

    static void messagingMessageData() {
        messageData("com.android.internal.widget.MessagingLayout", capabilities().messagingData);
    }

    private static NotificationAppearanceHookProfile.Image capabilities() {
        NotificationAppearanceHookProfile.Image cached = ImagePreviewPresentation.imageCapabilities();
        check(cached == ImagePreviewPresentation.imageCapabilities(), "image capabilities were resolved more than once");
        return cached;
    }

    private static void messageData(String name, Method cached) {
        Method actual = null;
        try {
            Class<?> root = Class.forName(name, false, RemoteViews.class.getClassLoader());
            Method candidate = root.getMethod("setData", Bundle.class);
            if (candidate.getReturnType() == void.class && !Modifier.isStatic(candidate.getModifiers()))
                for (Annotation annotation : candidate.getDeclaredAnnotations())
                    if ("android.view.RemotableViewMethod".equals(annotation.annotationType().getName())) actual = candidate;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Report this root separately below. */ }
        if (actual == null) {
            check(cached == null, "unsupported message-data setter was cached as available");
            throw new MessageIdentityInstrumentation.Skipped(name + " has no supported Remotable setData(Bundle): void");
        }
        check(actual.equals(cached), name + " has a supported setter missing from the cache");
    }

    private static Method instanceMethod(Class<?> type, String name, Class<?> returns, Class<?>... arguments) {
        try {
            Method method = type.getDeclaredMethod(name, arguments);
            if (method.getReturnType() != returns || Modifier.isStatic(method.getModifiers())) return null;
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    private static Integer reapplyFlag() {
        try {
            Field field = RemoteViews.class.getDeclaredField("FLAG_REAPPLY_DISALLOWED");
            int modifiers = field.getModifiers();
            if (field.getType() != int.class || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) return null;
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    private static RemoteViews views(Context context) {
        return new RemoteViews(context.getPackageName(), R.layout.remote_views_image_capability_fixture);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private ImageRenderCapabilitiesInstrumentation() {}
}
