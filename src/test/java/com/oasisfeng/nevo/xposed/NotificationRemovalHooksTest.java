package com.oasisfeng.nevo.xposed;

import org.junit.Test;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class NotificationRemovalHooksTest {
    @Test public void prefersTheCompleteFourArgumentSignature() {
        assertEquals(4, resolve(Both.class, Stats.class).getParameterCount());
    }

    @Test public void fallsBackWhenStatsExistsButTheFourArgumentMethodDoesNot() {
        assertEquals(3, resolve(Three.class, Stats.class).getParameterCount());
    }

    @Test public void usesThreeArgumentsWhenStatsIsUnavailable() {
        assertEquals(3, resolve(Three.class, null).getParameterCount());
    }

    @Test public void rejectsAnInvalidReturnType() {
        assertThrows(NoSuchMethodError.class, () -> resolve(Wrong.class, null));
    }

    @Test public void rejectsStaticCallbacks() {
        assertThrows(NoSuchMethodError.class, () -> resolve(Static.class, null));
    }

    @Test public void installsTheSameMethodOnlyOnce() {
        NotificationRemovalHooks hooks = new NotificationRemovalHooks();
        AtomicInteger calls = new AtomicInteger();
        assertTrue(hooks.installOnce(resolve(Three.class, null), ignored -> calls.incrementAndGet()));
        assertFalse(hooks.installOnce(resolve(Three.class, null), ignored -> calls.incrementAndGet()));
        assertEquals(1, calls.get());
    }

    @Test public void retriesAfterAnInstallationFailure() {
        NotificationRemovalHooks hooks = new NotificationRemovalHooks();
        Method method = resolve(Three.class, null);
        assertThrows(IllegalStateException.class, () -> hooks.installOnce(method, ignored -> { throw new IllegalStateException(); }));
        assertTrue(hooks.installOnce(method, ignored -> {}));
    }

    private static Method resolve(Class<?> listener, Class<?> stats) {
        return NotificationRemovalHooks.findMethod(listener, Notice.class, Ranking.class, stats);
    }
    private static final class Notice {}
    private static final class Ranking {}
    private static final class Stats {}
    private static class Three { public void onNotificationRemoved(Notice n, Ranking r, int reason) {} }
    private static final class Both extends Three { public void onNotificationRemoved(Notice n, Ranking r, Stats s, int reason) {} }
    private static final class Wrong { public int onNotificationRemoved(Notice n, Ranking r, int reason) { return 0; } }
    private static final class Static { public static void onNotificationRemoved(Notice n, Ranking r, int reason) {} }
}
