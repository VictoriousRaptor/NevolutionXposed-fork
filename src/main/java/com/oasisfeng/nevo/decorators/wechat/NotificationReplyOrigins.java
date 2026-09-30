package com.oasisfeng.nevo.decorators.wechat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Bounded, short-lived markers distinguishing notification replies from app sends. */
final class NotificationReplyOrigins {
    private static final long MAX_AGE_MS = 120_000;
    private static final int MAX_PENDING = 32;
    private static final int MAX_SEEN = 64;
    enum Origin { APP, NOTIFICATION, DUPLICATE }
    private final List<Pending> pending = new ArrayList<>();
    private final List<Seen> seen = new ArrayList<>();
    private long nextToken;

    long reserve(String talker, long now) {
        if (talker == null || talker.isEmpty()) return 0;
        expire(now);
        if (pending.size() == MAX_PENDING) pending.remove(0);
        long token = ++nextToken;
        if (token == 0) token = ++nextToken;
        pending.add(new Pending(token, talker, now));
        return token;
    }

    Origin classify(String talker, long messageId, long now) {
        expire(now);
        for (Seen entry : seen)
            if (entry.messageId == messageId && entry.talker.equals(talker)) return Origin.DUPLICATE;
        if (seen.size() == MAX_SEEN) seen.remove(0);
        seen.add(new Seen(talker, messageId, now));
        for (Iterator<Pending> entries = pending.iterator(); entries.hasNext();) {
            if (entries.next().talker.equals(talker)) {
                entries.remove();
                return Origin.NOTIFICATION;
            }
        }
        return Origin.APP;
    }

    void abort(long token) {
        if (token == 0) return;
        pending.removeIf(entry -> entry.token == token);
    }

    void clear() { pending.clear(); seen.clear(); }

    private void expire(long now) {
        pending.removeIf(entry -> now < entry.created || now - entry.created > MAX_AGE_MS);
        seen.removeIf(entry -> now < entry.created || now - entry.created > 10 * MAX_AGE_MS);
    }

    private static final class Pending {
        final long token;
        final String talker;
        final long created;
        Pending(long token, String talker, long created) {
            this.token = token;
            this.talker = talker;
            this.created = created;
        }
    }

    private static final class Seen {
        final String talker;
        final long messageId, created;
        Seen(String talker, long messageId, long created) {
            this.talker = talker;
            this.messageId = messageId;
            this.created = created;
        }
    }
}
