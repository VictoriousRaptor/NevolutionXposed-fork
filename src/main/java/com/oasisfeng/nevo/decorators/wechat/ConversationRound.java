package com.oasisfeng.nevo.decorators.wechat;

import java.util.LinkedHashMap;
import java.util.Map;

/** Notification generations, not message clocks, delimit a conversation's visible round. */
final class ConversationRound {
    long endThroughToken;
    private final Map<Object, Long> firstTokens = new LinkedHashMap<>();

    boolean end(long token) {
        if (token <= endThroughToken) return false;
        endThroughToken = token;
        return true;
    }

    boolean retains(long token) { return endThroughToken == 0 || token > endThroughToken; }

    /** A trusted-ID repost cannot become a new peer after its original round ended. */
    long firstToken(Object stableId, long token) {
        if (stableId == null || token <= 0) return token;
        Long first = firstTokens.get(stableId);
        if (first != null) return first;
        firstTokens.put(stableId, token);
        if (firstTokens.size() > 64) firstTokens.remove(firstTokens.keySet().iterator().next());
        return token;
    }
}
