package com.oasisfeng.nevo.sdk;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class NotificationArchiveTest {
    @Test public void repeatedRepliesRemovalAndNextNotificationKeepAccurateCount() {
        NotificationArchive<Integer, String> archive = new NotificationArchive<>(120, 20);
        for (int round = 0; round < 100; round++) {
            archive.add(1, "incoming");
            archive.add(1, "reply-recast");
            archive.add(1, "next-incoming");
            assertEquals(3, archive.size());
            archive.remove(1);
            archive.remove(1);
            assertEquals(0, archive.size());
            assertNull(archive.latest(1));
        }
        archive.add(1, "new-round");
        assertEquals(1, archive.size());
        assertEquals("new-round", archive.latest(1));
    }

    @Test public void keepsOnlyNewestTwentyPerConversation() {
        NotificationArchive<Integer, Integer> archive = new NotificationArchive<>(120, 20);
        for (int i = 0; i < 100; i++) archive.add(1, i);
        assertEquals(20, archive.size());
        assertEquals(Integer.valueOf(80), archive.snapshot(1).get(0));
        assertEquals(Integer.valueOf(99), archive.latest(1));
        archive.remove(1);
        assertEquals(0, archive.size());
    }

    @Test public void totalLimitCountsNotificationsAndEvictionKeepsHeldSnapshot() {
        NotificationArchive<Integer, Integer> archive = new NotificationArchive<>(120, 20);
        for (int key = 0; key < 6; key++)
            for (int i = 0; i < 20; i++) archive.add(key, i);
        List<Integer> held = archive.snapshot(0);
        // Move every other conversation after 0 in access order.
        for (int key = 1; key < 6; key++) archive.latest(key);
        archive.add(6, 0);
        assertNull(archive.latest(0));
        assertEquals(101, archive.size());
        assertEquals(20, held.size());
        for (int key = 0; key <= 6; key++) archive.remove(key);
        assertEquals(0, archive.size());
    }

    @Test public void growingExistingConversationEvictsOldestHistory() {
        NotificationArchive<String, Integer> archive = new NotificationArchive<>(4, 3);
        archive.add("a", 1);
        archive.add("a", 2);
        archive.add("b", 1);
        archive.add("b", 2);
        archive.add("a", 3);
        assertNull(archive.latest("b"));
        assertEquals(Arrays.asList(1, 2, 3), archive.snapshot("a"));
        assertEquals(3, archive.size());
    }

    @Test public void readingConversationMakesItRecentlyUsed() {
        NotificationArchive<String, Integer> archive = new NotificationArchive<>(3, 2);
        archive.add("a", 1);
        archive.add("b", 2);
        archive.add("c", 3);
        archive.snapshot("a");
        archive.add("d", 4);
        assertNull(archive.latest("b"));
        assertEquals(Integer.valueOf(1), archive.latest("a"));
        assertEquals(3, archive.size());
    }

    @Test public void snapshotsSurviveAppendReplaceAndRemoval() {
        NotificationArchive<Integer, String> archive = new NotificationArchive<>(120, 20);
        archive.add(1, "original");
        List<String> first = archive.snapshot(1);
        archive.add(1, "original");
        List<String> second = archive.snapshot(1);
        archive.replace(1, "original", "serialized");
        assertEquals(Arrays.asList("original", "serialized"), archive.snapshot(1));
        assertEquals(2, archive.size());
        archive.remove(1);
        archive.replace(1, "original", "late-rebuild");
        assertNull(archive.latest(1));
        assertEquals(Arrays.asList("original"), first);
        assertEquals(Arrays.asList("original", "original"), second);
        try { first.add("unexpected"); fail("Snapshot is mutable"); }
        catch (UnsupportedOperationException expected) { /* Structural snapshot is read-only. */ }
    }

    @Test public void missingReplacementDoesNotChangeHistoryOrCount() {
        NotificationArchive<Integer, String> archive = new NotificationArchive<>(120, 20);
        archive.add(1, "current");
        archive.replace(1, "missing", "rebuilt");
        assertEquals(Arrays.asList("current"), archive.snapshot(1));
        assertEquals(1, archive.size());
    }

    @Test public void oldRemovalTokenCannotDeleteNewNotification() {
        NotificationArchive<Integer, Long> archive = new NotificationArchive<>(120, 20);
        assertFalse(archive.removeIfLatest(1, token -> true));
        archive.add(1, 101L);
        archive.add(1, 102L);
        assertFalse(archive.removeIfLatest(1, token -> token == 101L));
        assertEquals(2, archive.size());
        assertTrue(archive.removeIfLatest(1, token -> token == 102L));
        assertEquals(0, archive.size());
        archive.add(1, 103L);
        assertFalse(archive.removeIfLatest(1, token -> token == 102L));
        assertEquals(Long.valueOf(103L), archive.latest(1));
    }

    @Test public void concurrentWritesReplacementAndRemovalRemainBounded() throws Exception {
        NotificationArchive<Integer, Integer> archive = new NotificationArchive<>(120, 20);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                final int offset = worker;
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 1000; i++) {
                        int key = (i + offset) % 9;
                        archive.add(key, i);
                        archive.replace(key, i, -i);
                        if (i % 7 == 0) archive.removeIfLatest(key, value -> value < 0);
                        if (i % 19 == 0) archive.remove(key);
                        assertTrue(archive.snapshot(key).size() <= 20);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) task.get(10, TimeUnit.SECONDS);
            int total = 0;
            for (int key = 0; key < 9; key++) total += archive.snapshot(key).size();
            assertEquals(total, archive.size());
            assertTrue(total <= 120);
            for (int key = 0; key < 9; key++) archive.remove(key);
            assertEquals(0, archive.size());
        } finally { executor.shutdownNow(); }
    }
}
