package com.oasisfeng.nevo.decorators.wechat;

import java.util.LinkedHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Single-threaded, bounded work queue. All calls and callbacks run on the supplied scheduler. */
final class VoiceTranscriptionQueue {
    static final long TTL = 30 * 60_000L;
    static final long TIMEOUT = 60_000L;
    record Identity(String account, String talker, long localId, long serverId) {}
    interface Scheduler { void later(Runnable action, long delay); }
    interface Backend {
        String cached() throws Exception;
        boolean ready() throws Exception;
        void transcribe(Consumer<String> completion) throws Exception;
    }
    private final LongSupplier clock;
    private final Scheduler scheduler;
    private final Consumer<String> diagnostics;
    private final LinkedHashMap<Identity, Task> tasks = new LinkedHashMap<>();
    private Task active;

    VoiceTranscriptionQueue(LongSupplier clock, Scheduler scheduler) {
        this(clock, scheduler, stage -> {});
    }
    VoiceTranscriptionQueue(LongSupplier clock, Scheduler scheduler, Consumer<String> diagnostics) {
        this.clock = clock; this.scheduler = scheduler; this.diagnostics = diagnostics;
    }

    void clear() { active = null; tasks.clear(); }

    void submit(Identity id, Backend backend, BooleanSupplier valid, Consumer<String> result) {
        long now = clock.getAsLong();
        tasks.values().removeIf(t -> now < t.created || now - t.created >= TTL);
        if (tasks.containsKey(id) || !valid.getAsBoolean()) return;
        if (tasks.size() >= 64) {
            Task evicted = tasks.remove(tasks.keySet().iterator().next());
            evicted.done = true;
            if (evicted == active) active = null;
        }
        tasks.put(id, new Task(id, now, backend, valid, result));
        pump();
    }

    private boolean valid(Task task) {
        long age = clock.getAsLong() - task.created;
        return !task.done && tasks.get(task.id) == task && age >= 0 && age < TTL
                && task.valid.getAsBoolean();
    }

    private void pump() {
        if (active != null) return;
        for (Task task : tasks.values()) {
            if (task.done) continue;
            if (!valid(task)) { task.done = true; continue; }
            active = task;
            scheduler.later(() -> prepare(task), 0);
            return;
        }
    }

    private void prepare(Task task) {
        if (active != task) return;
        if (!valid(task)) { finish(task, null); return; }
        try {
            String cached = task.backend.cached();
            if (cached != null && !cached.isEmpty()) { diagnostics.accept("cache_hit"); finish(task, cached); return; }
            if (!task.backend.ready()) {
                if (++task.waits >= 20) { diagnostics.accept("audio_unavailable"); finish(task, null); }
                else scheduler.later(() -> prepare(task), 1000);
                return;
            }
            task.started = clock.getAsLong();
            diagnostics.accept("requested");
            task.backend.transcribe(text -> scheduler.later(() -> finish(task, text), 0));
            scheduler.later(() -> watch(task), 1000);
        } catch (Exception failure) { diagnostics.accept("failed type=" + failure.getClass().getSimpleName()); finish(task, null); }
    }

    private void watch(Task task) {
        if (active != task) return;
        if (!valid(task) || clock.getAsLong() - task.started >= TIMEOUT) {
            diagnostics.accept("expired_or_invalidated"); finish(task, null);
        }
        else scheduler.later(() -> watch(task), 1000);
    }

    private void finish(Task task, String text) {
        // A late callback must never retire a newer task or publish after the timeout boundary.
        if (active != task) return;
        boolean publish = valid(task) && (task.started < 0 || clock.getAsLong() - task.started < TIMEOUT)
                && text != null && !text.isEmpty();
        task.done = true;
        active = null;
        try { if (publish) { diagnostics.accept("completed"); task.result.accept(bounded(text)); } }
        finally { pump(); }
    }

    static String bounded(String text) {
        int end = Math.min(4000, text.length());
        if (end < text.length() && end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    private static final class Task {
        final Identity id;
        final long created;
        final Backend backend;
        final BooleanSupplier valid;
        final Consumer<String> result;
        int waits;
        long started = -1;
        boolean done;
        Task(Identity id, long created, Backend backend, BooleanSupplier valid, Consumer<String> result) {
            this.id = id; this.created = created; this.backend = backend; this.valid = valid; this.result = result;
        }
    }
}
