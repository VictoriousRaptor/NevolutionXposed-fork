package com.oasisfeng.nevo.decorators.wechat;

import com.oasisfeng.nevo.decorators.wechat.ImagePreviewLoader.ReattachRetries;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImagePreviewReattachTest {
    @Test public void activeRequestHasFourAttemptsThenStops() {
        Queue queue = new Queue();
        queue.restart(new ReattachRetries(), () -> true);
        queue.drain();
        assertEquals(4, queue.attempts);
    }

    @Test public void laterNotificationReplacesTheQueuedAttempt() {
        Queue queue = new Queue();
        ReattachRetries retries = new ReattachRetries();
        queue.restart(retries, () -> true);
        queue.restart(retries, () -> true);
        assertEquals(1, queue.callbacks.size());
        queue.drain();
        assertEquals(4, queue.attempts);
    }

    @Test public void refreshRestoresTheFullWindowAfterPartialProgress() {
        Queue queue = new Queue();
        ReattachRetries retries = new ReattachRetries();
        queue.restart(retries, () -> true);
        queue.next();
        queue.next();
        queue.restart(retries, () -> true);
        assertEquals(1, queue.callbacks.size());
        queue.drain();
        assertEquals(6, queue.attempts);
    }

    @Test public void supersededCallbackCannotQueryOrScheduleAgain() {
        Queue queue = new Queue();
        ReattachRetries retries = new ReattachRetries();
        queue.restart(retries, () -> true);
        Runnable stale = queue.callbacks.removeFirst();
        queue.restart(retries, () -> true);
        stale.run();
        assertEquals(0, queue.attempts);
        assertEquals(1, queue.callbacks.size());
    }

    @Test public void refreshDuringAnAttemptDoesNotContinueTheOldChain() {
        Queue queue = new Queue();
        ReattachRetries retries = new ReattachRetries();
        AtomicInteger oldAttempts = new AtomicInteger();
        queue.restart(retries, () -> {
            oldAttempts.incrementAndGet();
            queue.restart(retries, () -> true);
            return true;
        });
        queue.next();
        assertEquals(1, queue.callbacks.size());
        queue.drain();
        assertEquals(1, oldAttempts.get());
        assertEquals(5, queue.attempts);
    }

    @Test public void removedOrExpiredRequestStopsAfterTheCurrentCheck() {
        Queue queue = new Queue();
        queue.restart(new ReattachRetries(), () -> false);
        queue.next();
        assertEquals(1, queue.attempts);
        assertTrue(queue.callbacks.isEmpty());
    }

    @Test public void differentRequestsKeepIndependentWindows() {
        Queue queue = new Queue();
        ReattachRetries first = new ReattachRetries(), second = new ReattachRetries();
        queue.restart(first, () -> true);
        queue.restart(second, () -> true);
        queue.restart(first, () -> true);
        assertEquals(2, queue.callbacks.size());
        queue.drain();
        assertEquals(8, queue.attempts);
    }

    private static final class Queue {
        final Deque<Runnable> callbacks = new ArrayDeque<>();
        int attempts;

        void restart(ReattachRetries retries, BooleanSupplier active) {
            retries.restart(callbacks::addLast, callbacks::remove, () -> {
                attempts++;
                return active.getAsBoolean();
            });
        }

        void next() { callbacks.removeFirst().run(); }

        void drain() {
            while (!callbacks.isEmpty()) {
                assertTrue("Retry chain did not terminate", attempts < 20);
                next();
            }
        }
    }
}
