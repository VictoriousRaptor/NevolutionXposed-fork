package com.oasisfeng.nevo.sdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Bounded notification history with atomic updates and stable, shallow list snapshots. */
final class NotificationArchive<K, V> {
    private final int maxItems, maxPerKey;
    private final LinkedHashMap<K, List<V>> histories = new LinkedHashMap<>(16, 0.75f, true);
    private int size;

    NotificationArchive(int maxItems, int maxPerKey) {
        if (maxPerKey <= 0 || maxItems < maxPerKey) throw new IllegalArgumentException("Invalid archive limits");
        this.maxItems = maxItems;
        this.maxPerKey = maxPerKey;
    }

    synchronized void add(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        List<V> previous = histories.get(key);
        List<V> updated = previous == null ? new ArrayList<>() : new ArrayList<>(previous);
        updated.add(value);
        if (updated.size() > maxPerKey) updated.remove(0);
        histories.put(key, Collections.unmodifiableList(updated));
        size += updated.size() - (previous == null ? 0 : previous.size());
        Iterator<List<V>> oldest = histories.values().iterator();
        while (size > maxItems && oldest.hasNext()) {
            size -= oldest.next().size();
            oldest.remove();
        }
    }

    synchronized List<V> snapshot(K key) {
        List<V> history = histories.get(key);
        return history == null ? Collections.emptyList() : history;
    }

    synchronized V latest(K key) {
        List<V> history = histories.get(key);
        return history == null ? null : history.get(history.size() - 1);
    }

    synchronized void remove(K key) {
        List<V> removed = histories.remove(key);
        if (removed != null) size -= removed.size();
    }

    /** The predicate must only inspect the latest value, without modifying this archive. */
    synchronized boolean removeIfLatest(K key, Predicate<V> matches) {
        V latest = latest(key);
        if (latest == null || !matches.test(latest)) return false;
        remove(key);
        return true;
    }

    synchronized void replace(K key, V original, V replacement) {
        Objects.requireNonNull(replacement);
        List<V> history = histories.get(key);
        if (history == null) return;
        int index = history.lastIndexOf(original);
        if (index < 0) return;
        List<V> updated = new ArrayList<>(history);
        updated.set(index, replacement);
        histories.put(key, Collections.unmodifiableList(updated));
    }

    synchronized int size() { return size; }
}
