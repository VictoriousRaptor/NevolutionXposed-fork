package com.oasisfeng.nevo.decorators.wechat;

/** Local publication state, independent of unread history and system delivery latency. */
final class ReplyPresentation {
    private String tag;
    private long token;
    private boolean observed, cancelled;

    void accept(String tag, long token) {
        if (token <= 0 || observed && token <= this.token) return;
        this.tag = tag;
        this.token = token;
        observed = true;
        cancelled = false;
    }

    void cancel(String tag) {
        if (observed && !java.util.Objects.equals(tag, this.tag)) return;
        this.tag = tag;
        cancelAll();
    }

    void cancelAll() { observed = cancelled = true; }

    /** Restore only before observing any publication or cancellation in this process. */
    void restore(String tag, long token) { if (!observed) accept(tag, token); }

    boolean allows(String tag, long token) {
        return observed && !cancelled && token > 0 && token == this.token && java.util.Objects.equals(tag, this.tag);
    }
}
