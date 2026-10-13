package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;

import com.oasisfeng.nevo.xposed.BuildConfig;

/** Presentation adapter. The archive and reply/recall policies always read the retained source. */
final class NotificationMarkdown {
    static final String PREF = "WeChatDecorator.markdown";
    static final String RAW_TEXT = "nevo.markdown.rawText";
    static final String OWNER = "nevo.markdown.owner";
    private static final String TAG = "WeChat.Markdown";
    // Translucent neutral gray works over light and dark backgrounds without fixing text color.
    static final int CODE_BACKGROUND = 0x30808080;

    static CharSequence originalText(Bundle extras) {
        CharSequence raw = extras.getCharSequence(RAW_TEXT);
        return raw != null ? raw : extras.getCharSequence(Notification.EXTRA_TEXT);
    }

    static CharSequence originalMessageText(Bundle message) {
        Bundle metadata = message.getBundle("extras");
        CharSequence raw = metadata == null ? null : metadata.getCharSequence(RAW_TEXT);
        return raw != null ? raw : message.getCharSequence("text");
    }

    static void restoreText(Bundle extras) {
        if (!extras.containsKey(RAW_TEXT)) return;
        extras.putCharSequence(Notification.EXTRA_TEXT, originalText(extras));
        extras.remove(RAW_TEXT);
    }

    static void apply(Notification notification, boolean enabled) {
        Bundle extras = notification.extras;
        restoreText(extras);
        CharSequence raw = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence rendered = enabled ? render(raw) : raw;
        if (rendered != raw) {
            extras.putCharSequence(RAW_TEXT, raw);
            extras.putCharSequence(Notification.EXTRA_TEXT, rendered);
        }
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages == null) return;
        Parcelable[] output = null;
        for (int i = 0; i < messages.length; i++) {
            if (!(messages[i] instanceof Bundle)) continue;
            Bundle original = (Bundle) messages[i];
            raw = originalMessageText(original);
            rendered = enabled ? render(raw) : raw;
            Bundle metadata = original.getBundle("extras");
            if (rendered == raw && (metadata == null || !metadata.containsKey(RAW_TEXT))) continue;
            Bundle message = new Bundle(original);
            message.putCharSequence("text", rendered);
            metadata = metadata == null ? new Bundle() : new Bundle(metadata);
            metadata.remove(RAW_TEXT);
            metadata.remove(OWNER);
            if (rendered != raw) {
                metadata.putCharSequence(RAW_TEXT, raw);
                metadata.putString(OWNER, WeChatDecorator.WECHAT_PACKAGE);
            }
            if (!metadata.isEmpty()) message.putBundle("extras", metadata);
            else message.remove("extras");
            if (output == null) output = messages.clone();
            output[i] = message;
        }
        if (output != null) extras.putParcelableArray(Notification.EXTRA_MESSAGES, output);
    }

    /** Restore only our code ranges after framework sanitization, keeping its other decisions. */
    static CharSequence restoreCodeStyles(CharSequence cleaned, Bundle metadata) {
        if (cleaned == null || metadata == null
                || !WeChatDecorator.WECHAT_PACKAGE.equals(metadata.getString(OWNER))) return cleaned;
        CharSequence raw = metadata.getCharSequence(RAW_TEXT);
        if (raw == null) return cleaned;
        InlineMarkdown.Result parsed = InlineMarkdown.parse(raw.toString());
        if (parsed.limited || !TextUtils.equals(cleaned, parsed.text)) return cleaned;
        SpannableStringBuilder output = null;
        for (InlineMarkdown.Range range : parsed.spans) {
            if ((range.style & InlineMarkdown.CODE) == 0) continue;
            boolean font = false, background = false;
            if (cleaned instanceof Spanned) {
                Spanned spans = (Spanned) cleaned;
                for (TypefaceSpan span : spans.getSpans(range.start, range.end, TypefaceSpan.class))
                    if ("monospace".equals(span.getFamily()) && spans.getSpanStart(span) == range.start
                            && spans.getSpanEnd(span) == range.end) font = true;
                for (BackgroundColorSpan span : spans.getSpans(range.start, range.end, BackgroundColorSpan.class))
                    if (span.getBackgroundColor() == CODE_BACKGROUND && spans.getSpanStart(span) == range.start
                            && spans.getSpanEnd(span) == range.end) background = true;
            }
            if (font && background) continue;
            if (output == null) output = new SpannableStringBuilder(cleaned);
            if (!font) output.setSpan(new TypefaceSpan("monospace"), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!background) output.setSpan(new BackgroundColorSpan(CODE_BACKGROUND), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return output == null ? cleaned : output;
    }

    static CharSequence render(CharSequence source) {
        if (source == null) return null;
        try {
            InlineMarkdown.Result parsed = InlineMarkdown.parse(source.toString());
            if (parsed.limited && BuildConfig.DEBUG) Log.d(TAG, "source=markdown_skipped reason=limit");
            if (parsed.spans.isEmpty()) return source;
            // Delete only the delimiters so existing spans and emoji keep their positions.
            SpannableStringBuilder text = new SpannableStringBuilder(source);
            for (int i = parsed.removed.size() - 1; i >= 0; i--) {
                InlineMarkdown.Range range = parsed.removed.get(i);
                text.delete(range.start, range.end);
            }
            for (InlineMarkdown.Range range : parsed.spans) {
                if ((range.style & InlineMarkdown.CODE) != 0) {
                    text.setSpan(new TypefaceSpan("monospace"), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new BackgroundColorSpan(CODE_BACKGROUND), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                int style = ((range.style & InlineMarkdown.BOLD) != 0 ? Typeface.BOLD : 0)
                        | ((range.style & InlineMarkdown.ITALIC) != 0 ? Typeface.ITALIC : 0);
                if (style != 0) text.setSpan(new StyleSpan(style), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if ((range.style & InlineMarkdown.STRIKE) != 0)
                    text.setSpan(new StrikethroughSpan(), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return text;
        } catch (RuntimeException failure) {
            Log.w(TAG, "source=markdown_fallback type=" + failure.getClass().getSimpleName());
            return source;
        }
    }
}
