package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;

import com.oasisfeng.nevo.sdk.Decorating;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Synthetic framework checks exercise the production decorator without posting notifications. */
final class MediaConversationInstrumentation {
    private static final String[] MEDIA = {"[表情]", "[动画表情]", "[图片]", "[视频]", "[文件]报告.pdf", "[链接]合成标题",
            "[音乐]", "[位置]", "[红包]", "[转账]", "[小程序]", "[语音]"};

    static void mediaHistoryUpdatesImmediately(Context context) {
        PendingIntent reply = target(context, 1160);
        try {
            for (boolean nativeReply : new boolean[]{false, true}) {
                try (Fixture fixture = new Fixture(context, nativeReply ? 1161 : 1162, false)) {
                    List<String> expected = new ArrayList<>();
                    expected.add("A");
                    fixture.accept(raw(context, "A", 100, null, nativeReply ? reply : null));
                    fixture.expect(expected);
                    for (int i = 0; i < MEDIA.length; i++) {
                        expected.add(MEDIA[i]);
                        Notification media = raw(context, MEDIA[i], 200 + i * 100, null, nativeReply ? reply : null);
                        RemoteViews nativeCard = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
                        media.contentView = nativeCard;
                        media.bigContentView = nativeCard;
                        media.headsUpContentView = nativeCard;
                        fixture.accept(media);
                        fixture.expect(expected);
                        check(media.contentView == null && media.bigContentView == null && media.headsUpContentView == null,
                                "native media views hid the immediate transcript");
                        check(replyCount(media) == (nativeReply ? 1 : 0), "reply availability changed media history");
                    }
                    expected.add("C");
                    fixture.accept(raw(context, "C", 2000, null, nativeReply ? reply : null));
                    fixture.expect(expected);
                }
            }
        } finally { reply.cancel(); }
    }

    static void groupMediaKeepsSenders(Context context) {
        try (Fixture fixture = new Fixture(context, 1163, false)) {
            fixture.accept(raw(context, "A", 100, "Synthetic member A", null));
            fixture.accept(raw(context, "[文件]报告.pdf", 200, "Synthetic member B", null));
            fixture.expect(Arrays.asList("A", "[文件]报告.pdf"));
            fixture.accept(raw(context, "[动画表情]", 300, "Synthetic member C", null));
            fixture.expect(Arrays.asList("A", "[文件]报告.pdf", "[动画表情]"));
            MessagingStyle style = style(fixture.latest());
            check(style.isGroupConversation() && "Synthetic group".contentEquals(style.getConversationTitle()),
                    "group media lost the conversation title");
            for (int i = 0; i < 3; i++)
                check(("Synthetic member " + (char) ('A' + i)).contentEquals(style.getMessages().get(i).getPerson().getName()),
                        "group media changed sender at " + i);
        }
    }

    static void mediaMarkersKeepOriginalText(Context context) {
        try (Fixture fixture = new Fixture(context, 1164, false)) {
            String literal = "请把[文件]发过来，[表情]、[链接]、[红包]保持原文 [微笑]";
            fixture.accept(raw(context, literal, 100, null, null));
            String translatedEmoji = EmojiTranslator.translate("[微笑]").toString();
            check(!"[微笑]".equals(translatedEmoji), "ordinary emoji fixture did not translate");
            fixture.expect(Arrays.asList(literal.replace("[微笑]", translatedEmoji)));
            check(literal.contentEquals(fixture.latest().extras.getCharSequence(Notification.EXTRA_TEXT)),
                    "body conversion rewrote the original notification text");
            for (String media : MEDIA)
                check(media.contentEquals(EmojiTranslator.translateMessageText(media)), "media placeholder was translated");
        }
    }

