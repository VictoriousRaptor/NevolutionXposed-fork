package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class ReplyEchoTest {
    @Test @Config(sdk = 26) public void completionSupportsMinimumSdkAndKeepsLatestMessages() {
        Context context = RuntimeEnvironment.getApplication();
        Notification.MessagingStyle messaging = new Notification.MessagingStyle("Synthetic self");
        for (int i = 0; i < Notification.MessagingStyle.MAXIMUM_RETAINED_MESSAGES; i++) {
            Notification.MessagingStyle.Message message =
                    new Notification.MessagingStyle.Message("Synthetic " + i, i, "Synthetic peer");
            if (i == 1) {
                message.setData("image/png", android.net.Uri.parse("content://synthetic/image"));
                message.getExtras().putString("synthetic-metadata", "retained");
            }
            messaging.addMessage(message);
        }
        Notification active = new Notification.Builder(context, "test")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setStyle(messaging).build();
        Notification completed = NotificationMessages.completeReplyUi(context, active, "B");
        androidx.core.app.NotificationCompat.MessagingStyle style =
                androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(completed);
        assertEquals(Notification.MessagingStyle.MAXIMUM_RETAINED_MESSAGES, style.getMessages().size());
        assertEquals("Synthetic 1", style.getMessages().get(0).getText().toString());
        assertEquals("image/png", style.getMessages().get(0).getDataMimeType());
        assertEquals(android.net.Uri.parse("content://synthetic/image"), style.getMessages().get(0).getDataUri());
        assertEquals("retained", style.getMessages().get(0).getExtras().getString("synthetic-metadata"));
        assertEquals("B", style.getMessages().get(24).getText().toString());
        assertNull(style.getMessages().get(24).getPerson());
        assertEquals("Synthetic 0", androidx.core.app.NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(active).getMessages().get(0).getText().toString());
    }

    @Test public void completionKeepsSubmittedReplyVisible() {
        Context context = RuntimeEnvironment.getApplication();
        Notification active = new Notification.Builder(context, "test")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(new Notification.MessagingStyle("Synthetic self")
                        .addMessage("A", 1, "Synthetic peer"))
                .setRemoteInputHistory(new CharSequence[]{"B"}).build();
        Notification completed = NotificationMessages.completeReplyUi(context, active, "B");
        androidx.core.app.NotificationCompat.MessagingStyle style = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(completed);
        assertEquals(2, style.getMessages().size());
        assertEquals("B", style.getMessages().get(1).getText().toString());
        assertNull(style.getMessages().get(1).getPerson());
        assertEquals(1, androidx.core.app.NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(active).getMessages().size());
        assertNull(completed.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY));
        assertTrue((completed.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
    }

    @Test public void repeatedRepliesRemainOnCurrentCardWithoutSystemInputHistory() {
        Context context = RuntimeEnvironment.getApplication();
        Notification active = new Notification.Builder(context, "test")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(new Notification.MessagingStyle("Synthetic self")
                        .setConversationTitle("Synthetic group").setGroupConversation(true)
                        .addMessage("A", 1, "Synthetic peer")).build();
        Notification first = NotificationMessages.completeReplyUi(context, active, "B");
        Notification second = NotificationMessages.completeReplyUi(context, first, "B");
        androidx.core.app.NotificationCompat.MessagingStyle style =
                androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(second);
        assertEquals(3, style.getMessages().size());
        assertEquals("B", style.getMessages().get(1).getText().toString());
        assertEquals("B", style.getMessages().get(2).getText().toString());
        assertNull(style.getMessages().get(2).getPerson());
        assertTrue(style.isGroupConversation());
        assertEquals("Synthetic group", style.getConversationTitle().toString());
    }

    @Test public void failedDispatchClearsSpinnerWithoutAddingReply() {
        Context context = RuntimeEnvironment.getApplication();
        Notification active = new Notification.Builder(context, "test")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(new Notification.MessagingStyle("Synthetic self")
                        .addMessage("A", 1, "Synthetic peer"))
                .setRemoteInputHistory(new CharSequence[]{"Unsent"}).build();
        active.extras.putBoolean(NotificationMessages.REMOTE_INPUT_SPINNER, true);
        Notification completed = NotificationMessages.completeReplyUi(context, active, null);
        assertEquals(1, androidx.core.app.NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(completed).getMessages().size());
        assertFalse(completed.extras.getBoolean(NotificationMessages.REMOTE_INPUT_SPINNER));
        assertNull(completed.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY));
    }

    @Test public void echoedReplyDoesNotLeakIntoNextRound() {
        ConversationRoundInstrumentation.replyCompletionPresentation(RuntimeEnvironment.getApplication());
    }
}
