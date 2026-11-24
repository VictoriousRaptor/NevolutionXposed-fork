package com.oasisfeng.nevo.decorators.wechat;

import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook.MethodHookParam;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class WeChatReadEventsTest {
    @Test public void requiresExactNameAndCode() {
        assertTrue(WeChatReadEvents.supports("8.0.79", 3182));
        assertTrue(WeChatReadEvents.supports("8.0.79", 3183));
        assertTrue(WeChatReadEvents.supports("8.0.80", 3200));
        assertFalse(WeChatReadEvents.supports("8.0.80", 3183));
        assertFalse(WeChatReadEvents.supports("8.0.79", 3200));
        assertFalse(WeChatReadEvents.supports("8.0.79", 0));
        assertFalse(WeChatReadEvents.supports("8.0.78", 3183));
        assertFalse(WeChatReadEvents.supports("8.0.79.dev", 3183));
        assertFalse(WeChatReadEvents.supports(null, 3183));
    }

    @Test public void resolvesOnlyTheDeclaredPublicBooleanStringMethod() throws Exception {
        Method method = WeChatReadEvents.resolve("8.0.79", name -> {
            assertEquals("com.tencent.mm.storage.l4", name);
            return ConversationStorage.class;
        });
        assertEquals(ConversationStorage.class, method.getDeclaringClass());
        assertEquals("b0", method.getName());
        assertEquals(boolean.class, method.getReturnType());
        assertArrayEquals(new Class<?>[]{String.class}, method.getParameterTypes());
    }

    @Test public void rejectsInheritedPrivateStaticAbstractAndWrongDescriptors() {
        assertInvalid(InheritedStorage.class);
        assertInvalid(PrivateStorage.class);
        assertInvalid(StaticStorage.class);
        assertInvalid(AbstractStorage.class);
        assertInvalid(BoxedStorage.class);
        assertInvalid(WrongArgumentStorage.class);
        assertInvalid(WrongArityStorage.class);
        assertInvalid(Object.class);
    }

    @Test public void missingClassCannotResolveAReadHook() {
        try {
            WeChatReadEvents.resolve("8.0.79", name -> { throw new ClassNotFoundException("synthetic"); });
            fail("Missing classes must not produce a hook target");
        } catch (ClassNotFoundException expected) {
            // The installer turns signature/class failures into an unavailable capability.
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    @Test public void capturesBeforeAndCommitsOnlyAfterSuccessfulOriginal() throws Throwable {
        List<String> observed = new ArrayList<>();
        Harness hook = install(talker -> {
            observed.add("capture:" + talker);
            return () -> observed.add("commit:" + talker);
        });
        MethodHookParam param = call("synthetic-peer");
        hook.before(param);
        assertEquals(Arrays.asList("capture:synthetic-peer"), observed);
        param.setResult(Boolean.TRUE);
        hook.after(param);
        assertEquals(Arrays.asList("capture:synthetic-peer", "commit:synthetic-peer"), observed);
        assertEquals(Boolean.TRUE, param.getResult());
        assertFalse(param.hasThrowable());
    }

    @Test public void capturesOnceAndKeepsTheOriginalConversationAndGeneration() throws Throwable {
        int[] generation = {7};
        List<String> commits = new ArrayList<>();
        Harness hook = install(talker -> {
            int captured = generation[0];
            return () -> commits.add(talker + ":" + captured);
        });
        MethodHookParam param = call("synthetic-original");
        hook.before(param);
        generation[0] = 8;
        param.args[0] = "synthetic-replacement";
        param.thisObject = null;
        param.setResult(Boolean.TRUE);
        hook.after(param);
        assertEquals(Arrays.asList("synthetic-original:7"), commits);
    }

    @Test public void falseNullAndNonBooleanResultsNeverCommit() throws Throwable {
        int[] commits = {0};
        Harness hook = install(talker -> () -> commits[0]++);
        for (Object result : new Object[]{Boolean.FALSE, null, "true", 1}) {
            MethodHookParam param = call("synthetic-peer");
            hook.before(param);
            param.setResult(result);
            hook.after(param);
            assertSame(result, param.getResult());
        }
        assertEquals(0, commits[0]);
    }

    @Test public void originalThrowableIsPreservedAndNeverCommits() throws Throwable {
        int[] commits = {0};
        Harness hook = install(talker -> () -> commits[0]++);
        MethodHookParam param = call("synthetic-peer");
        hook.before(param);
        Throwable original = new IllegalStateException("synthetic");
        param.setThrowable(original);
        hook.after(param);
        assertEquals(0, commits[0]);
        assertSame(original, param.getThrowable());
    }

    @Test public void missingBeforeAndMissingCaptureNeverCommit() throws Throwable {
        int[] captures = {0};
        Harness hook = install(talker -> { captures[0]++; return null; });
        MethodHookParam withoutBefore = call("synthetic-peer");
        withoutBefore.setResult(Boolean.TRUE);
        hook.after(withoutBefore);
        assertEquals(0, captures[0]);
        MethodHookParam withoutTarget = call("synthetic-peer");
        hook.before(withoutTarget);
        withoutTarget.setResult(Boolean.TRUE);
        hook.after(withoutTarget);
        assertEquals(1, captures[0]);
    }

    @Test public void malformedOrNullReceiverNeverCaptures() throws Throwable {
        int[] captures = {0};
        Harness hook = install(talker -> { captures[0]++; return () -> {}; });
        List<MethodHookParam> calls = new ArrayList<>();
        MethodHookParam missingArgs = call("synthetic-peer"); missingArgs.args = null; calls.add(missingArgs);
        MethodHookParam noArgs = call("synthetic-peer"); noArgs.args = new Object[0]; calls.add(noArgs);
        MethodHookParam nullTalker = call(null); calls.add(nullTalker);
        MethodHookParam emptyTalker = call(""); calls.add(emptyTalker);
        MethodHookParam wrongTalker = call("synthetic-peer"); wrongTalker.args[0] = 1; calls.add(wrongTalker);
        MethodHookParam nullReceiver = call("synthetic-peer"); nullReceiver.thisObject = null; calls.add(nullReceiver);
        for (MethodHookParam param : calls) {
            hook.before(param);
            param.setResult(Boolean.TRUE);
            hook.after(param);
        }
        assertEquals(0, captures[0]);
    }

    @Test public void nestedCallbacksCommitTheirOwnCapturedTargets() throws Throwable {
        List<String> commits = new ArrayList<>();
        Harness hook = install(talker -> () -> commits.add(talker));
        MethodHookParam outer = call("synthetic-outer");
        MethodHookParam inner = call("synthetic-inner");
        hook.before(outer);
        hook.before(inner);
        inner.setResult(Boolean.TRUE); hook.after(inner);
        outer.setResult(Boolean.TRUE); hook.after(outer);
        assertEquals(Arrays.asList("synthetic-inner", "synthetic-outer"), commits);
    }

    @Test public void failedCallbackCannotLeaveAPendingCommitForAnotherInvocation() throws Throwable {
        List<String> commits = new ArrayList<>();
        Harness hook = install(talker -> () -> commits.add(talker));
        MethodHookParam failed = call("synthetic-failed");
        hook.before(failed);
        failed.setThrowable(new IllegalStateException("synthetic")); hook.after(failed);
        failed.setResult(Boolean.TRUE); hook.after(failed);
        MethodHookParam next = call("synthetic-next");
        hook.before(next); next.setResult(Boolean.TRUE); hook.after(next);
        assertEquals(Arrays.asList("synthetic-next"), commits);
    }

    @Test public void eachSuccessfulReadMayDeliverTheSameIdempotentCommit() throws Throwable {
        int[] commits = {0};
        Runnable commit = () -> commits[0]++;
        Harness hook = install(talker -> commit);
        for (int i = 0; i < 2; i++) {
            MethodHookParam param = call("synthetic-peer");
            hook.before(param); param.setResult(Boolean.TRUE); hook.after(param);
        }
        assertEquals(2, commits[0]);
    }

    @Test public void captureFailureDoesNotChangeTheObservedResult() throws Throwable {
        Harness hook = install(talker -> { throw new IllegalStateException("synthetic"); });
        MethodHookParam param = call("synthetic-peer");
        hook.before(param); param.setResult(Boolean.TRUE); hook.after(param);
        assertEquals(Boolean.TRUE, param.getResult());
        assertFalse(param.hasThrowable());
    }

    @Test public void commitFailureDoesNotChangeTheObservedResultOrPolluteNextCall() throws Throwable {
        int[] commits = {0};
        Harness hook = install(talker -> () -> {
            if (++commits[0] == 1) throw new IllegalStateException("synthetic");
        });
        for (int i = 0; i < 2; i++) {
            MethodHookParam param = call("synthetic-peer");
            hook.before(param); param.setResult(Boolean.TRUE); hook.after(param);
            assertEquals(Boolean.TRUE, param.getResult());
            assertFalse(param.hasThrowable());
        }
        assertEquals(2, commits[0]);
    }

    @Test public void failedInstallationLeavesRetainedCallbacksInertAndCanRetry() throws Throwable {
        WeChatReadEvents events = new WeChatReadEvents();
        XC_MethodHook[] retained = new XC_MethodHook[1];
        int[] captures = {0};
        WeChatReadEvents.Listener listener = talker -> { captures[0]++; return () -> {}; };
        assertFalse(events.installResolved(target(), listener, (method, callback) -> {
            retained[0] = callback;
            throw new IllegalStateException("synthetic");
        }));
        assertFalse(events.ready());
        MethodHookParam param = call("synthetic-peer");
        invoke(retained[0], "beforeHookedMethod", param);
        param.setResult(Boolean.TRUE); invoke(retained[0], "afterHookedMethod", param);
        assertEquals(0, captures[0]);
        assertTrue(events.installResolved(target(), listener, (method, callback) -> () -> {}));
        assertTrue(events.ready());
        invoke(retained[0], "beforeHookedMethod", param);
        invoke(retained[0], "afterHookedMethod", param);
        assertEquals(0, captures[0]);
    }

    @Test public void nullHookHandleNeverActivatesTheCallback() throws Throwable {
        WeChatReadEvents events = new WeChatReadEvents();
        XC_MethodHook[] retained = new XC_MethodHook[1];
        int[] captures = {0};
        assertFalse(events.installResolved(target(), talker -> { captures[0]++; return () -> {}; },
                (method, callback) -> { retained[0] = callback; return null; }));
        assertFalse(events.ready());
        MethodHookParam param = call("synthetic-peer");
        invoke(retained[0], "beforeHookedMethod", param);
        param.setResult(Boolean.TRUE); invoke(retained[0], "afterHookedMethod", param);
        assertEquals(0, captures[0]);
    }

    @Test public void callbacksStayInertUntilTheHookHandleIsInstalled() throws Throwable {
        WeChatReadEvents events = new WeChatReadEvents();
        int[] captures = {0};
        assertTrue(events.installResolved(target(), talker -> { captures[0]++; return () -> {}; },
                (method, callback) -> {
                    MethodHookParam param = call("synthetic-peer");
                    invoke(callback, "beforeHookedMethod", param);
                    param.setResult(Boolean.TRUE); invoke(callback, "afterHookedMethod", param);
                    return () -> {};
                }));
        assertTrue(events.ready());
        assertEquals(0, captures[0]);
    }

    private static Harness install(WeChatReadEvents.Listener listener) throws Exception {
        Harness harness = new Harness();
        WeChatReadEvents events = new WeChatReadEvents();
        assertTrue(events.installResolved(target(), listener, (method, callback) -> {
            assertEquals(target(), method);
            harness.callback = callback;
            return () -> {};
        }));
        assertTrue(events.ready());
        return harness;
    }

    private static Method target() throws Exception {
        return WeChatReadEvents.resolve("8.0.79", name -> ConversationStorage.class);
    }

    private static MethodHookParam call(String talker) {
        MethodHookParam param = new MethodHookParam();
        param.thisObject = new ConversationStorage();
        param.args = new Object[]{talker};
        return param;
    }

    private static void assertInvalid(Class<?> type) {
        try {
            WeChatReadEvents.resolve("8.0.79", name -> type);
            fail("Accepted an unverified read descriptor: " + type.getSimpleName());
        } catch (ReflectiveOperationException | IllegalStateException expected) {
            // No fallback to a similar, inherited, or inaccessible method.
        }
    }

    private static void invoke(XC_MethodHook callback, String phase, MethodHookParam param) throws Throwable {
        Method method = callback.getClass().getDeclaredMethod(phase, MethodHookParam.class);
        method.setAccessible(true);
        try { method.invoke(callback, param); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private static final class Harness {
        XC_MethodHook callback;
        void before(MethodHookParam param) throws Throwable { invoke(callback, "beforeHookedMethod", param); }
        void after(MethodHookParam param) throws Throwable { invoke(callback, "afterHookedMethod", param); }
    }

    public static class ConversationStorage { public boolean b0(String talker) { return true; } }
    public static class InheritedStorage extends ConversationStorage {}
    public static class PrivateStorage { private boolean b0(String talker) { return true; } }
    public static class StaticStorage { public static boolean b0(String talker) { return true; } }
    public abstract static class AbstractStorage { public abstract boolean b0(String talker); }
    public static class BoxedStorage { public Boolean b0(String talker) { return true; } }
    public static class WrongArgumentStorage { public boolean b0(Object talker) { return true; } }
    public static class WrongArityStorage { public boolean b0(String talker, int status) { return true; } }
}
