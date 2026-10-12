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

import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;
import androidx.core.app.Person;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;

import java.util.Collections;
import java.util.List;

/** Framework regression cases, registered in the existing runner. Compilation is not execution. */
final class NotificationMarkdownInstrumentation {
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
            List<Message> messages = NotificationMessages.rebuild(conversation, notification, Collections.emptyList());
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
        MessagingBuilder.flatIntoExtras(style, notification.extras);
        NotificationMarkdown.apply(notification, true);
        List<Message> messages = NotificationMessages.rebuild(conversation, notification, Collections.emptyList());
        check(messages.size() == 2, "visually identical messages were merged");
        NotificationMessages.recordReply(notification, "**回复**", 200, "markdown-reply");
        check("**回复**".contentEquals(notification.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY)[0]), "reply payload changed");
        notification = normalized(conversation, notification);
        NotificationMarkdown.apply(notification, true);
        messages = NotificationMessages.rebuild(conversation, notification, Collections.emptyList());
        check(messages.size() == 3 && "**回复**".contentEquals(messages.get(2).getText()), "reply source lost");
        NotificationMessages.markRecall(notification, 11);
        messages = NotificationMessages.rebuild(conversation, notification, Collections.emptyList());
        check(messages.size() == 2 && "相同".contentEquals(messages.get(0).getText()), "recall removed wrong line");
    }

    static void sourceSurvivesBuilderAndPreview(Context context) {
        Conversation conversation = conversation();
        Notification notification = normalized(conversation, notification(context, "**原文**", 100));
        NotificationMarkdown.apply(notification, true);
        Notification rebuilt = Notification.Builder.recoverBuilder(context, notification).build();
        check("**原文**".contentEquals(NotificationMessages.rebuild(conversation, rebuilt, Collections.emptyList()).get(0).getText()), "builder lost source");
        Notification preview = Notification.Builder.recoverBuilder(context, rebuilt)
                .setStyle(new Notification.BigPictureStyle()).build();
        NotificationMessages.copyIdentityExtras(rebuilt, preview);
        preview.extras = parcel(preview.extras);
        check("**原文**".contentEquals(NotificationMarkdown.originalText(preview.extras)), "preview lost collapsed source");
        check("**原文**".contentEquals(NotificationMessages.rebuild(conversation, preview, Collections.emptyList()).get(0).getText()), "preview lost message source");
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
        check(raw.contentEquals(NotificationMessages.rebuild(conversation, notification, Collections.emptyList()).get(0).getText()),
                "code source lost after Parcel");
        NotificationMessages.recordReply(notification, reply, 200, "markdown-code-reply");
        notification = normalized(conversation, notification);
        NotificationMarkdown.apply(notification, true);
        notification.extras = parcel(notification.extras);
        List<Message> messages = NotificationMessages.rebuild(conversation, notification, Collections.emptyList());
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
        for (Message message : NotificationMessages.rebuild(conversation, notification, Collections.emptyList())) style.addMessage(message);
        NotificationMessages.stamp(conversation, notification);
        MessagingBuilder.flatIntoExtras(style, notification.extras);
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
