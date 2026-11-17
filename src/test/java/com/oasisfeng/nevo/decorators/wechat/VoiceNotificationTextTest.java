package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 28}, manifest = Config.NONE)
public class VoiceNotificationTextTest {
    private Notification notification() {
        Notification.MessagingStyle style = new Notification.MessagingStyle("Synthetic self");
        for (int i = 1; i <= 25; i++) {
            Notification.MessagingStyle.Message message = new Notification.MessagingStyle.Message(
                    i == 1 ? "[语音]" : "Synthetic " + i, i, "Synthetic peer " + i);
            message.getExtras().putLong(NotificationMessages.SERVER_ID, i);
            message.getExtras().putString("synthetic-extra", "retained");
            if (i == 2) message.setData("image/png", android.net.Uri.parse("content://synthetic/image"));
            style.addMessage(message);
        }
        PendingIntent pending = PendingIntent.getBroadcast(RuntimeEnvironment.getApplication(), 1,
                new Intent("synthetic.reply"), PendingIntent.FLAG_IMMUTABLE);
        Notification result = new Notification.Builder(RuntimeEnvironment.getApplication(), "test")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setStyle(style)
                .addAction(new Notification.Action.Builder(null, "Reply", pending).build()).build();
        result.extras.putLong(NotificationMessages.SERVER_ID, 25);
        return result;
    }
    @Test public void lateResultUpdatesOnlyOriginalMessageAndPreservesLaterMessagesAttachmentsAndActions() {
        Notification n = notification();
        PendingIntent action = n.actions[0].actionIntent;
        assertTrue(VoiceNotificationText.apply(n, 1, "**plain transcript**"));
        NotificationMarkdown.apply(n, true);
        Notification rebuilt = Notification.Builder.recoverBuilder(RuntimeEnvironment.getApplication(), n)
                .setOnlyAlertOnce(true).build();
        Parcelable[] rows = rebuilt.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        assertEquals(25, rows.length);
        Bundle first = (Bundle) rows[0], image = (Bundle) rows[1], last = (Bundle) rows[24];
        assertEquals("[语音]\n**plain transcript**", first.getCharSequence("text").toString());
        assertEquals(1, first.getLong("time"));
        assertEquals("Synthetic peer 1", first.getCharSequence("sender").toString());
        assertEquals("retained", first.getBundle("extras").getString("synthetic-extra"));
        assertEquals("image/png", image.getString("type"));
        assertEquals("Synthetic 25", last.getCharSequence("text").toString());
        assertEquals(action, rebuilt.actions[0].actionIntent);
        assertTrue((rebuilt.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
    }
    @Test public void missingOrRecalledIdentityCannotAppendOrReplaceAnything() {
        Notification n = notification();
        assertFalse(VoiceNotificationText.apply(n, 100, "wrong"));
        n.extras.putLongArray(NotificationMessages.RECALLED_IDS, new long[]{1});
        assertFalse(VoiceNotificationText.apply(n, 1, "recalled"));
        assertEquals(25, n.extras.getParcelableArray(Notification.EXTRA_MESSAGES).length);
    }
    @Test public void latestResultUpdatesSummaryAndRepeatRefreshDoesNotDuplicateTranscript() {
        Notification n = notification();
        assertTrue(VoiceNotificationText.apply(n, 25, "first"));
        assertTrue(VoiceNotificationText.apply(n, 25, "**second**"));
        NotificationMarkdown.apply(n, true);
        assertEquals("Synthetic 25\n**second**", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        Bundle last = (Bundle) n.extras.getParcelableArray(Notification.EXTRA_MESSAGES)[24];
        assertEquals("Synthetic 25\n**second**", last.getCharSequence("text").toString());
    }
    @Test public void transcriptSurvivesNextMessageButCannotCrossEndedRound() {
        ConversationManager.Conversation conversation = new ConversationManager.Conversation(5);
        conversation.key = "synthetic-peer";
        conversation.title = "Synthetic chat";
        conversation.notificationPeerId = "synthetic-instance";
        Notification archived = notification();
        archived.extras.putBoolean(NotificationMessages.STORED, true);
        archived.extras.putString(NotificationMessages.KEY, conversation.key);
        archived.extras.putString(NotificationMessages.TITLE, conversation.title.toString());
        archived.extras.putString(NotificationMessages.PEER_ID, conversation.notificationPeerId);
        archived.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 1);
        assertTrue(VoiceNotificationText.apply(archived, 2, "retained text"));
        Notification next = new Notification.Builder(RuntimeEnvironment.getApplication(), "test")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(conversation.title)
                .setContentText("New incoming").build();
        next.extras.putString(NotificationMessages.KEY, conversation.key);
        next.extras.putLong(NotificationMessages.SERVER_ID, 26);
        next.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 2);
        java.util.List<androidx.core.app.NotificationCompat.MessagingStyle.Message> kept =
                NotificationMessages.rebuild(conversation, next, java.util.List.of(archived));
        assertEquals(25, kept.size());
        assertTrue(kept.stream().anyMatch(m -> m.getText().toString().endsWith("\nretained text")));
        conversation.round.end(1);
        java.util.List<androidx.core.app.NotificationCompat.MessagingStyle.Message> newRound =
                NotificationMessages.rebuild(conversation, next, java.util.List.of(archived));
        assertEquals(1, newRound.size());
        assertEquals("New incoming", newRound.get(0).getText().toString());
    }
    @Test public void rawRepostAfterEnablingIsNotEligibleForHistoricalTranscription() {
        ConversationManager.Conversation conversation = new ConversationManager.Conversation(5);
        conversation.key = "synthetic-peer"; conversation.title = "Synthetic chat";
        Notification original = new Notification.Builder(RuntimeEnvironment.getApplication(), "test")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(conversation.title)
                .setContentText("[语音]").build();
        original.extras.putString(NotificationMessages.KEY, conversation.key);
        original.extras.putLong(NotificationMessages.SERVER_ID, 42);
        original.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 10);
        androidx.core.app.NotificationCompat.MessagingStyle first = new androidx.core.app.NotificationCompat.MessagingStyle("Synthetic self");
        for (var message : NotificationMessages.rebuild(conversation, original, java.util.List.of())) first.addMessage(message);
        NotificationMessages.flatIntoExtras(first, original.extras);
        assertTrue(NotificationMessages.isFirstAppearance(original, 42));
        Notification repost = new Notification.Builder(RuntimeEnvironment.getApplication(), "test")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(conversation.title)
                .setContentText("[语音]").build();
        repost.extras.putString(NotificationMessages.KEY, conversation.key);
        repost.extras.putLong(NotificationMessages.SERVER_ID, 42);
        repost.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 11);
        androidx.core.app.NotificationCompat.MessagingStyle repeated = new androidx.core.app.NotificationCompat.MessagingStyle("Synthetic self");
        for (var message : NotificationMessages.rebuild(conversation, repost, java.util.List.of(original))) repeated.addMessage(message);
        NotificationMessages.flatIntoExtras(repeated, repost.extras);
        assertFalse(NotificationMessages.isFirstAppearance(repost, 42));
    }
}
