package com.oasisfeng.nevo.decorators.wechat;

import com.oasisfeng.nevo.xposed.compat.XC_MethodHook.MethodHookParam;

import org.junit.Test;

import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WeChatEmojiHooksTest {
    @Test public void requiresBothVerifiedVersionFields() {
        assertTrue(WeChatEmojiHooks.supports("8.0.79", 3182));
        assertFalse(WeChatEmojiHooks.supports("8.0.79", 3180));
        assertFalse(WeChatEmojiHooks.supports("8.0.78", 3182));
        assertFalse(WeChatEmojiHooks.supports(null, 3182));
    }

    @Test public void preservesOriginalUnicodeAndLiteralMarkersInsideNotification() {
        WeChatEmojiHooks.NotificationScope scope = readyScope();
        WeChatEmojiHooks.PreserveEmoji hook = new WeChatEmojiHooks.PreserveEmoji(scope, () -> true);
        scope.beforeHookedMethod(new MethodHookParam());
        for (String text : new String[] { "👌", "收到👌🏽😀", "👨‍👩‍👧‍👦", "[OK]👌[动画表情]", "", null }) {
            MethodHookParam conversion = conversion(text);
            hook.beforeHookedMethod(conversion);
            assertSame(text, conversion.getResult());
        }
        scope.afterHookedMethod(new MethodHookParam());
    }

    @Test public void otherWeChatCallersKeepOriginalConversion() {
        WeChatEmojiHooks.NotificationScope scope = readyScope();
        MethodHookParam conversion = conversion("👌");
        new WeChatEmojiHooks.PreserveEmoji(scope, () -> true).beforeHookedMethod(conversion);
        assertNull(conversion.getResult());
        assertFalse(scope.active());
    }

    @Test public void settingCanTurnOffAndBackOnWithoutReinstalling() {
        AtomicBoolean enabled = new AtomicBoolean();
        WeChatEmojiHooks.NotificationScope scope = readyScope();
        WeChatEmojiHooks.PreserveEmoji hook = new WeChatEmojiHooks.PreserveEmoji(scope, enabled::get);
        scope.beforeHookedMethod(new MethodHookParam());
        MethodHookParam disabled = conversion("👌");
        hook.beforeHookedMethod(disabled);
        assertNull(disabled.getResult());
        enabled.set(true);
        MethodHookParam on = conversion("👌");
        hook.beforeHookedMethod(on);
        assertSame(on.args[0], on.getResult());
        enabled.set(false);
        MethodHookParam off = conversion("👌");
        hook.beforeHookedMethod(off);
        assertNull(off.getResult());
        scope.afterHookedMethod(new MethodHookParam());
    }

    @Test public void partialInstallationNeverChangesConversion() {
        WeChatEmojiHooks.NotificationScope scope = new WeChatEmojiHooks.NotificationScope();
        scope.beforeHookedMethod(new MethodHookParam());
        MethodHookParam conversion = conversion("👌");
        new WeChatEmojiHooks.PreserveEmoji(scope, () -> true).beforeHookedMethod(conversion);
        assertNull(conversion.getResult());
        scope.afterHookedMethod(new MethodHookParam());
    }

    @Test public void nestedBuildAndExceptionalExitDoNotLeakScope() {
        WeChatEmojiHooks.NotificationScope scope = readyScope();
        MethodHookParam outer = new MethodHookParam();
        MethodHookParam inner = new MethodHookParam();
        scope.beforeHookedMethod(outer);
        scope.beforeHookedMethod(inner);
        inner.setThrowable(new IllegalStateException("synthetic builder failure"));
        scope.afterHookedMethod(inner);
        assertTrue(scope.active());
        scope.afterHookedMethod(outer);
        assertFalse(scope.active());
        MethodHookParam next = conversion("👌");
        new WeChatEmojiHooks.PreserveEmoji(scope, () -> true).beforeHookedMethod(next);
        assertNull(next.getResult());
    }

    @Test public void notificationScopeDoesNotCrossThreads() throws Exception {
        WeChatEmojiHooks.NotificationScope scope = readyScope();
        scope.beforeHookedMethod(new MethodHookParam());
        FutureTask<Boolean> outside = new FutureTask<>(scope::active);
        Thread worker = new Thread(outside);
        worker.start();
        try {
            assertFalse(outside.get());
            assertTrue(scope.active());
        } finally {
            scope.afterHookedMethod(new MethodHookParam());
            worker.join();
        }
    }

    @Test public void verifiesExactInstanceSignature() throws Exception {
        WeChatEmojiHooks.instanceMethod(Fixture.class, "convert", String.class, String.class);
    }

    @Test(expected = NoSuchMethodException.class) public void rejectsStaticLookalike() throws Exception {
        WeChatEmojiHooks.instanceMethod(Fixture.class, "staticConvert", String.class, String.class);
    }

    @Test(expected = NoSuchMethodException.class) public void rejectsDifferentReturnType() throws Exception {
        WeChatEmojiHooks.instanceMethod(Fixture.class, "convert", CharSequence.class, String.class);
    }

    @Test(expected = NoSuchMethodException.class) public void rejectsDifferentParameterType() throws Exception {
        WeChatEmojiHooks.instanceMethod(Fixture.class, "convert", String.class, CharSequence.class);
    }

    private static WeChatEmojiHooks.NotificationScope readyScope() {
        WeChatEmojiHooks.NotificationScope scope = new WeChatEmojiHooks.NotificationScope();
        scope.ready = true;
        return scope;
    }

    private static MethodHookParam conversion(String text) {
        MethodHookParam param = new MethodHookParam();
        param.args = new Object[] { text };
        return param;
    }

    public static class Fixture {
        public String convert(String value) { return value; }
        public static String staticConvert(String value) { return value; }
    }
}
