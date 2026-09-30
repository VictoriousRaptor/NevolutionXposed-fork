package com.oasisfeng.nevo.decorators.wechat;

import java.util.LinkedHashMap;
import java.util.Map;

/** Process-local, bounded recall IDs scoped to the verified talker rather than notification ID. */
final class RecallIndex {
    private final Map<String, RecallHistory> conversations = new LinkedHashMap<>();
    synchronized void add(String talker, long id) {
        if (talker == null || talker.isEmpty() || id <= 0) return;
        RecallHistory history = conversations.remove(talker);
        if (history == null) history = new RecallHistory();
        history.add(id); conversations.put(talker, history);
        while (conversations.size() > 64) conversations.remove(conversations.keySet().iterator().next());
    }
    synchronized long[] ids(String talker) {
        RecallHistory history = conversations.get(talker);
        return history == null ? new long[0] : history.ids();
    }
}
