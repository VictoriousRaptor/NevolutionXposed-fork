package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcel;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.AbsoluteSizeSpan;

import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;
import androidx.core.app.Person;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;

import java.util.Collections;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Framework regression cases, registered in the existing runner. Compilation is not execution. */
@androidx.annotation.RequiresApi(28)
@android.annotation.SuppressLint("SoonBlockedPrivateApi") // Framework tests run with --no-hidden-api-checks.
final class NotificationMarkdownInstrumentation {
    static void headingsKeepSystemSizeAndInlineStyles() {
        String raw = "# Bold\n## Medium ~~123~~";
        Bundle bundle = new Bundle();
        bundle.putCharSequence("text", NotificationMarkdown.render(raw));
        Spanned text = (Spanned) parcel(bundle).getCharSequence("text");
        check("Bold\nMedium 123".contentEquals(text), "heading markers remained");
        StyleSpan[] bold = text.getSpans(0, 4, StyleSpan.class);
        check(bold.length == 1 && bold[0].getStyle() == Typeface.BOLD
                && text.getSpanStart(bold[0]) == 0 && text.getSpanEnd(bold[0]) == 4, "H1 weight");
        TypefaceSpan[] medium = text.getSpans(5, 15, TypefaceSpan.class);
        check(medium.length == 1 && "sans-serif-medium".equals(medium[0].getFamily())
                && text.getSpanStart(medium[0]) == 5 && text.getSpanEnd(medium[0]) == 15, "H2 weight");
        StrikethroughSpan[] strike = text.getSpans(12, 15, StrikethroughSpan.class);
        check(strike.length == 1 && text.getSpanStart(strike[0]) == 12 && text.getSpanEnd(strike[0]) == 15,
                "heading strike lost");
        check(text.getSpans(0, text.length(), RelativeSizeSpan.class).length == 0
                && text.getSpans(0, text.length(), AbsoluteSizeSpan.class).length == 0, "heading changed size");
    }

