package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;
import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;
import com.oasisfeng.nevo.xposed.compat.XposedHelpers;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/** Adds exact notification IDs and observes recall metadata, without reading message bodies. */
final class WeChatRecallEvents {
    interface Listener { void onRecalled(String talker, long serverId); }
    interface NameResolver { Class<?> find(String name) throws ClassNotFoundException; }
    interface Hooker { Runnable hook(Method method, XC_MethodHook callback) throws Throwable; }
    static final String VERIFIED_KEY = "nevo.wechat.verifiedNotificationKey";
    private final RecallIndex index = new RecallIndex();
    private volatile HookState installed;
    private volatile String lastHookFailureType;
    private static final class HookState { volatile boolean ready; }

    static final class Profile {
        final String label, message, payload, serverId, talker;
        private Profile(String label, String message, String payload, String serverId, String talker) {
            this.label = label; this.message = message; this.payload = payload;
            this.serverId = serverId; this.talker = talker;
        }
        private static final Profile WX79 = new Profile("8.0.79/3182", "com.tencent.mm.storage.e9", "en.js", "I0", "N0");
        private static final Profile WX72 = new Profile("8.0.72/3085", "com.tencent.mm.storage.f9", "pm.ds", "I0", "O0");
        private static final Profile WX78 = new Profile("8.0.78/3180", "com.tencent.mm.storage.e9", "fm.ks", "F0", "N0");
        private static final Profile WX77_3160 = new Profile("8.0.77/3160", "com.tencent.mm.storage.e9", "fm.fs", "K0", "Q0");
        private static final Profile WX77_3141 = new Profile("8.0.77/3141", "com.tencent.mm.storage.e9", "en.gs", "J0", "N0");
        static final List<WeChatProfileSelector.Candidate<Profile>> CANDIDATES = Arrays.asList(
                candidate(WX79, "8.0.79", 3182), candidate(WX72, "8.0.72", 3085),
                candidate(WX78, "8.0.78", 3180), candidate(WX77_3160, "8.0.77", 3160),
                candidate(WX77_3141, "8.0.77", 3141));
        private static WeChatProfileSelector.Candidate<Profile> candidate(Profile profile, String name, long code) {
            return new WeChatProfileSelector.Candidate<>("recall-" + profile.label, name, code, profile);
        }
        static Profile forVersion(String name, long code) {
            for (WeChatProfileSelector.Candidate<Profile> candidate : CANDIDATES)
                if (candidate.versionName.equals(name) && candidate.verifiedCode == code) return candidate.profile;
            return null;
        }
    }

    static String messageClass(String name, long code) {
        Profile profile = Profile.forVersion(name, code);
        return profile == null ? null : profile.message;
    }

    static final class Resolved {
        final Profile profile;
        final Class<?> event;
        final Field notification, itemTalker, itemServerId, eventData, recalledMessage;
        final Method publishNotification, serverId, talker, publishEvent;
        Resolved(Profile profile, Class<?> event, Field notification, Field itemTalker, Field itemServerId,
                 Field eventData, Field recalledMessage, Method publishNotification, Method serverId,
                 Method talker, Method publishEvent) {
            this.profile = profile; this.event = event;
            this.notification = notification; this.itemTalker = itemTalker; this.itemServerId = itemServerId;
            this.eventData = eventData; this.recalledMessage = recalledMessage;
            this.publishNotification = publishNotification; this.serverId = serverId;
            this.talker = talker; this.publishEvent = publishEvent;
        }
    }

    static WeChatProfileSelector.Selection<Profile> select(WeChatProfileSelector.BuildId build,
            WeChatProfileSelector selector, WeChatProfileSelector.Cache cache, NameResolver resolver) {
        return selector.select("recall", build, Profile.CANDIDATES,
                profile -> resolve(profile, resolver) != null, cache);
    }

    static Resolved resolve(Profile profile, ClassLoader loader) throws ReflectiveOperationException {
        return resolve(profile, name -> Class.forName(name, false, loader));
    }

    static Resolved resolve(Profile profile, NameResolver resolver) throws ClassNotFoundException, NoSuchFieldException {
        Class<?> item = resolver.find("com.tencent.mm.booter.notification.NotificationItem");
        Field notification = field(item, "f", Notification.class);
        Field itemTalker = field(item, "h", String.class);
        Field itemServerId = field(item, "i", long.class);
        Method publishNotification = method(item, "a", void.class, Context.class);
        Class<?> event = resolver.find("com.tencent.mm.autogen.events.RevokeMsgEvent");
        Class<?> payload = resolver.find(profile.payload);
        Field eventData = field(event, "g", payload);
        Class<?> message = resolver.find(profile.message);
        Field recalledMessage = field(payload, "c", message);
        Method serverId = method(message, profile.serverId, long.class);
        Method talker = method(message, profile.talker, String.class);
        Class<?> eventBase = resolver.find("com.tencent.mm.sdk.event.IEvent");
        if (event.getSuperclass() != eventBase) throw new IllegalStateException("Unexpected event base");
        Method publishEvent = method(eventBase, "e", boolean.class);
        return new Resolved(profile, event, notification, itemTalker, itemServerId, eventData,
                recalledMessage, publishNotification, serverId, talker, publishEvent);
    }

