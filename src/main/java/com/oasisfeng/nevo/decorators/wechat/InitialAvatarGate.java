package com.oasisfeng.nevo.decorators.wechat;

import java.util.function.*;
import java.util.HashMap;
import java.util.Map;

/** Pending first posts. All calls, including scheduled tasks, run under the owner's lock. */
final class InitialAvatarGate<K, N, A> {
    private final LongSupplier clock;
    private final DeferredAvatar.Scheduler scheduler;
    private final Map<K, Pending> pending = new HashMap<>();
    private final class Pending {
        final K key;
        final N notification;
        final long deadline;
        final BooleanSupplier valid;
        final Supplier<A> load;
        final BiConsumer<N, A> publish;
        Pending(K key, N notification, long deadline, BooleanSupplier valid, Supplier<A> load, BiConsumer<N, A> publish) {
            this.key = key; this.notification = notification; this.deadline = deadline;
            this.valid = valid; this.load = load; this.publish = publish;
        }
    }
    InitialAvatarGate(LongSupplier clock, DeferredAvatar.Scheduler scheduler) {
        this.clock = clock; this.scheduler = scheduler;
    }
    boolean offer(K key, N notification, BooleanSupplier valid, Supplier<A> load, BiConsumer<N, A> publish) {
        Pending previous = pending.get(key);
        if (previous == null && pending.size() >= 64) return false;
        Pending request = new Pending(key, notification, previous == null ? clock.getAsLong() + 500 : previous.deadline,
                valid, load, publish);
        pending.put(key, request);
        scheduler.post(() -> attempt(request), 0);
        return true;
    }
    private void attempt(Pending request) {
        if (pending.get(request.key) != request) return;
        if (!request.valid.getAsBoolean()) { pending.remove(request.key); return; }
        A avatar = request.load.get();
        if (pending.get(request.key) != request) return;
        if (!request.valid.getAsBoolean()) { pending.remove(request.key); return; }
        long remaining = request.deadline - clock.getAsLong();
        if (avatar != null || remaining <= 0) {
            pending.remove(request.key); // Remove before publishing: notify may re-enter the owner.
            request.publish.accept(request.notification, avatar);
        } else scheduler.post(() -> attempt(request), Math.min(50, remaining));
    }
    boolean contains(K key) { return pending.containsKey(key); }
    N get(K key) { Pending value = pending.get(key); return value == null ? null : value.notification; }
    java.util.List<K> keys() { return new java.util.ArrayList<>(pending.keySet()); }
    void cancel(K key) { pending.remove(key); }
    void clear() { pending.clear(); }
}
