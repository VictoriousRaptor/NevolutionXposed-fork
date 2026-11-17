package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class VoiceTranscriptionQueueTest {
    static class Loop implements VoiceTranscriptionQueue.Scheduler {
        record Work(long at, long sequence, Runnable action) {}
        long now, sequence;
        final PriorityQueue<Work> work = new PriorityQueue<>(Comparator.comparingLong(Work::at).thenComparingLong(Work::sequence));
        public void later(Runnable action, long delay) { work.add(new Work(now + delay, sequence++, action)); }
        void advance(long millis) {
            long until = now + millis;
            while (!work.isEmpty() && work.peek().at <= until) { Work next = work.remove(); now = next.at; next.action.run(); }
            now = until;
        }
    }
    static class Native implements VoiceTranscriptionQueue.Backend {
        int requests, checks;
        boolean ready = true;
        String cached;
        Consumer<String> callback;
        public String cached() { return cached; }
        public boolean ready() { checks++; return ready; }
        public void transcribe(Consumer<String> callback) { requests++; this.callback = callback; }
    }
    static VoiceTranscriptionQueue.Identity id(long value) {
        return new VoiceTranscriptionQueue.Identity("synthetic-account", "synthetic-peer", value, value + 100);
    }
    @Test public void cachedResultDoesNotRequestNativeTranscription() {
        Loop loop = new Loop(); VoiceTranscriptionQueue queue = new VoiceTranscriptionQueue(() -> loop.now, loop);
        Native service = new Native(); service.cached = "cached text"; List<String> results = new ArrayList<>();
        queue.submit(id(1), service, () -> true, results::add); loop.advance(0);
        assertEquals(List.of("cached text"), results); assertEquals(0, service.requests);
    }
    @Test public void sameMessageDeduplicatesButDifferentIdsSerializeAndLateTimeoutCannotFinishNext() {
        Loop loop = new Loop(); VoiceTranscriptionQueue queue = new VoiceTranscriptionQueue(() -> loop.now, loop);
        Native first = new Native(), second = new Native(); List<String> results = new ArrayList<>();
        queue.submit(id(1), first, () -> true, results::add);
        queue.submit(id(1), first, () -> true, results::add);
        queue.submit(id(2), second, () -> true, results::add); loop.advance(0);
        assertEquals(1, first.requests); assertEquals(0, second.requests);
        loop.advance(60_000); assertEquals(1, second.requests);
        first.callback.accept("late"); loop.advance(0); assertTrue(results.isEmpty());
        second.callback.accept("same voice text"); loop.advance(0);
        assertEquals(List.of("same voice text"), results);
    }
    @Test public void clearRejectsOldCallbacksWithoutRetiringNewTask() {
        Loop loop = new Loop(); VoiceTranscriptionQueue queue = new VoiceTranscriptionQueue(() -> loop.now, loop);
        Native service = new Native(), next = new Native(); List<String> results = new ArrayList<>();
        queue.submit(id(1), service, () -> true, results::add); loop.advance(0);
        queue.submit(id(2), next, () -> true, text -> fail("Cleared queued task ran"));
        queue.clear();
        loop.advance(1000);
        assertEquals(0, next.requests);
        queue.submit(id(1), next, () -> true, results::add); loop.advance(0);
        service.callback.accept("late"); loop.advance(0);
        assertTrue(results.isEmpty()); assertEquals(1, service.requests);
        next.callback.accept("current"); loop.advance(0);
        assertEquals(List.of("current"), results);
    }
    @Test public void waitsAreBoundedAndDoNotBlockNextTaskForever() {
        Loop loop = new Loop(); VoiceTranscriptionQueue queue = new VoiceTranscriptionQueue(() -> loop.now, loop);
        Native waiting = new Native(), next = new Native(); waiting.ready = false;
        queue.submit(id(1), waiting, () -> true, text -> fail());
        queue.submit(id(2), next, () -> true, text -> {}); loop.advance(20_000);
        assertEquals(20, waiting.checks); assertEquals(0, waiting.requests); assertEquals(1, next.requests);
    }
    @Test public void invalidatedRoundRetiresTaskBeforeTimeoutAndCannotPublish() {
        Loop loop = new Loop(); VoiceTranscriptionQueue queue = new VoiceTranscriptionQueue(() -> loop.now, loop);
        boolean[] live = {true}; Native first = new Native(), next = new Native();
        queue.submit(id(1), first, () -> live[0], text -> fail()); loop.advance(0);
        queue.submit(id(2), next, () -> true, text -> {});
        live[0] = false; loop.advance(1000);
        assertEquals(1, next.requests); first.callback.accept("old round"); loop.advance(0);
    }
    @Test public void emptyResultReleasesQueueAndLengthLimitPreservesSurrogatePair() {
        Loop loop = new Loop(); VoiceTranscriptionQueue queue = new VoiceTranscriptionQueue(() -> loop.now, loop);
        Native first = new Native(), next = new Native();
        queue.submit(id(1), first, () -> true, text -> fail());
        queue.submit(id(2), next, () -> true, text -> {}); loop.advance(0);
        first.callback.accept(""); loop.advance(0); assertEquals(1, next.requests);
        assertEquals(3999, VoiceTranscriptionQueue.bounded("a".repeat(3999) + "😀").length());
    }
    @Test public void fullQueueEvictionInvalidatesActiveCallback() {
        Loop loop = new Loop(); VoiceTranscriptionQueue queue = new VoiceTranscriptionQueue(() -> loop.now, loop);
        Native first = new Native(); queue.submit(id(1), first, () -> true, text -> fail()); loop.advance(0);
        for (int i = 2; i <= 65; i++) queue.submit(id(i), new Native(), () -> true, text -> {});
        first.callback.accept("evicted"); loop.advance(0);
    }
}
