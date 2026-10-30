package com.oasisfeng.nevo.decorators.wechat;

import java.util.List;

/** A preview replaces only its own incoming placeholder in the rendered conversation. */
final class ImagePlaceholderPolicy {
    static final class Message {
        final CharSequence text;
        final long serverId;
        final boolean incoming;
        Message(CharSequence text, long serverId, boolean incoming) {
            this.text = text; this.serverId = serverId; this.incoming = incoming;
        }
    }

    static int target(List<Message> messages, long serverId) {
        int target = -1;
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            boolean placeholder = message.text != null
                    && ("[图片]".contentEquals(message.text) || "图片".contentEquals(message.text));
            if (serverId > 0 ? message.serverId != serverId : !message.incoming || !placeholder) continue;
            if (target >= 0 || !message.incoming || !placeholder) return -1;
            target = i;
        }
        return target;
    }

    private ImagePlaceholderPolicy() {}
}
