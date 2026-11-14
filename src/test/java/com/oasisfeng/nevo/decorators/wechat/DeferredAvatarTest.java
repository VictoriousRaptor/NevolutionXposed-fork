package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class DeferredAvatarTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final List<String> rendered = new ArrayList<>();
    private void drain() { while (!pending.isEmpty()) pending.remove().run(); }

    @Test public void coldAvatarRefreshesWithoutASecondMessage() {
        AtomicInteger reads = new AtomicInteger();
        DeferredAvatar.<String>start((task, delay) -> pending.add(task), () -> true,
                () -> reads.incrementAndGet() < 3 ? null : "avatar", rendered::add);
        assertEquals(0, reads.get()); // Never block the original notification post.
        drain();
        assertEquals(3, reads.get());
        assertEquals(java.util.Collections.singletonList("avatar"), rendered);
    }

    @Test public void removedOrReplacedNotificationStopsLoading() {
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger reads = new AtomicInteger();
        DeferredAvatar.<String>start((task, delay) -> pending.add(task), active::get,
                () -> { reads.incrementAndGet(); return null; }, rendered::add);
        pending.remove().run();
        active.set(false);
        drain();
        assertEquals(1, reads.get());
        assertTrue(rendered.isEmpty());
    }

    @Test public void removalDuringLoadingDoesNotResurrectNotification() {
        AtomicBoolean active = new AtomicBoolean(true);
        DeferredAvatar.<String>start((task, delay) -> pending.add(task), active::get,
                () -> { active.set(false); return "avatar"; }, rendered::add);
        drain();
        assertTrue(rendered.isEmpty());
    }

    @Test public void missingAvatarHasAFiniteRetryBudget() {
        AtomicInteger reads = new AtomicInteger();
        DeferredAvatar.<String>start((task, delay) -> pending.add(task), () -> true,
                () -> { reads.incrementAndGet(); return null; }, rendered::add);
        drain();
        assertEquals(6, reads.get());
        assertTrue(rendered.isEmpty());
    }

    @Test public void contactsRetryIndependently() {
        AtomicInteger a = new AtomicInteger(), b = new AtomicInteger();
        DeferredAvatar.<String>start((task, delay) -> pending.add(task), () -> true,
                () -> a.incrementAndGet() < 2 ? null : "A", rendered::add);
        DeferredAvatar.<String>start((task, delay) -> pending.add(task), () -> true,
                () -> b.incrementAndGet() < 3 ? null : "B", rendered::add);
        drain();
        assertEquals(java.util.Arrays.asList("A", "B"), rendered);
    }
}
