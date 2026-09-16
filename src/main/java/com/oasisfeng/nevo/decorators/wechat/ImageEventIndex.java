package com.oasisfeng.nevo.decorators.wechat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/** Small metadata-only index; all time-to-live checks use a monotonic clock. */
final class ImageEventIndex {
	static final int CAPACITY = 64;
	static final long TTL_MS = 30000;
	/** WeChat's own notification timestamp lags the message by seconds, so observation time is the primary anchor. */
	static final long OBSERVED_WINDOW_MS = 5000;
	private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

	synchronized void put(String talker, long messageId, long created, List<String> paths, long now) {
		put(talker, messageId, 0, created, paths, now);
	}

	synchronized void put(String talker, long messageId, long msgSvrId, long created, List<String> paths, long now) {
		List<Path> ranked = new ArrayList<>();
		for (String path : paths) ranked.add(new Path(path, ImagePreviewPolicy.QUALITY_UNKNOWN));
		putRanked(talker, messageId, msgSvrId, created, ranked, now);
	}

	synchronized void putRanked(String talker, long messageId, long created, List<Path> paths, long now) {
		putRanked(talker, messageId, 0, created, paths, now);
	}

	synchronized void putRanked(String talker, long messageId, long msgSvrId, long created, List<Path> paths, long now) {
		prune(now);
		if (talker == null || talker.isEmpty() || messageId <= 0 || created <= 0) return;
		String key = talker + ':' + messageId;
		Entry previous = entries.remove(key);
		LinkedHashMap<String, Integer> merged = new LinkedHashMap<>();
		for (Path path : paths) merge(merged, path);
		if (previous != null) for (Path path : previous.paths) merge(merged, path);
		List<Path> bounded = new ArrayList<>();
		for (java.util.Map.Entry<String, Integer> path : merged.entrySet()) {
			bounded.add(new Path(path.getKey(), path.getValue()));
		}
		bounded.sort((a, b) -> Integer.compare(b.quality, a.quality));
		if (bounded.size() > 4) bounded = new ArrayList<>(bounded.subList(0, 4));
		if (bounded.isEmpty()) return;
		long stableMsgSvrId = msgSvrId > 0 ? msgSvrId : previous == null ? 0 : previous.msgSvrId;
		entries.put(key, new Entry(key, talker, stableMsgSvrId, created, bounded, now));
		while (entries.size() > CAPACITY) entries.remove(entries.keySet().iterator().next());
	}

	/** Stores message identity before the image pipeline publishes any path. */
	synchronized void putIdentity(String talker, long messageId, long msgSvrId, long created, long now) {
		prune(now);
		if (talker == null || talker.isEmpty() || messageId <= 0 || msgSvrId <= 0) return;
		String key = talker + ':' + messageId;
		Entry previous = entries.remove(key);
		List<Path> paths = previous == null ? Collections.emptyList() : previous.paths;
		long stableCreated = previous != null && previous.created > 0 ? previous.created : created;
		entries.put(key, new Entry(key, talker, msgSvrId, stableCreated, paths, now));
		while (entries.size() > CAPACITY) entries.remove(entries.keySet().iterator().next());
	}

	private static void merge(LinkedHashMap<String, Integer> merged, Path path) {
		if (path == null || path.value == null || path.value.isEmpty() || path.value.length() > 4096) return;
		Integer quality = merged.get(path.value);
		if (quality == null || quality < path.quality) merged.put(path.value, path.quality);
	}

	synchronized Entry select(String talker, long notificationTime, long observedAt, long now) {
		prune(now);
		if (talker == null || talker.isEmpty()) return null;
		Entry result = null;
		for (Entry entry : entries.values()) {
			if (!entry.talker.equals(talker) || !matches(entry, notificationTime, observedAt)) continue;
			if (result != null) return null; // Never guess between messages in the same conversation.
			result = entry;
		}
		return result;
	}

	private static boolean matches(Entry entry, long notificationTime, long observedAt) {
		if (entry.created > 0 && notificationTime > 0 && ImageCandidateSelector.isRecent(entry.created, notificationTime)) return true;
		return Math.abs(entry.observed - observedAt) <= OBSERVED_WINDOW_MS;
	}

	synchronized int size(long now) { prune(now); return entries.size(); }
	synchronized Entry get(String key, long now) { prune(now); return entries.get(key); }
	private void prune(long now) {
		Iterator<Entry> iterator = entries.values().iterator();
		while (iterator.hasNext()) if (now - iterator.next().observed >= TTL_MS) iterator.remove();
	}

	static final class Entry {
		final String key, talker;
		final long msgSvrId, created, observed;
		final List<Path> paths;
		Entry(String key, String talker, long msgSvrId, long created, List<Path> paths, long observed) {
			this.key = key; this.talker = talker; this.msgSvrId = msgSvrId; this.created = created;
			this.paths = paths; this.observed = observed;
		}
	}

	static final class Path {
		final String value;
		final int quality;
		Path(String value, int quality) { this.value = value; this.quality = quality; }
	}
}