    static void headingRebuildAndDisable(Context context) {
        String raw = "# Bold\n## Medium ~~123~~";
        Conversation conversation = conversation();
        Notification notification = normalized(conversation, notification(context, raw, 100));
        for (int round = 0; round < 3; round++) {
            NotificationMarkdown.apply(notification, true);
            check("Bold\nMedium 123".contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT)),
                    "heading render changed on repeat");
            notification.extras = parcel(notification.extras);
            check(raw.contentEquals(NotificationMessages.rebuild(conversation, notification, Collections.emptyList(), true)
                    .get(0).getText()), "heading source lost");
            notification = normalized(conversation, notification);
        }
        NotificationMarkdown.apply(notification, false);
        Bundle message = (Bundle) notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)[0];
        check(raw.contentEquals(message.getCharSequence("text"))
                && raw.contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT)), "heading markers not restored");
        check(message.getBundle("extras") == null || !message.getBundle("extras").containsKey(NotificationMarkdown.OWNER),
                "stale heading owner");
    }

    static void frameworkHeadingCleanupRoundTrip() {
        try {
            String raw = "# Bold\n## Medium `code` ~~123~~";
            Notification.MessagingStyle.Message message = new Notification.MessagingStyle.Message(
                    NotificationMarkdown.render(raw), 100, (android.app.Person) null);
            message.getExtras().putAll(codeMetadata(raw));
            NotificationMarkdownHookProfile profile = NotificationMarkdownHookProfile.resolve(
                    message.getClass(), Notification.MessagingStyle.class, Bundle.class, android.widget.RemoteViews.class);
            Field textField = message.getClass().getDeclaredField("mText");
            textField.setAccessible(true);
            Method toBundle = message.getClass().getDeclaredMethod("toBundle");
            toBundle.setAccessible(true);
            Method fromBundle = message.getClass().getDeclaredMethod("getMessageFromBundle", Bundle.class);
            fromBundle.setAccessible(true);
            for (int round = 0; round < 3; round++) {
                if (profile.textField == null) profile.callers[0].invoke(message, 0xffeeeeee);
                else profile.cleanup.invoke(message);
                CharSequence cleaned = message.getText();
                CharSequence restored = NotificationMarkdown.restoreStylesAfterCleanup(cleaned, message.getExtras());
                check("Bold\nMedium code 123".contentEquals(restored), "heading cleanup changed characters");
                Spanned styled = (Spanned) restored;
                TypefaceSpan[] fonts = styled.getSpans(0, styled.length(), TypefaceSpan.class);
                int medium = 0, code = 0;
                for (TypefaceSpan font : fonts) {
                    if ("sans-serif-medium".equals(font.getFamily())) medium++;
                    else if ("monospace".equals(font.getFamily())) code++;
                }
                check(medium == 2 && code == 1, "heading/code font lost or duplicated");
                check(styled.getSpans(0, styled.length(), StyleSpan.class).length == 1, "H1 bold lost");
                check(styled.getSpans(0, styled.length(), StrikethroughSpan.class).length == 1, "strike lost");
                check(styled.getSpans(0, styled.length(), RelativeSizeSpan.class).length == 0, "heading size changed");
                check(NotificationMarkdown.restoreStylesAfterCleanup(restored, message.getExtras()) == restored,
                        "heading restoration not idempotent");
                textField.set(message, restored);
                message = (Notification.MessagingStyle.Message) fromBundle.invoke(null, parcel((Bundle) toBundle.invoke(message)));
            }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    static void stylesSurviveParcelAndExistingSpans() {
        SpannableStringBuilder source = new SpannableStringBuilder("😀 **粗体** *斜体* ~~旧~~");
        source.setSpan(new ForegroundColorSpan(0xff123456), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Bundle bundle = new Bundle();
        bundle.putCharSequence("text", NotificationMarkdown.render(source));
        Spanned text = (Spanned) parcel(bundle).getCharSequence("text");
        check("😀 粗体 斜体 旧".contentEquals(text), "rendered text");
        StyleSpan[] styles = text.getSpans(0, text.length(), StyleSpan.class);
        check(styles.length == 2 && styles[0].getStyle() == Typeface.BOLD && styles[1].getStyle() == Typeface.ITALIC, "styles lost");
        check(text.getSpanStart(styles[0]) == 3 && text.getSpanEnd(styles[0]) == 5, "bold offsets");
        check(text.getSpans(0, text.length(), StrikethroughSpan.class).length == 1, "strike lost");
        ForegroundColorSpan color = text.getSpans(0, text.length(), ForegroundColorSpan.class)[0];
        check(text.getSpanStart(color) == 0 && text.getSpanEnd(color) == 2, "existing span moved");
        check("😀 **粗体** *斜体* ~~旧~~".contentEquals(source), "source was mutated");
    }

    static void repeatedRebuildAndDisable(Context context) {
        Conversation conversation = conversation();
        Notification notification = normalized(conversation, notification(context, "**原文**", 100));
        Bundle originalBundle = (Bundle) notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)[0];
        NotificationMarkdown.apply(notification, true);
        check("原文".contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT)), "collapsed text");
        check("**原文**".contentEquals(originalBundle.getCharSequence("text")), "source bundle was mutated");
        NotificationMarkdown.apply(notification, true);
        notification.extras = parcel(notification.extras);
        for (int i = 0; i < 3; i++) {
            List<Message> messages = NotificationMessages.rebuild(conversation, notification, Collections.emptyList(), true);
            check(messages.size() == 1 && "**原文**".contentEquals(messages.get(0).getText()), "source or identity lost");
            notification = normalized(conversation, notification);
            NotificationMarkdown.apply(notification, true);
        }
        NotificationMarkdown.apply(notification, false);
        Bundle restored = (Bundle) notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)[0];
        check("**原文**".contentEquals(restored.getCharSequence("text")), "disabled message not restored");
        check("**原文**".contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT)), "disabled collapsed text not restored");
        check(!notification.extras.containsKey(NotificationMarkdown.RAW_TEXT), "stale source metadata");
    }

    static void identityReplyAndRecallUseSource(Context context) {
        Conversation conversation = conversation();
        Notification notification = notification(context, "**相同**", 100);
        MessagingStyle style = new MessagingStyle(new Person.Builder().setName("我").build());
        Person peer = NotificationMessages.peer(conversation, null);
        Message first = new Message("**相同**", 100, peer);
        first.getExtras().putLong(NotificationMessages.SERVER_ID, 11);
        style.addMessage(first).addMessage(new Message("相同", 100, peer));
        NotificationMessages.stamp(conversation, notification);
        NotificationMessages.flatIntoExtras(style, notification.extras);
        NotificationMarkdown.apply(notification, true);
        List<Message> messages = NotificationMessages.rebuild(conversation, notification, Collections.emptyList(), true);
        check(messages.size() == 2, "visually identical messages were merged");
        NotificationMessages.recordReply(notification, "**回复**", 200, "markdown-reply");
        check("**回复**".contentEquals(notification.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY)[0]), "reply payload changed");
        notification = normalized(conversation, notification);
        NotificationMarkdown.apply(notification, true);
        messages = NotificationMessages.rebuild(conversation, notification, Collections.emptyList(), true);
        check(messages.size() == 3 && "**回复**".contentEquals(messages.get(2).getText()), "reply source lost");
        NotificationMessages.markRecall(notification, 11);
        messages = NotificationMessages.rebuild(conversation, notification, Collections.emptyList(), true);
        check(messages.size() == 2 && "相同".contentEquals(messages.get(0).getText()), "recall removed wrong line");
    }

    static void sourceSurvivesBuilderAndPreview(Context context) {
        Conversation conversation = conversation();
        Notification notification = normalized(conversation, notification(context, "**原文**", 100));
        NotificationMarkdown.apply(notification, true);
        Notification rebuilt = Notification.Builder.recoverBuilder(context, notification).build();
        check("**原文**".contentEquals(NotificationMessages.rebuild(conversation, rebuilt, Collections.emptyList(), true).get(0).getText()), "builder lost source");
        Notification preview = Notification.Builder.recoverBuilder(context, rebuilt)
                .setStyle(new Notification.BigPictureStyle()).build();
        NotificationMessages.copyIdentityExtras(rebuilt, preview);
        preview.extras = parcel(preview.extras);
        check("**原文**".contentEquals(NotificationMarkdown.originalText(preview.extras)), "preview lost collapsed source");
        check("**原文**".contentEquals(NotificationMessages.rebuild(conversation, preview, Collections.emptyList(), true).get(0).getText()), "preview lost message source");
    }

    static void codeStylesSurviveParcel() {
        String raw = "😀 `**a**`\n```java\n  x();\n```\n**尾**";
        SpannableStringBuilder source = new SpannableStringBuilder(raw);
        source.setSpan(new ForegroundColorSpan(0xff123456), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Bundle bundle = new Bundle();
        bundle.putCharSequence("text", NotificationMarkdown.render(source));
        Spanned text = (Spanned) parcel(bundle).getCharSequence("text");
        check("😀 **a**\n  x();\n尾".contentEquals(text), "code whitespace or delimiters changed");
        TypefaceSpan[] fonts = text.getSpans(0, text.length(), TypefaceSpan.class);
        BackgroundColorSpan[] backgrounds = text.getSpans(0, text.length(), BackgroundColorSpan.class);
        check(fonts.length == 2 && backgrounds.length == 2, "code styles lost in Parcel");
        int[] starts = { 3, 9 }, ends = { 8, 16 };
        for (int i = 0; i < starts.length; i++) {
            check("monospace".equals(fonts[i].getFamily()), "code font family changed");
            check(text.getSpanStart(fonts[i]) == starts[i] && text.getSpanEnd(fonts[i]) == ends[i], "code font offsets");
            check(backgrounds[i].getBackgroundColor() == NotificationMarkdown.CODE_BACKGROUND, "code background changed");
            check(text.getSpanStart(backgrounds[i]) == starts[i] && text.getSpanEnd(backgrounds[i]) == ends[i], "code background offsets");
        }
        StyleSpan[] styles = text.getSpans(0, text.length(), StyleSpan.class);
        check(styles.length == 1 && text.getSpanStart(styles[0]) == 16 && text.getSpanEnd(styles[0]) == 17,
                "code contents acquired emphasis");
        ForegroundColorSpan color = text.getSpans(0, text.length(), ForegroundColorSpan.class)[0];
        check(text.getSpanStart(color) == 0 && text.getSpanEnd(color) == 2, "existing emoji span moved");
        check(raw.contentEquals(source), "code source was mutated");
    }

    static void codeSourceAndReplySurviveRebuild(Context context) {
        String raw = "代码 `**同**`\n```txt\n  x();\n```";
        String reply = "`**回复**`";
        Conversation conversation = conversation();
        Notification notification = normalized(conversation, notification(context, raw, 100));
        Bundle originalBundle = (Bundle) notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)[0];
        NotificationMarkdown.apply(notification, true);
        NotificationMarkdown.apply(notification, true);
        notification.extras = parcel(notification.extras);
        check(raw.contentEquals(originalBundle.getCharSequence("text")), "code source bundle was mutated");
        check(raw.contentEquals(NotificationMessages.rebuild(conversation, notification, Collections.emptyList(), true).get(0).getText()),
                "code source lost after Parcel");
        NotificationMessages.recordReply(notification, reply, 200, "markdown-code-reply");
        notification = normalized(conversation, notification);
        NotificationMarkdown.apply(notification, true);
        notification.extras = parcel(notification.extras);
        List<Message> messages = NotificationMessages.rebuild(conversation, notification, Collections.emptyList(), true);
        check(messages.size() == 2 && raw.contentEquals(messages.get(0).getText()) && reply.contentEquals(messages.get(1).getText()),
                "code source or reply changed");
        check(reply.contentEquals(notification.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY)[0]),
                "code reply payload changed");
        NotificationMarkdown.apply(notification, false);
        Bundle first = (Bundle) notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)[0];
        Bundle last = (Bundle) notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)[1];
        check(raw.contentEquals(first.getCharSequence("text")) && reply.contentEquals(last.getCharSequence("text")),
                "disabled code messages not restored");
        check(raw.contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT)), "disabled code collapsed text not restored");
        check(!last.getBundle("extras").containsKey(NotificationMarkdown.RAW_TEXT), "stale code source metadata");
        check(!last.getBundle("extras").containsKey(NotificationMarkdown.OWNER), "stale code owner metadata");
    }

    static void frameworkCodeCleanupRoundTrip() {
        try {
            String raw = "😀 **粗** `Wi.i`\n```txt\n x();\n```\n尾";
            SpannableStringBuilder original = new SpannableStringBuilder(raw);
            original.setSpan(new ForegroundColorSpan(0xff123456), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            Notification.MessagingStyle.Message message = new Notification.MessagingStyle.Message(
                    NotificationMarkdown.render(original), 100, (android.app.Person) null);
            message.getExtras().putAll(codeMetadata(raw));
            NotificationMarkdownHookProfile profile;
            try {
                profile = NotificationMarkdownHookProfile.resolve(
                        message.getClass(), Notification.MessagingStyle.class, Bundle.class, android.widget.RemoteViews.class);
            } catch (NoSuchMethodException | NoSuchFieldException unavailable) {
                throw new MessageIdentityInstrumentation.Skipped("Framework message cleanup signature unavailable");
            }
            Field textField = message.getClass().getDeclaredField("mText");
            textField.setAccessible(true);
            Method toBundle = message.getClass().getDeclaredMethod("toBundle");
            toBundle.setAccessible(true);
            Method fromBundle = message.getClass().getDeclaredMethod("getMessageFromBundle", Bundle.class);
            fromBundle.setAccessible(true);
            for (int round = 0; round < 3; round++) {
                if (profile.textField == null) profile.callers[0].invoke(message, 0xffeeeeee);
                else profile.cleanup.invoke(message);
                CharSequence cleaned = message.getText();
                CharSequence restored = NotificationMarkdown.restoreStylesAfterCleanup(cleaned, message.getExtras());
                check(cleaned.toString().contentEquals(restored), "restoration changed message characters");
                Spanned styled = (Spanned) restored;
                check(styled.getSpans(0, styled.length(), TypefaceSpan.class).length == 2, "code fonts lost or duplicated");
                check(styled.getSpans(0, styled.length(), BackgroundColorSpan.class).length == 2, "code backgrounds lost or duplicated");
                check(styled.getSpans(0, styled.length(), StyleSpan.class).length == 1, "sanitized emphasis lost");
                check(styled.getSpans(0, styled.length(), ForegroundColorSpan.class).length == 0, "unrelated source color restored");
                check(NotificationMarkdown.restoreStylesAfterCleanup(restored, message.getExtras()) == restored, "restoration not idempotent");
                textField.set(message, restored);
                Bundle bundle = (Bundle) toBundle.invoke(message);
                message = (Notification.MessagingStyle.Message) fromBundle.invoke(null, parcel(bundle));
                check(raw.contentEquals(message.getExtras().getCharSequence(NotificationMarkdown.RAW_TEXT)), "framework lost source metadata");
            }
            check(raw.contentEquals(original), "restoration mutated source");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    static void restorationRejectsUnownedOrChangedText() {
        CharSequence cleaned = "code";
        check(NotificationMarkdown.restoreStylesAfterCleanup(cleaned, new Bundle()) == cleaned, "unmarked message changed");
        Bundle metadata = codeMetadata("`code`");
        metadata.remove(NotificationMarkdown.OWNER);
        check(NotificationMarkdown.restoreStylesAfterCleanup(cleaned, metadata) == cleaned, "ownerless message changed");
        metadata.putString(NotificationMarkdown.OWNER, "another.app");
        check(NotificationMarkdown.restoreStylesAfterCleanup(cleaned, metadata) == cleaned, "other app message changed");
        check(NotificationMarkdown.restoreStylesAfterCleanup("modified", codeMetadata("`code`")) == "modified", "changed text restyled");
        check(NotificationMarkdown.restoreStylesAfterCleanup(cleaned, codeMetadata("**code**")) == cleaned, "emphasis-only message restyled");
        CharSequence heading = "Medium";
        check(NotificationMarkdown.restoreStylesAfterCleanup(heading, codeMetadata("## Other")) == heading,
                "changed heading restyled");
        Bundle foreignHeading = codeMetadata("## Medium");
        foreignHeading.putString(NotificationMarkdown.OWNER, "another.app");
        check(NotificationMarkdown.restoreStylesAfterCleanup(heading, foreignHeading) == heading,
                "foreign heading restyled");
        check(NotificationMarkdown.restoreStylesAfterCleanup(cleaned, codeMetadata("`" + "a".repeat(InlineMarkdown.MAX_LENGTH) + "`")) == cleaned,
                "over-limit source restyled");
    }

    static void restorationCompletesPartialStyles() {
        Bundle metadata = codeMetadata("😀 `Wi.i`");
        SpannableStringBuilder cleaned = new SpannableStringBuilder("😀 Wi.i");
        cleaned.setSpan(new TypefaceSpan("monospace"), 3, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Spanned restored = (Spanned) NotificationMarkdown.restoreStylesAfterCleanup(cleaned, metadata);
        check(restored.getSpans(0, restored.length(), TypefaceSpan.class).length == 1, "existing code font duplicated");
        BackgroundColorSpan[] backgrounds = restored.getSpans(0, restored.length(), BackgroundColorSpan.class);
        check(backgrounds.length == 1 && restored.getSpanStart(backgrounds[0]) == 3 && restored.getSpanEnd(backgrounds[0]) == 7,
                "missing code background or wrong emoji offsets");
        check(backgrounds[0].getBackgroundColor() == NotificationMarkdown.CODE_BACKGROUND, "wrong background color");
        check(cleaned.getSpans(0, cleaned.length(), BackgroundColorSpan.class).length == 0, "cleaned source was mutated");
        check(NotificationMarkdown.restoreStylesAfterCleanup(restored, metadata) == restored, "partial repair duplicates styles");
    }

    private static Bundle codeMetadata(String raw) {
        Bundle metadata = new Bundle();
        metadata.putCharSequence(NotificationMarkdown.RAW_TEXT, raw);
        metadata.putString(NotificationMarkdown.OWNER, WeChatDecorator.WECHAT_PACKAGE);
        return metadata;
    }

    private static Notification notification(Context context, String text, long time) {
        return new Notification.Builder(context, "markdown-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Alice").setContentText(text).setWhen(time).build();
    }

    private static Conversation conversation() {
        Conversation conversation = new Conversation(1);
        conversation.title = "Alice";
        conversation.setType(Conversation.TYPE_DIRECT_MESSAGE);
        return conversation;
    }

    private static Notification normalized(Conversation conversation, Notification notification) {
        NotificationMarkdown.restoreText(notification.extras);
        MessagingStyle style = new MessagingStyle(new Person.Builder().setName("我").build());
        for (Message message : NotificationMessages.rebuild(conversation, notification, Collections.emptyList(), true)) style.addMessage(message);
        NotificationMessages.stamp(conversation, notification);
        NotificationMessages.flatIntoExtras(style, notification.extras);
        notification.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        return notification;
    }

    private static Bundle parcel(Bundle bundle) {
        Parcel parcel = Parcel.obtain();
        try { parcel.writeBundle(bundle); parcel.setDataPosition(0); return parcel.readBundle(NotificationMarkdownInstrumentation.class.getClassLoader()); }
        finally { parcel.recycle(); }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