    void install(Context context, ClassLoader loader, Listener listener, WeChatProfileSelector selector,
            WeChatProfileSelector.Cache cache) {
        if (ready()) return;
        String stage = "version";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(WeChatDecorator.WECHAT_PACKAGE, 0);
            WeChatProfileSelector.BuildId build = WeChatProfileSelector.BuildId.fromPackageInfo(info);
            WeChatProfileSelector.Selection<Profile> choice =
                    select(build, selector, cache, name -> Class.forName(name, false, loader));
            if (choice.profile == null) {
                log("unavailable", "reason=" + choice.kind.name().toLowerCase());
                return;
            }
            stage = "signature";
            Resolved resolved = resolve(choice.profile, loader);
            stage = "hook";
            if (!installResolved(resolved, listener, (method, callback) -> {
                XposedInterface.HookHandle handle = XposedBridge.hookMethod(method, callback);
                return handle::unhook;
            })) {
                log("unavailable", "stage=hook type=" + lastHookFailureType);
                return;
            }
            log("ready", "version=" + build.versionName + " code=" + build.versionCode
                    + " profile=" + choice.profile.label + " match=" + choice.kind.name().toLowerCase());
        } catch (Throwable failure) {
            XposedBridge.rethrowFrameworkError(failure);
            log("unavailable", "stage=" + stage + " type=" + failure.getClass().getSimpleName());
        }
    }

    boolean ready() { HookState state = installed; return state != null && state.ready; }

    boolean installResolved(Resolved resolved, Listener listener, Hooker hooker) {
        HookState state = new HookState();
        Runnable eventUnhook = null, notificationUnhook = null;
        try {
            eventUnhook = hooker.hook(resolved.publishEvent, eventCallback(resolved, listener, state));
            notificationUnhook = hooker.hook(resolved.publishNotification, notificationCallback(resolved, state));
            state.ready = true;
            installed = state;
            return true;
        } catch (Throwable failure) {
            state.ready = false;
            unhook(notificationUnhook);
            unhook(eventUnhook);
            XposedBridge.rethrowFrameworkError(failure);
            lastHookFailureType = failure.getClass().getSimpleName();
            return false;
        }
    }

    private XC_MethodHook eventCallback(Resolved resolved, Listener listener, HookState state) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!state.ready || !resolved.event.isInstance(param.thisObject)) return;
                try {
                    Object value = resolved.recalledMessage.get(resolved.eventData.get(param.thisObject));
                    if (value == null) return;
                    long id = ((Number) resolved.serverId.invoke(value)).longValue();
                    String key = (String) resolved.talker.invoke(value);
                    if (id > 0 && key != null && !key.isEmpty()) {
                        index.add(key, id);
                        listener.onRecalled(key, id);
                    }
                } catch (Exception failure) { log("event_error", "type=" + failure.getClass().getSimpleName()); }
            }
        };
    }

    private XC_MethodHook notificationCallback(Resolved resolved, HookState state) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!state.ready) return;
                try {
                    Notification n = (Notification) resolved.notification.get(param.thisObject);
                    String key = (String) resolved.itemTalker.get(param.thisObject);
                    long id = resolved.itemServerId.getLong(param.thisObject);
                    if (n == null || key == null || key.isEmpty() || id <= 0) return;
                    n.extras.putString(NotificationMessages.KEY, key);
                    n.extras.putBoolean(VERIFIED_KEY, true);
                    n.extras.putLong(NotificationMessages.SERVER_ID, id);
                    long[] recalled = index.ids(key);
                    n.extras.putLongArray(NotificationMessages.RECALLED_IDS, recalled);
                    RecallHistory history = new RecallHistory(); history.addAll(recalled);
                    n.extras.putBoolean(NotificationMessages.RECALL_PROMPT, history.contains(id));
                    if (!history.contains(id)) n.extras.remove(WeChatDecorator.EXTRA_SUPPRESSED_RECALL);
                } catch (Exception failure) { log("identity_error", "type=" + failure.getClass().getSimpleName()); }
            }
        };
    }

    private static void unhook(Runnable handle) {
        if (handle == null) return;
        try { handle.run(); } catch (Throwable failure) {
            XposedBridge.rethrowFrameworkError(failure);
            log("unhook_failed", "type=" + failure.getClass().getSimpleName());
        }
    }

    private static Field field(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (field.getType() != type || Modifier.isStatic(field.getModifiers()))
                    throw new IllegalStateException("Unexpected field signature");
                field.setAccessible(true); return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... arguments) {
        Method method = XposedHelpers.findMethodExact(owner, name, arguments);
        int modifiers = method.getModifiers();
        if (method.getReturnType() != result || Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers))
            throw new IllegalStateException("Unexpected method signature");
        return method;
    }

    private static void log(String stage, String details) {
        if (BuildConfig.DEBUG) Log.d("WeChat.Identity", "source=recall_" + stage + " " + details);
    }
}
