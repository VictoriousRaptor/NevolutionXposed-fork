package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class InitialAvatarGateTest {
    private long now;
    private final TreeMap<Long, List<Runnable>> tasks = new TreeMap<>();
    private final List<String> posted = new ArrayList<>();
    private final InitialAvatarGate<String, String, String> gate = new InitialAvatarGate<>(
            () -> now, (task, delay) -> tasks.computeIfAbsent(now + delay, ignored -> new ArrayList<>()).add(task));
    private void advance(long time) {
        while (!tasks.isEmpty() && tasks.firstKey() <= time) {
            now = tasks.firstKey();
            for (Runnable task : tasks.pollFirstEntry().getValue()) task.run();
        }
        now = time;
    }
    private boolean offer(String key, String text, java.util.function.Supplier<String> avatar) {
        return gate.offer(key, text, () -> true, avatar, (n, a) -> posted.add(n + ":" + a));
    }
    @Test public void readyAvatarPostsWithoutWaiting() {
        assertTrue(offer("a", "first", () -> "icon"));
        advance(0);
        assertEquals(Collections.singletonList("first:icon"), posted);
    }
    @Test public void coldAvatarArrivesBeforeDeadline() {
        offer("a", "first", () -> now >= 150 ? "icon" : null);
        advance(149); assertTrue(posted.isEmpty());
        advance(150); assertEquals(Collections.singletonList("first:icon"), posted);
        advance(1000); assertEquals(1, posted.size());
    }
    @Test public void timeoutPostsExactlyOnceAt500ms() {
        offer("a", "first", () -> null);
        advance(499); assertTrue(posted.isEmpty());
        advance(500); assertEquals(Collections.singletonList("first:null"), posted);
        advance(5000); assertEquals(1, posted.size());
    }
    @Test public void newerMessageReplacesOldWithoutExtendingDeadline() {
        offer("a", "old", () -> null); advance(450);
        offer("a", "new", () -> null); advance(500);
        assertEquals(Collections.singletonList("new:null"), posted);
    }
    @Test public void cancelAndCancelAllDoNotResurrect() {
        offer("a", "a", () -> null); offer("b", "b", () -> null);
        gate.cancel("a"); advance(500);
        assertEquals(Collections.singletonList("b:null"), posted);
        offer("c", "c", () -> null); gate.clear(); advance(1000);
        assertEquals(1, posted.size());
    }
    @Test public void invalidReadOrReplacedConversationNeverPosts() {
        AtomicBoolean valid = new AtomicBoolean(true);
        gate.offer("a", "first", valid::get, () -> null, (n, a) -> posted.add(n));
        advance(100); valid.set(false); advance(1000);
        assertTrue(posted.isEmpty());
    }
    @Test public void cancellationDuringLoadCannotPost() {
        offer("a", "first", () -> { gate.cancel("a"); return "icon"; });
        advance(1000); assertTrue(posted.isEmpty());
    }
    @Test public void contactsHaveIndependentDeadlines() {
        offer("a", "a", () -> null); advance(200);
        offer("b", "b", () -> null); advance(500);
        assertEquals(Collections.singletonList("a:null"), posted);
        advance(700); assertEquals(Arrays.asList("a:null", "b:null"), posted);
    }
    @Test public void publishingCanReenterWithoutDroppingNewRequest() {
        gate.offer("a", "first", () -> true, () -> "icon", (n, a) -> {
            posted.add(n); offer("a", "next", () -> null);
        });
        advance(500); assertEquals(Arrays.asList("first", "next:null"), posted);
    }
}
