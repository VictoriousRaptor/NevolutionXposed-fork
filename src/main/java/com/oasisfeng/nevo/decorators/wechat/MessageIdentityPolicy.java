package com.oasisfeng.nevo.decorators.wechat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Android-free identity and merge rules. Missing identity is never implicitly self. */
final class MessageIdentityPolicy {
    enum Identity { SELF, PEER, UNKNOWN }

    static Identity resolve(boolean personPresent, String sender, boolean moduleReply,
                            boolean messagingContext) {
        if (personPresent) return Identity.PEER;
        if (moduleReply) return Identity.SELF;
        if (sender != null && !sender.isEmpty()) return Identity.PEER;
        if (messagingContext && sender == null) return Identity.SELF;
        return Identity.UNKNOWN;
    }

    static boolean sameConversation(String title, String key, String otherTitle, String otherKey) {
        if (key != null && otherKey != null) return key.equals(otherKey);
        return title != null && title.equals(otherTitle);
    }

    static final class Entry<T> {
        final T value;
        final long time;
        final Object signature;
        final String replyId;
        final int source;
        /** Trusted message identity, already scoped to its conversation/sender; null when absent. */
        final Object stableId;
        Entry(T value, long time, Object signature, String replyId, int source) {
            this(value, time, signature, replyId, source, null);
        }
        Entry(T value, long time, Object signature, String replyId, int source, Object stableId) {
            this.value = value;
            this.time = time;
            this.signature = signature;
            this.replyId = replyId;
            this.source = source;
            this.stableId = stableId;
        }
    }

    /** Trusted IDs collapse once; unidentified copies merge by occurrence across sources only. */
    static <T> List<T> merge(List<Entry<T>> input, int limit) {
        return merge(input, limit, Comparator.comparingLong(e -> Math.max(0, e.time)));
    }

    /** Keep the first representation of each trusted stable ID. */
    static <T> List<Entry<T>> distinctStableIds(List<Entry<T>> input) {
        List<Entry<T>> result = new ArrayList<>();
        Set<Object> messages = new HashSet<>();
        for (Entry<T> entry : input)
            if (entry.stableId == null || messages.add(entry.stableId)) result.add(entry);
        return result;
    }

    static <T> List<T> merge(List<Entry<T>> input, int limit, Comparator<Entry<T>> order) {
        input = distinctStableIds(input);
        List<Entry<T>> result = new ArrayList<>();
        Set<String> replies = new HashSet<>();
        for (int i = 0; i < input.size(); i++) {
            Entry<T> entry = input.get(i);
            if (entry.replyId != null && !replies.add(entry.replyId)) continue;
            int occurrence = 0;
            if (entry.stableId == null && entry.time > 0 && entry.replyId == null) {
                for (int j = 0; j <= i; j++) {
                    Entry<T> preceding = input.get(j);
                    if (preceding.source == entry.source && same(entry, preceding)) occurrence++;
                }
                int copies = 0;
                for (Entry<T> existing : result)
                    if (existing.source != entry.source && same(entry, existing)) copies++;
                if (copies >= occurrence) continue;
            }
            result.add(entry);
        }
        result.sort(order);
        List<T> values = new ArrayList<>();
        for (int i = Math.max(0, result.size() - limit); i < result.size(); i++) values.add(result.get(i).value);
        return values;
    }

    private static boolean same(Entry<?> a, Entry<?> b) {
        return a.stableId == null && b.stableId == null
                && a.time == b.time && Objects.equals(a.signature, b.signature);
    }

    private MessageIdentityPolicy() {}
}
