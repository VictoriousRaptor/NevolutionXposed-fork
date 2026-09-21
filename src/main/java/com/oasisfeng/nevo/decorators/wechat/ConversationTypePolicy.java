package com.oasisfeng.nevo.decorators.wechat;

/** Body/ticker text is deliberately not an input: it cannot prove a conversation's type. */
final class ConversationTypePolicy {
    static final int UNKNOWN = 0, DIRECT = 1, GROUP = 2, BOT = 3;
    enum Source { UNKNOWN, TALKER, NATIVE_STYLE }
    static final class Decision {
        final int type;
        final Source source;
        Decision(int type, Source source) { this.type = type; this.source = source; }
    }
    static Decision resolve(String talker, Boolean nativeGroup, Decision cached) {
        if (talker != null && !talker.trim().isEmpty()) {
            int type = talker.endsWith("@chatroom") || talker.endsWith("@im.chatroom") ? GROUP
                    : talker.startsWith("gh_") ? BOT : DIRECT;
            return new Decision(type, Source.TALKER);
        }
        if (nativeGroup != null) return new Decision(nativeGroup ? GROUP : DIRECT, Source.NATIVE_STYLE);
        if (cached != null && cached.source != Source.UNKNOWN) return cached;
        return new Decision(UNKNOWN, Source.UNKNOWN);
    }
    static boolean sameKnownTalker(String a, String b) {
        return a != null && !a.isEmpty() && a.equals(b);
    }
}
