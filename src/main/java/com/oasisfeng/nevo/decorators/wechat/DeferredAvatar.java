package com.oasisfeng.nevo.decorators.wechat;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounded, nonblocking retries for a cold avatar. The caller owns notification identity/lifetime. */
final class DeferredAvatar {
    interface Scheduler { void post(Runnable task, long delayMillis); }

    static <T> void start(Scheduler scheduler, BooleanSupplier current, Supplier<T> load, Consumer<T> publish) {
        attempt(scheduler, current, load, publish, 0);
    }

    private static <T> void attempt(Scheduler scheduler, BooleanSupplier current, Supplier<T> load,
                                    Consumer<T> publish, int attempt) {
        scheduler.post(() -> {
            if (!current.getAsBoolean()) return;
            T avatar = load.get();
            if (!current.getAsBoolean()) return;
            if (avatar != null) publish.accept(avatar);
            else if (attempt < 5) attempt(scheduler, current, load, publish, attempt + 1);
        }, 100L << attempt);
    }

    private DeferredAvatar() {}
}
