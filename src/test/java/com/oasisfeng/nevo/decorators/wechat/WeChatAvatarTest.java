package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeChatAvatarTest {
    public static class Avatar { public static Object a(String key) { return null; } }
    public static class Instance { public Object a(String key) { return null; } }
    public static class WrongResult { public static String a(String key) { return null; } }
    public static class WrongArgument { public static Object a(Object key) { return null; } }

    @Test public void onlyVerifiedVersionPairsEnableLookup() {
        assertTrue(WeChatAvatar.supported("8.0.79", 3182));
        assertTrue(WeChatAvatar.supported("8.0.79", 3183));
        assertFalse(WeChatAvatar.supported("8.0.79", 3200));
        assertFalse(WeChatAvatar.supported("8.0.80", 3183));
        assertFalse(WeChatAvatar.supported(null, 3182));
    }

    @Test public void exactStaticSignatureRequired() throws Exception {
        assertEquals(Avatar.class, WeChatAvatar.resolve(Avatar.class, Object.class).getDeclaringClass());
        for (Class<?> invalid : new Class<?>[]{Instance.class, WrongResult.class, WrongArgument.class}) {
            try {
                WeChatAvatar.resolve(invalid, Object.class);
                fail("Accepted invalid avatar lookup");
            } catch (NoSuchMethodException expected) { }
        }
    }
}
