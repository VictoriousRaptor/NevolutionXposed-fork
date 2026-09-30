package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Loads the installed WeChat code without initialization, hooks, database access or message sends. */
final class WeChatDescriptorChecks {
    private final ClassLoader loader;
    private int checks;
    private WeChatDescriptorChecks(ClassLoader loader) { this.loader = loader; }

    static int verify(Context context) throws Exception {
        android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo("com.tencent.mm", 0);
        WeChatImageProfile p = WeChatImageProfile.forVersion(info.versionName, info.versionCode);
        if (p == null) throw new AssertionError("No exact installed image profile");
        Context wechat = context.createPackageContext("com.tencent.mm",
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        WeChatDescriptorChecks v = new WeChatDescriptorChecks(wechat.getClassLoader());
        Class<?> msg = v.type(p.message), state = v.type(p.state), simple = v.type(p.simple);
        v.method(state, "d", Object.class, String.class);
        v.field(simple, "d", int.class);
        v.method(simple, "getString", String.class, int.class);
        v.method(simple, "getLong", long.class, int.class);
        v.method(simple, "getInteger", int.class, int.class);
        v.field(msg, "field_msgSvrId", long.class);
        v.method(msg, p.talker, String.class);
        v.method(msg, p.sender, int.class);
        v.method(msg, p.serverId, long.class);
        v.method(msg, "getMsgId", long.class);
        v.method(msg, "getCreateTime", long.class);
        v.method(msg, "getType", int.class);
        Class<?> flow = v.type(p.flow), result = v.type(p.result);
        v.method(flow, "l", result, state);
        v.method(flow, "handleDataFromRemote", result, state, v.type(p.remote));
        v.method(flow, "handleDataFromFile", result, state, v.type(p.local));
        v.method(v.type(p.pathOwner), p.pathMethod, String.class, msg, String.class, boolean.class);
        v.staticMethod(v.type(p.vfs), "i", String.class, String.class, boolean.class);

        Class<?> core = v.type(p.core), db = v.type(p.database);
        v.staticMethod(v.type(p.kernel), p.coreGet, core);
        int fields = 0;
        for (Field f : core.getDeclaredFields()) if (f.getType() == db) fields++;
        if (fields != 1) throw new AssertionError("Ambiguous database wrapper");
        v.checks++;
        v.method(db, p.query, Cursor.class, String.class, String[].class, int.class);
        v.staticMethod(v.type(p.manager), "c", v.type(p.serviceBase), Class.class);
        Class<?> service = v.type(p.service), implementation = v.type(p.implementation);
        if (!service.isAssignableFrom(implementation)) throw new AssertionError("Wrong image service");
        v.method(implementation, p.downloadGet, v.type(p.downloadInterface));
        Class<?> pair = v.type("com.tencent.mm.plugin.msg.MsgIdTalker");
        pair.getDeclaredConstructor(long.class, String.class); v.checks++;
        Class<?> callback = v.type(p.callback);
        if (!callback.isInterface()) throw new AssertionError("Callback is not an interface");
        v.method(v.type(p.download), "b", int.class, long.class, pair, int.class, Object.class,
                int.class, callback, int.class, boolean.class);
        v.staticMethod(v.type("com.tencent.mm.plugin.gif.MMWXGFJNI"), "wxam2PicBuf",
                byte[].class, byte[].class, int.class, int.class);

        WeChatAppReplyEvents.Profile app = WeChatAppReplyEvents.Profile.forVersion(info.versionName, info.versionCode);
        if (app == null) throw new AssertionError("No exact app reply profile");
        if (v.method(msg, app.setStatus, void.class, int.class).getDeclaringClass() != msg)
            throw new AssertionError("Status override missing");
        v.method(msg, app.status, int.class);
        for (String excluded : app.excluded) v.method(msg, excluded, boolean.class);

        WeChatRecallEvents.Profile recall = WeChatRecallEvents.Profile.forVersion(info.versionName, info.versionCode);
        if (recall == null) throw new AssertionError("No exact recall profile");
        Class<?> item = v.type("com.tencent.mm.booter.notification.NotificationItem");
        v.field(item, "f", Notification.class); v.field(item, "h", String.class); v.field(item, "i", long.class);
        v.method(item, "a", void.class, Context.class);
        Class<?> event = v.type("com.tencent.mm.autogen.events.RevokeMsgEvent"), payload = v.type(recall.payload);
        Class<?> base = v.type("com.tencent.mm.sdk.event.IEvent");
        if (event.getSuperclass() != base) throw new AssertionError("Wrong event base");
        v.field(event, "g", payload); v.field(payload, "c", msg); v.method(base, "e", boolean.class);

        WeChatReplyProfile.Resolved reply = WeChatReplyProfile.forPackage(info.versionName, info.versionCode)
                .resolve(v.loader, info.versionName, info.versionCode);
        if (!reply.isUsable() || reply.helperClass == null || reply.gateMethods.size() != 3)
            throw new AssertionError("Reply profile did not resolve");
        v.method(reply.receiverClass, "onReceive", void.class, Context.class, Intent.class);
        v.staticMethod(reply.helperClass, "b", Bundle.class, Intent.class);
        v.checks++;
        return v.checks;
    }

    private Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, false, loader); }
    private Method method(Class<?> c, String name, Class<?> returns, Class<?>... args) throws Exception {
        for (Class<?> cursor = c; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Method m = cursor.getDeclaredMethod(name, args);
                if (m.getReturnType() != returns) throw new AssertionError("Return type: " + c.getName() + "." + name);
                checks++; return m;
            } catch (NoSuchMethodException absent) { /* inherited descriptor */ }
        }
        throw new NoSuchMethodException(c.getName() + "." + name);
    }
    private void staticMethod(Class<?> c, String name, Class<?> returns, Class<?>... args) throws Exception {
        if (!Modifier.isStatic(method(c, name, returns, args).getModifiers())) throw new AssertionError("Static required");
    }
    private void field(Class<?> c, String name, Class<?> type) throws Exception {
        for (Class<?> cursor = c; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                if (cursor.getDeclaredField(name).getType() != type) throw new AssertionError("Field type: " + name);
                checks++; return;
            } catch (NoSuchFieldException absent) { /* inherited field */ }
        }
        throw new NoSuchFieldException(name);
    }
}
