package com.oasisfeng.nevo.decorators.wechat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Bounded Markdown subset: emphasis, strike, backtick code spans and fenced code. */
final class InlineMarkdown {
    static final int BOLD = 1, ITALIC = 2, STRIKE = 4, CODE = 8;
    static final int MAX_LENGTH = 4096, MAX_SPANS = 128, MAX_DEPTH = 12, MAX_WORK = 65536;

    static final class Range {
        final int start, end, style;
        Range(int start, int end, int style) { this.start = start; this.end = end; this.style = style; }
    }

    static final class Result {
        final String text;
        final List<Range> spans, removed;
        final boolean limited;
        Result(String text, List<Range> spans, List<Range> removed, boolean limited) {
            this.text = text; this.spans = spans; this.removed = removed; this.limited = limited;
        }
    }

    static Result parse(String source) {
        if (source.length() > MAX_LENGTH) return unchanged(source, true);
        InlineMarkdown parser = new InlineMarkdown(source);
        try {
            parser.append(0, source.length(), 0);
            parser.removed.sort(Comparator.comparingInt(range -> range.start));
            return new Result(parser.output.toString(), parser.spans, parser.removed, false);
        } catch (LimitReached limit) {
            return unchanged(source, true);
        }
    }

    private static Result unchanged(String source, boolean limited) {
        return new Result(source, Collections.emptyList(), Collections.emptyList(), limited);
    }

    private final String source;
    private final StringBuilder output = new StringBuilder();
    private final List<Range> spans = new ArrayList<>(), removed = new ArrayList<>();
    private int work;
    private InlineMarkdown(String source) { this.source = source; }

    private void step() { if (++work > MAX_WORK) throw new LimitReached(); }

    private void append(int start, int end, int depth) {
        if (depth > MAX_DEPTH) throw new LimitReached();
        for (int i = start; i < end;) {
            step();
            char c = source.charAt(i);
            if (c == '\\') {
                int next = escapedEnd(i, end);
                output.append(source, i, next);
                i = next;
                continue;
            }
            if (c == '`') {
                Code code = code(i, end);
                if (code.closed && code.bodyEnd > code.bodyStart) {
                    // Opening-fence indentation has already been appended as ordinary text.
                    output.setLength(output.length() - (i - code.start));
                    int spanStart = output.length();
                    removed.add(new Range(code.start, code.bodyStart, 0));
                    output.append(source, code.bodyStart, code.bodyEnd);
                    removed.add(new Range(code.bodyEnd, code.end, 0));
                    addSpan(spanStart, output.length(), CODE);
                } else output.append(source, i, code.end);
                i = code.end;
                continue;
            }
            int length = run(i, end);
            int style = c == '*' && length <= 3 ? (length == 1 ? ITALIC : length == 2 ? BOLD : BOLD | ITALIC)
                    : c == '~' && length == 2 ? STRIKE : 0;
            int close = style != 0 && i + length < end && !Character.isWhitespace(source.charAt(i + length))
                    ? closing(i + length, end, c, length) : -1;
            if (close >= 0) {
                int spanStart = output.length();
                removed.add(new Range(i, i + length, 0));
                append(i + length, close, depth + 1);
                removed.add(new Range(close, close + length, 0));
                addSpan(spanStart, output.length(), style);
                i = close + length;
            } else {
                output.append(source, i, i + length);
                i += length;
            }
        }
    }

    private void addSpan(int start, int end, int style) {
        if (spans.size() >= MAX_SPANS) throw new LimitReached();
        spans.add(new Range(start, end, style));
    }

    private int closing(int start, int end, char marker, int length) {
        for (int i = start; i < end;) {
            step();
            char c = source.charAt(i);
            // Inline delimiters never span a line break.
            if (c == '\n' || c == '\r') return -1;
            if (c == '\\' || c == '`') {
                int next = c == '\\' ? escapedEnd(i, end) : code(i, end).end;
                for (int j = i; j < next; j++) {
                    step();
                    if (source.charAt(j) == '\n' || source.charAt(j) == '\r') return -1;
                }
                i = next;
                continue;
            }
            int count = run(i, end);
            if (c == marker && count == length && i > start && !Character.isWhitespace(source.charAt(i - 1))) return i;
            i += count;
        }
        return -1;
    }

    private int run(int start, int end) {
        char c = source.charAt(start);
        if (c != '*' && c != '~' && c != '`') return 1;
        int i = start + 1;
        while (i < end && source.charAt(i) == c) { step(); i++; }
        return i - start;
    }

    private int escapedEnd(int start, int end) {
        // Keep an escaped backtick run literal instead of reinterpreting its remaining ticks.
        return start + 1 < end && source.charAt(start + 1) == '`'
                ? start + 1 + run(start + 1, end) : Math.min(start + 2, end);
    }

    private Code code(int start, int end) {
        int count = run(start, end);
        int lineStart = start;
        while (lineStart > 0 && start - lineStart < 4 && source.charAt(lineStart - 1) == ' ') {
            step(); lineStart--;
        }
        boolean fence = count >= 3 && start - lineStart <= 3
                && (lineStart == 0 || source.charAt(lineStart - 1) == '\n' || source.charAt(lineStart - 1) == '\r');
        if (fence) {
            int headerEnd = lineEnd(start + count, end);
            for (int i = start + count; i < headerEnd; i++) {
                step();
                // Backticks in an info string disqualify a fence; it can still be inline code.
                if (source.charAt(i) == '`') { fence = false; break; }
            }
            if (fence) return fencedCode(lineStart, afterLine(headerEnd, end), end, count);
        }
        for (int i = start + count; i < end;) {
            step();
            if (source.charAt(i) == '`') {
                int nextCount = run(i, end);
                if (nextCount == count) return new Code(start, start + count, i, i + count, true);
                i += nextCount;
            } else i++;
        }
        // An unclosed code marker protects the remainder rather than changing its contents.
        return new Code(start, start, end, end, false);
    }

    private Code fencedCode(int start, int bodyStart, int end, int count) {
        for (int line = bodyStart; line < end;) {
            step();
            int finish = lineEnd(line, end);
            int ticks = line;
            while (ticks < finish && ticks - line < 3 && source.charAt(ticks) == ' ') { step(); ticks++; }
            if (ticks < finish && source.charAt(ticks) == '`') {
                int closingCount = run(ticks, finish);
                if (closingCount >= count) {
                    int tail = ticks + closingCount;
                    while (tail < finish && (source.charAt(tail) == ' ' || source.charAt(tail) == '\t')) { step(); tail++; }
                    if (tail == finish) return new Code(start, bodyStart, line, afterLine(finish, end), true);
                }
            }
            line = afterLine(finish, end);
        }
        return new Code(start, start, end, end, false);
    }

    private int lineEnd(int start, int end) {
        int i = start;
        while (i < end && source.charAt(i) != '\n' && source.charAt(i) != '\r') { step(); i++; }
        return i;
    }

    private int afterLine(int finish, int end) {
        if (finish == end) return end;
        return source.charAt(finish) == '\r' && finish + 1 < end && source.charAt(finish + 1) == '\n'
                ? finish + 2 : finish + 1;
    }

    private static final class Code {
        final int start, bodyStart, bodyEnd, end;
        final boolean closed;
        Code(int start, int bodyStart, int bodyEnd, int end, boolean closed) {
            this.start = start; this.bodyStart = bodyStart; this.bodyEnd = bodyEnd; this.end = end; this.closed = closed;
        }
    }

    private static final class LimitReached extends RuntimeException {}
}
