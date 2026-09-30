package com.oasisfeng.nevo.decorators.wechat;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Exact server IDs only: never infer a recall target from text, order or timestamps. */
final class RecallHistory {
    private static final int LIMIT = 128;
    private static final Pattern PROMPT = Pattern.compile(
            "^(?:\\[\\d+(?:条|則)?\\])?(?:你|对方|(?:\"[^\"]+\"|“[^”]+”)\\s*)?撤回了?一条消息$" );
    private final Set<Long> recalled = new LinkedHashSet<>();

    static boolean isPrompt(CharSequence text) {
        return text != null && PROMPT.matcher(text).matches();
    }
    void add(long id) {
        if (id <= 0) return;
        recalled.add(id);
        while (recalled.size() > LIMIT) recalled.remove(recalled.iterator().next());
    }
    void addAll(long[] ids) { if (ids != null) for (long id : ids) add(id); }
    boolean contains(long id) { return id > 0 && recalled.contains(id); }
    long[] ids() {
        long[] ids = new long[recalled.size()];
        int i = 0;
        for (long id : recalled) ids[i++] = id;
        return ids;
    }
}
