package com.oasisfeng.nevo.decorators.wechat;

/** Text format only. Conversation type must come from ConversationTypePolicy evidence. */
final class WeChatMessage {
    static final String SENDER_MESSAGE_SEPARATOR = ": ";

    /** Use the original ticker, independently of native reply objects or conversation type. */
    static boolean isChat(CharSequence ticker) {
        if (ticker == null) return false;
        for (int i = 1; i < ticker.length(); i++) if (ticker.charAt(i) == ':') return true;
        return false;
    }
    static boolean canUseSyntheticReply(CharSequence ticker, boolean hasTarget, boolean receiverVerified) {
        return isChat(ticker) && hasTarget && receiverVerified;
    }
    private WeChatMessage() {}
}
