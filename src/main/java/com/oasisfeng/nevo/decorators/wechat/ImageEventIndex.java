package com.oasisfeng.nevo.decorators.wechat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/** Small metadata-only index; all time-to-live checks use a monotonic clock. */
final class ImageEventIndex {
	static final int CAPACITY = 64;
	static final long TTL_MS = 30000;
	private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

	synchronized void put(String talker, long messageId, long created, List<String> paths, long now) {
		prune(now);
		if (talker == null || talker.isEmpty() || messageId <= 0 || created <= 0) return;
		String key = talker + ':' + messageId;
		Entry previous = entries.remove(key);
		LinkedHashSet<String> merged = new LinkedHashSet<>();
		for (String path : paths) if (path != null && !path.isEmpty() && path.length() <= 4096) merged.add(path);
		if (previous != null) merged.addAll(previous.paths);
		List<String> bounded = new ArrayList<>();
		for (String path : merged) { if (bounded.size() == 4) break; bounded.add(path); }
		if (bounded.isEmpty()) return;
		entries.put(key, new Entry(key, talker, created, bounded, now));
		while (entries.size() > CAPACITY) entries.remove(entries.keySet().iterator().next());
	}

	synchronized Entry select(String talker, long notificationTime, long now) {
		prune(now);
		if (talker == null || talker.isEmpty()) return null;
		Entry result = null;
		for (Entry entry : entries.values()) {
			if (!entry.talker.equals(talker) || !ImageCandidateSelector.isRecent(entry.created, notificationTime)) continue;
			if (result != null) return null; // Never guess between messages in the same conversation.
			result = entry;
		}
		return result;
	}

	synchronized int size(long now) { prune(now); return entries.size(); }
	private void prune(long now) {
		Iterator<Entry> iterator = entries.values().iterator();
		while (iterator.hasNext()) if (now - iterator.next().observed >= TTL_MS) iterator.remove();
	}

	static final class Entry {
		final String key, talker;
		final long created, observed;
		final List<String> paths;
		Entry(String key, String talker, long created, List<String> paths, long observed) {
			this.key = key; this.talker = talker; this.created = created;
			this.paths = paths; this.observed = observed;
		}
	}
}