    static void mediaReplyActionsArePreserved(Context context) {
        PendingIntent reply = target(context, 1165), other = target(context, 1166);
        try {
            for (boolean car : new boolean[]{true, false}) {
                try (Fixture fixture = new Fixture(context, car ? 1167 : 1168, false)) {
                    Notification notification = raw(context, "[文件]报告.pdf", 100, null, car ? reply : null);
                    Notification.Action open = new Notification.Action.Builder(null, "Open", other).build();
                    Notification.Action choices = new Notification.Action.Builder(null, "Choices", other)
                            .addRemoteInput(new RemoteInput.Builder("choices").setAllowFreeFormInput(false)
                                    .setChoices(new CharSequence[]{"Yes", "No"}).build()).build();
                    Notification.Action data = new Notification.Action.Builder(null, "Data", other)
                            .addRemoteInput(new RemoteInput.Builder("data").setAllowFreeFormInput(false)
                                    .setAllowDataType("image/png", true).build()).build();
                    Notification.Action noIntent = new Notification.Action.Builder(null, "NoIntent", null)
                            .addRemoteInput(new RemoteInput.Builder("free-without-intent").build()).build();
                    Notification.Action oldReply = new Notification.Action.Builder(null, "Old reply", reply)
                            .addRemoteInput(new RemoteInput.Builder("action-key").build()).build();
                    notification.actions = new Notification.Action[]{open, oldReply, null, choices, data, noIntent, oldReply};
                    fixture.accept(notification);
                    fixture.expect(Arrays.asList("[文件]报告.pdf"));
                    Notification.Action[] actions = notification.actions;
                    check(actions.length == 6 && actions[0] == open && actions[1] == null && actions[2] == choices
                                    && actions[3] == data && actions[4] == noIntent && replyCount(notification) == 1,
                            "conversation conversion dropped or reordered native operations");
                    check((car ? "car-key" : "action-key").equals(actions[5].getRemoteInputs()[0].getResultKey()),
                            "reply source priority or result key changed");
                    PendingIntent proxy = actions[5].actionIntent;
                    fixture.accept(notification);
                    fixture.expect(Arrays.asList("[文件]报告.pdf"));
                    check(replyCount(notification) == 1 && proxy.equals(notification.actions[5].actionIntent),
                            "normalized media gained a nested or duplicate reply");
                }
            }
            try (Fixture fixture = new Fixture(context, 1169, false)) {
                Notification notification = raw(context, "[文件]报告.pdf", 100, null, null);
                Notification.Action[] actions = {new Notification.Action.Builder(null, "Open", other).build()};
                notification.actions = actions;
                fixture.accept(notification);
                fixture.expect(Arrays.asList("[文件]报告.pdf"));
                check(notification.actions == actions, "missing reply capability changed native actions");
            }
        } finally { reply.cancel(); other.cancel(); }
    }

    static void mediaHistorySurvivesRebuild(Context context) {
        try (Fixture fixture = new Fixture(context, 1170, false)) {
            fixture.accept(raw(context, "A", 100, null, null));
            fixture.accept(raw(context, "[动画表情]", 200, null, null));
            fixture.accept(raw(context, "[动画表情]", 300, null, null));
            List<String> expected = Arrays.asList("A", "[动画表情]", "[动画表情]");
            fixture.expect(expected);
            Notification rebuilt = Notification.Builder.recoverBuilder(context, fixture.latest()).build();
            Parcel parcel = Parcel.obtain();
            try {
                rebuilt.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                fixture.accept(Notification.CREATOR.createFromParcel(parcel));
                fixture.expect(expected);
            } finally { parcel.recycle(); }
        }
    }

    static void mediaAfterReplyUsesExistingRound(Context context) {
        for (boolean showLastReply : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(context, showLastReply ? 1171 : 1172, showLastReply)) {
                fixture.accept(raw(context, "A", 100, null, null));
                fixture.accept(raw(context, "[文件]报告.pdf", 200, null, null));
                try {
                    java.lang.reflect.Field field = WeChatDecorator.Local.class.getDeclaredField("mReplyController");
                    field.setAccessible(true);
                    MessagingBuilder.Controller controller = (MessagingBuilder.Controller) field.get(fixture);
                    ReplyTarget target = controller.replyTarget(fixture.id);
                    controller.onReplyRead(fixture.id, target);
                    controller.onReplySent(fixture.id, target, "B", 300, "synthetic-media-reply");
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                fixture.expect(Arrays.asList("A", "[文件]报告.pdf", "B"));
                fixture.accept(raw(context, "[视频]", 400, null, null));
                fixture.expect(showLastReply ? Arrays.asList("B", "[视频]") : Arrays.asList("[视频]"));
                fixture.accept(raw(context, "C", 500, null, null));
                fixture.expect(showLastReply ? Arrays.asList("B", "[视频]", "C") : Arrays.asList("[视频]", "C"));
            }
        }
    }

