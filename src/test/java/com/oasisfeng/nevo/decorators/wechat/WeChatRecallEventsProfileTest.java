package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.content.Context;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook.MethodHookParam;
import org.junit.Test;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class WeChatRecallEventsProfileTest {
    private static final WeChatProfileSelector.BuildId UNKNOWN_79 =
            new WeChatProfileSelector.BuildId("8.0.79", 3183, 100);

    @Test public void unknown3183RequiresFullRecallDescriptorGraph() {
        assertEquals(WeChatProfileSelector.MatchKind.EXPERIMENTAL,
                select(UNKNOWN_79, NotificationItem.class, Revoke79.class, Payload79.class, Message79.class).kind);
        assertEquals(WeChatProfileSelector.MatchKind.NO_MATCH,
                select(UNKNOWN_79, BadNotificationItem.class, Revoke79.class, Payload79.class, Message79.class).kind);
        assertEquals(WeChatProfileSelector.MatchKind.NO_MATCH,
                select(UNKNOWN_79, NotificationItem.class, BadRevoke79.class, Payload79.class, Message79.class).kind);
    }

    @Test public void unknown77RequiresUniqueRecallProfile() {
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.77", 9999, 100);
        assertEquals(WeChatProfileSelector.MatchKind.EXPERIMENTAL,
                select77(build, false).kind);
        assertEquals(WeChatProfileSelector.MatchKind.AMBIGUOUS,
                select77(build, true).kind);
    }

    @Test public void secondHookFailureLeavesFirstCallbackInertEvenAfterRetry() throws Exception {
        Map<String, Class<?>> classes = new HashMap<>();
        classes.put("com.tencent.mm.booter.notification.NotificationItem", NotificationItem.class);
        classes.put("com.tencent.mm.autogen.events.RevokeMsgEvent", Revoke79.class);
        classes.put("com.tencent.mm.sdk.event.IEvent", EventBase.class);
        classes.put("en.js", Payload79.class);
        classes.put("com.tencent.mm.storage.e9", Message79.class);
        WeChatRecallEvents.Resolved resolved = WeChatRecallEvents.resolve(
                WeChatRecallEvents.Profile.forVersion("8.0.79", 3182), lookup(classes));
        WeChatRecallEvents events = new WeChatRecallEvents();
        List<XC_MethodHook> callbacks = new ArrayList<>();
        int[] removed = {0}, delivered = {0};
        assertFalse(events.installResolved(resolved, (talker, id) -> delivered[0]++, (method, callback) -> {
            callbacks.add(callback);
            if (callbacks.size() == 2) throw new IllegalStateException("synthetic second hook failure");
            return () -> removed[0]++;
        }));
        assertFalse(events.ready());
        assertEquals(1, removed[0]);
        assertEquals(2, callbacks.size());
        assertTrue(events.installResolved(resolved, (talker, id) -> {}, (method, callback) -> () -> {}));
        assertTrue(events.ready());
        Method before = callbacks.get(0).getClass().getDeclaredMethod("beforeHookedMethod", MethodHookParam.class);
        before.setAccessible(true);
        Revoke79 event = new Revoke79();
        event.g = new Payload79();
        event.g.c = new Message79();
        MethodHookParam param = new MethodHookParam();
        param.thisObject = event;
        before.invoke(callbacks.get(0), param);
        assertEquals(0, delivered[0]);
    }

    private static WeChatProfileSelector.Selection<WeChatRecallEvents.Profile> select(
            WeChatProfileSelector.BuildId build, Class<?> item, Class<?> event, Class<?> payload, Class<?> message) {
        Map<String, Class<?>> classes = new HashMap<>();
        classes.put("com.tencent.mm.booter.notification.NotificationItem", item);
        classes.put("com.tencent.mm.autogen.events.RevokeMsgEvent", event);
        classes.put("com.tencent.mm.sdk.event.IEvent", EventBase.class);
        classes.put("en.js", payload);
        classes.put("com.tencent.mm.storage.e9", message);
        return WeChatRecallEvents.select(build, new WeChatProfileSelector(1), new MemoryCache(), lookup(classes));
    }

    private static WeChatProfileSelector.Selection<WeChatRecallEvents.Profile> select77(
            WeChatProfileSelector.BuildId build, boolean both) {
        Map<String, Class<?>> classes = new HashMap<>();
        classes.put("com.tencent.mm.booter.notification.NotificationItem", NotificationItem.class);
        classes.put("com.tencent.mm.autogen.events.RevokeMsgEvent", Revoke77.class);
        classes.put("com.tencent.mm.sdk.event.IEvent", EventBase.class);
        classes.put("en.gs", Payload77.class);
        if (both) classes.put("fm.fs", Payload77.class);
        classes.put("com.tencent.mm.storage.e9", Message77.class);
        return WeChatRecallEvents.select(build, new WeChatProfileSelector(1), new MemoryCache(), lookup(classes));
    }

    private static WeChatRecallEvents.NameResolver lookup(Map<String, Class<?>> classes) {
        return name -> {
            Class<?> type = classes.get(name);
            if (type == null) throw new ClassNotFoundException(name);
            return type;
        };
    }

    public static class NotificationItem {
        public Notification f;
        public String h;
        public long i;
        public void a(Context context) {}
    }
    public static class BadNotificationItem {
        public Object f;
        public String h;
        public long i;
        public void a(Context context) {}
    }
    public static class EventBase { public boolean e() { return true; } }
    public static class Revoke79 extends EventBase { public Payload79 g; }
    public static class BadRevoke79 extends EventBase { public Object g; }
    public static class Payload79 { public Message79 c; }
    public static class Message79 {
        public long I0() { return 1; }
        public String N0() { return "synthetic"; }
    }
    public static class Revoke77 extends EventBase { public Payload77 g; }
    public static class Payload77 { public Message77 c; }
    public static class Message77 {
        public long J0() { return 1; }
        public long K0() { return 1; }
        public String N0() { return "synthetic"; }
        public String Q0() { return "synthetic"; }
    }
    private static class MemoryCache implements WeChatProfileSelector.Cache {
        final Map<String, String> values = new HashMap<>();
        private String key(String f, WeChatProfileSelector.BuildId b, int r) { return WeChatProfileSelector.cacheKey(f,b,r); }
        public String read(String f, WeChatProfileSelector.BuildId b, int r) { return values.get(key(f,b,r)); }
        public void write(String f, WeChatProfileSelector.BuildId b, int r, String id) { values.put(key(f,b,r),id); }
        public void remove(String f, WeChatProfileSelector.BuildId b, int r) { values.remove(key(f,b,r)); }
    }
}
