package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class WeChatAppReplyEventsProfileTest {
    private static final WeChatProfileSelector.BuildId UNKNOWN_79 =
            new WeChatProfileSelector.BuildId("8.0.79", 3183, 100);

    @Test public void unknown3183RequiresCompleteResolvedMessage() {
        assertEquals(WeChatProfileSelector.MatchKind.EXPERIMENTAL,
                select(UNKNOWN_79, Valid79.class).kind);
        assertEquals(WeChatProfileSelector.MatchKind.NO_MATCH,
                select(UNKNOWN_79, BadStatus79.class).kind);
        assertEquals(WeChatProfileSelector.MatchKind.NO_MATCH,
                select(new WeChatProfileSelector.BuildId("8.0.79.dev", 3183, 100), Valid79.class).kind);
    }

    @Test public void unknown77RequiresUniqueCompleteProfile() {
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.77", 9999, 100);
        assertEquals(WeChatProfileSelector.MatchKind.EXPERIMENTAL, select(build, Valid77A.class).kind);
        assertEquals(WeChatProfileSelector.MatchKind.AMBIGUOUS, select(build, Both77.class).kind);
        assertEquals(WeChatProfileSelector.MatchKind.NO_MATCH, select(build, Object.class).kind);
    }

    private static WeChatProfileSelector.Selection<WeChatAppReplyEvents.Profile> select(
            WeChatProfileSelector.BuildId build, Class<?> message) {
        WeChatAppReplyEvents.NameResolver resolver = name -> {
            if ("com.tencent.mm.storage.e9".equals(name)) return message;
            throw new ClassNotFoundException(name);
        };
        return WeChatAppReplyEvents.select(build, new WeChatProfileSelector(1), new MemoryCache(), resolver);
    }

    public static class Valid79 {
        public void r1(int status) {}
        public int C0() { return 1; }
        public int M0() { return 2; }
        public String N0() { return "synthetic"; }
        public long getMsgId() { return 1; }
        public long getCreateTime() { return 1; }
        public boolean a3() { return false; }
        public boolean M2() { return false; }
        public boolean F2() { return false; }
    }
    public static class BadStatus79 {
        public void r1(int status) {}
        public int C0() { return 1; }
        public String M0() { return "wrong return"; }
        public String N0() { return "synthetic"; }
        public long getMsgId() { return 1; }
        public long getCreateTime() { return 1; }
        public boolean a3() { return false; }
        public boolean M2() { return false; }
        public boolean F2() { return false; }
    }
    public static class Valid77A extends Valid79 { @Override public void r1(int status) {} }
    public static class Both77 extends Valid79 {
        @Override public void r1(int status) {}
        public void u1(int status) {}
        public int A0() { return 1; }
        public int P0() { return 2; }
        public String Q0() { return "synthetic"; }
        public boolean Z2() { return false; }
        public boolean L2() { return false; }
        public boolean E2() { return false; }
    }
    private static class MemoryCache implements WeChatProfileSelector.Cache {
        final Map<String, String> values = new HashMap<>();
        private String key(String f, WeChatProfileSelector.BuildId b, int r) { return WeChatProfileSelector.cacheKey(f,b,r); }
        public String read(String f, WeChatProfileSelector.BuildId b, int r) { return values.get(key(f,b,r)); }
        public void write(String f, WeChatProfileSelector.BuildId b, int r, String id) { values.put(key(f,b,r),id); }
        public void remove(String f, WeChatProfileSelector.BuildId b, int r) { values.remove(key(f,b,r)); }
    }
}
