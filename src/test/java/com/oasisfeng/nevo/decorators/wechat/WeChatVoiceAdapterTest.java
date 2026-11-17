package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeChatVoiceAdapterTest {
    @Test public void profilesRequireBothNameAndCodeAndKeepDifferentCacheSignatures() {
        assertNull(WeChatVoiceAdapter.profile("8.0.79", 3182));
        assertNull(WeChatVoiceAdapter.profile("8.0.80", 3200));
        assertEquals("m1", WeChatVoiceAdapter.profile("8.0.79", 3183).cacheGet());
        assertEquals("P1", WeChatVoiceAdapter.profile("8.0.79", 3200).cacheGet());
    }
    public static class Signature {
        public static String staticText() { return ""; }
        public int wrongReturn() { return 0; }
        public static long field;
    }
    public static class BaseMessage { private long field_msgSvrId; }
    public static class Message extends BaseMessage {}
    public enum Resource { AUDIO }
    @Test public void audioResourceUsesTypedValueOfInsteadOfObfuscatedField() throws Exception {
        java.lang.reflect.Method valueOf = WeChatVoiceAdapter.method(Resource.class, "valueOf",
                Resource.class, true, String.class);
        assertSame(Resource.AUDIO, valueOf.invoke(null, "AUDIO"));
        try { WeChatVoiceAdapter.method(Resource.class, "valueOf", Object.class, true, String.class); fail(); }
        catch (NoSuchMethodException expected) { }
    }
    @Test public void privateInheritedMessageIdentityIsResolvedWithoutDecompilerAliases() throws Exception {
        Message message = new Message();
        java.lang.reflect.Field field = WeChatVoiceAdapter.field(Message.class, "field_msgSvrId", long.class);
        field.setLong(message, 123);
        assertEquals(123, field.getLong(message));
    }
    @Test public void methodRejectsWrongReturnAndStaticness() throws Exception {
        try { WeChatVoiceAdapter.method(Signature.class, "staticText", String.class, false); fail(); }
        catch (NoSuchMethodException expected) { }
        try { WeChatVoiceAdapter.method(Signature.class, "wrongReturn", long.class, false); fail(); }
        catch (NoSuchMethodException expected) { }
        assertNotNull(WeChatVoiceAdapter.method(Signature.class, "staticText", String.class, true));
    }
    @Test public void fieldsRejectStaticIdentity() throws Exception {
        try { WeChatVoiceAdapter.field(Signature.class, "field", long.class); fail(); }
        catch (NoSuchFieldException expected) { }
    }
}