    private static Notification raw(Context context, String body, long time, String member, PendingIntent reply) {
        boolean group = member != null;
        String title = group ? "Synthetic group" : "Synthetic peer";
        String text = group ? member + ": " + body : body;
        Notification.Builder builder = new Notification.Builder(context, "identity-test")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
                .setTicker(title + ": " + text).setWhen(time);
        if (reply != null) {
            Notification.CarExtender.UnreadConversation car = new Notification.CarExtender.Builder(title)
                    .addMessage(text).setLatestTimestamp(time)
                    .setReplyAction(reply, new RemoteInput.Builder("car-key").build()).build();
            builder.extend(new Notification.CarExtender().setUnreadConversation(car));
        }
        Notification notification = builder.build();
        notification.extras.putBoolean(WeChatRecallEvents.VERIFIED_KEY, true);
        notification.extras.putString(NotificationMessages.KEY, group ? "synthetic-group@chatroom" : "synthetic-peer");
        notification.extras.putLong(NotificationMessages.SERVER_ID, time);
        return notification;
    }

    private static PendingIntent target(Context context, int request) {
        return PendingIntent.getBroadcast(context, request,
                new Intent("synthetic.media." + request).setPackage(context.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
    }

    private static MessagingStyle style(Notification notification) {
        MessagingStyle style = MessagingStyle.extractMessagingStyleFromNotification(notification);
        check(style != null, "media did not produce MessagingStyle");
        return style;
    }

    private static int replyCount(Notification notification) {
        int count = 0;
        if (notification.actions != null) for (Notification.Action action : notification.actions) {
            if (action == null || action.actionIntent == null || action.getRemoteInputs() == null) continue;
            for (RemoteInput input : action.getRemoteInputs()) if (input != null && input.getAllowFreeFormInput()) {
                count++;
                break;
            }
        }
        return count;
    }

    private static final class Fixture extends WeChatDecorator.Local implements AutoCloseable {
        final Context context;
        final int id;
        final SharedPreferences preferences;
        final boolean hadDisplayChoice, previousDisplayChoice;

        Fixture(Context context, int id, boolean showLastReply) {
            super("WeChatDecorator");
            this.context = context;
            this.id = id;
            clearArchivedNotifications(id);
            preferences = context.getSharedPreferences("synthetic-media-history", Context.MODE_PRIVATE);
            hadDisplayChoice = preferences.contains(WeChatDecorator.PREF_SHOW_LAST_REPLY);
            previousDisplayChoice = preferences.getBoolean(WeChatDecorator.PREF_SHOW_LAST_REPLY, false);
            check(preferences.edit().putBoolean(WeChatDecorator.PREF_SHOW_LAST_REPLY, showLastReply).commit(),
                    "test display preference was not set");
            onCreate(preferences);
        }

        void accept(Notification notification) {
            check(apply(context.getSystemService(NotificationManager.class), null, id, notification) == Decorating.Processed,
                    "chat media did not complete conversation normalization");
            check(notification.extras.getBoolean(NotificationMessages.STORED), "media snapshot was not normalized");
        }

        Notification latest() { return getArchivedNotification(id); }

        void expect(List<String> expected) {
            List<Message> messages = style(latest()).getMessages();
            check(messages.size() == expected.size(), "unexpected immediate media history length " + messages.size());
            for (int i = 0; i < expected.size(); i++)
                check(expected.get(i).contentEquals(messages.get(i).getText()), "media history changed at " + i);
        }

        @Override void repostIfActive(int id, Notification notification, long expectedToken, long readToken) {}

        @Override public void close() {
            try { clearArchivedNotifications(id); }
            finally {
                onDestroy();
                SharedPreferences.Editor editor = preferences.edit();
                if (hadDisplayChoice) editor.putBoolean(WeChatDecorator.PREF_SHOW_LAST_REPLY, previousDisplayChoice);
                else editor.remove(WeChatDecorator.PREF_SHOW_LAST_REPLY);
                check(editor.commit(), "test display preference was not restored");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private MediaConversationInstrumentation() {}
}
