package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;
import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Tests real Android serialization and reply forwarding without requiring WeChat. */
final class Plan123Instrumentation {
    static void recallSequence(Context context) {
        for (boolean group : new boolean[]{false, true}) {
            Conversation c = conversation(group);
            Notification a = normalize(c, raw(context, "A", 100, 1), null);
            Notification b = normalize(c, raw(context, "B", 200, 2), a);
            Notification staleCopy = b.clone();
            Notification recall = raw(context, "\"Bob\" 撤回了一条消息", 300, 2);
            recall.extras.putBoolean(NotificationMessages.RECALL_PROMPT, true);
            NotificationMessages.markRecall(recall, 2);
            Notification pruned = normalize(c, recall, b);
            texts(c, pruned, "A");
            check("A".contentEquals(pruned.extras.getCharSequence(Notification.EXTRA_TEXT)), "fallback retained recall");
            check(pruned.extras.getBundle(NotificationMessages.ROUND).getLong("latestPeer") == 200,
                    "recall advanced peer watermark");
            Notification d = normalize(c, raw(context, "D", 400, 4), pruned);
            texts(c, d, "A", "D");
            texts(c, normalize(c, d, b), "A", "D");
            texts(c, normalize(c, staleCopy, d), "A");
            Notification preview = new Notification();
            NotificationMessages.copyIdentityExtras(d, preview);
            texts(c, normalize(c, preview, b), "A", "D");
        }
    }

    static void recallWithoutIdentity(Context context) {
        Conversation c = conversation(false);
        Notification a = normalize(c, raw(context, "A", 100, 0), null);
        Notification b = normalize(c, raw(context, "B", 200, 0), a);
        Notification recall = raw(context, "撤回了一条消息", 300, 0);
        recall.extras.putBoolean(NotificationMessages.RECALL_PROMPT, true);
        NotificationMessages.markRecall(recall, 2);
        texts(c, normalize(c, recall, b), "A", "B");
        // Identical text and timestamp are insufficient to remove an untagged message.
        Notification untagged = normalize(c, raw(context, "B", 200, 0), null);
        NotificationMessages.markRecall(untagged, 2);
        texts(c, untagged, "B");
    }

    static void recallSameTextAndTime(Context context) {
        Conversation c = conversation(false);
        Notification a = normalize(c, raw(context, "same", 100, 1), null);
        Notification b = normalize(c, raw(context, "same", 100, 2), a);
        texts(c, b, "same", "same");
        NotificationMessages.markRecall(b, 2);
        texts(c, normalize(c, b, null), "same");
        NotificationMessages.markRecall(b, 1);
        Notification empty = normalize(c, b, null);
        texts(c, empty);
        check(empty.extras.getParcelableArray(Notification.EXTRA_MESSAGES).length == 0, "empty snapshot retained old array");
    }

    static void recallSelfAndRoundMetadata(Context context) {
        Conversation c = conversation(false);
        Notification n = normalize(c, raw(context, "A", 100, 1), null);
        NotificationMessages.recordReply(n, "self", 150, "reply");
        Parcelable[] messages = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        ((Bundle) messages[messages.length - 1]).getBundle("extras").putLong(NotificationMessages.SERVER_ID, 2);
        NotificationMessages.markRecall(n, 2);
        Notification pruned = normalize(c, n, null);
        texts(c, pruned, "A");
        check(pruned.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) == null, "recalled self in input history");
        check(pruned.extras.getBundle(NotificationMessages.ROUND).getStringArrayList("pending").isEmpty(), "recalled reply ID in round");
    }

    static void recallReusedNotificationId(Context context) {
        Conversation old = conversation(false);
        Notification a = normalize(old, raw(context, "old", 100, 1), null);
        NotificationMessages.markRecall(a, 1);
        Conversation fresh = conversation(false); fresh.title = "Carol"; fresh.key = "carol";
        Notification n = raw(context, "new", 200, 1); n.extras.putCharSequence(Notification.EXTRA_TITLE, "Carol");
        Notification current = normalize(fresh, n, a);
        texts(fresh, current, "new");
        check(current.extras.getLongArray(NotificationMessages.RECALLED_IDS).length == 0, "recall crossed conversation");
    }

    static void emptyRecallStaysSuppressed(Context context) {
        WeChatDecorator.Local local = new WeChatDecorator.Local("WeChatDecorator");
        local.onCreate(context.getSharedPreferences("identity-test", Context.MODE_PRIVATE));
        try {
            Notification recall = raw(context, "撤回了一条消息", 100, 1);
            recall.extras.putBoolean(NotificationMessages.RECALL_PROMPT, true);
            android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
            check(local.apply(manager, null, 810, recall) == com.oasisfeng.nevo.sdk.Decorating.StopPost,
                    "empty recall was published");
            check(local.apply(manager, null, 810, recall.clone()) == com.oasisfeng.nevo.sdk.Decorating.StopPost,
                    "repeated empty recall was revived");
        } finally { local.onDestroy(); }
    }

    static void replyAlertsAfterIncoming(Context context) {
        WeChatDecorator.Local local = new WeChatDecorator.Local("WeChatDecorator");
        local.onCreate(context.getSharedPreferences("reply-alert-test", Context.MODE_PRIVATE));
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        try {
            Notification a = raw(context, "A", 100, 1);
            local.apply(manager, null, 830, a);
            NotificationMessages.recordReply(a, "B1", 110, "alert-reply-1");
            NotificationMessages.recordReply(a, "B2", 120, "alert-reply-2");
            local.apply(manager, null, 830, a);
            check((a.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0, "reply refresh could alert again");
            CharSequence[] history = a.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY);
            check(history.length == 2, "consecutive pending replies lost");

            Notification c = raw(context, "C", 200, 2);
            local.apply(manager, null, 830, c);
            texts(conversation(false), c, "B1", "B2", "C");
            check(c.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) == null,
                    "answered replies remained in input history");
            check((c.flags & Notification.FLAG_ONLY_ALERT_ONCE) == 0, "incoming message was muted after reply");

            Notification preview = Notification.Builder.recoverBuilder(context, c)
                    .setStyle(new Notification.BigPictureStyle().bigPicture(
                            android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)))
                    .setOnlyAlertOnce(true).build();
            NotificationMessages.copyIdentityExtras(c, preview);
            local.apply(manager, null, 830, preview);
            check((preview.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0, "preview lost its silent update flag");
            check(preview.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) == null,
                    "preview restored answered input history");

            Notification d = raw(context, "D", 300, 3);
            local.apply(manager, null, 830, d);
            texts(conversation(false), d, "B1", "B2", "C", "D");
            check((d.flags & Notification.FLAG_ONLY_ALERT_ONCE) == 0, "later incoming message was still muted");

            Notification silent = raw(context, "E", 400, 4);
            silent.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
            local.apply(manager, null, 830, silent);
            check((silent.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0, "original silent update flag was cleared");
        } finally { local.onDestroy(); }
    }

    static void replyEligibility(Context context) {
        MessagingBuilder builder = new MessagingBuilder(context, (id, modifies) -> {});
        PendingIntent nativeReply = PendingIntent.getBroadcast(context, 802,
                new Intent("com.oasisfeng.nevo.test.NATIVE").setPackage(context.getPackageName()), flags());
        try {
            for (String ticker : new String[]{null, "", "好友请求", ":hello"}) {
                Notification n = car(context, nativeReply); n.tickerText = ticker;
                check(builder.buildFromExtender(conversation(false), 802, n, Collections.emptyList()) != null,
                        "non-chat content stopped rebuilding");
                check(n.actions == null || n.actions.length == 0, "non-chat gained car reply");
                check(!builder.attachReplyAction(802, n), "non-chat gained media reply");
                Notification.Action original = new Notification.Action.Builder(null, "native", nativeReply)
                        .addRemoteInput(new RemoteInput.Builder("native-key").build()).build();
                n.actions = new Notification.Action[]{original};
                builder.buildFromExtender(conversation(false), 802, n, Collections.emptyList());
                check(n.actions.length == 1 && n.actions[0] == original, "non-chat native action was proxied or deleted");
            }
            Notification media = car(context, nativeReply); media.tickerText = "Group: Bob: [文件]";
            check(builder.attachReplyAction(802, media), "chat media lost reply");
            Notification noNative = raw(context, "hello", 100, 0);
            noNative.tickerText = "Alice: hello";
            noNative.contentIntent = nativeReply;
            check(builder.buildFromExtender(conversation(false), 803, noNative, Collections.emptyList()) != null,
                    "chat without native objects lost message rebuilding");
            check(noNative.actions == null || noNative.actions.length == 0,
                    "ordinary test process exposed an unverified synthetic reply");
        } finally { builder.close(); nativeReply.cancel(); }
    }

    static void mediaReplyActionDeduplication(Context context) {
        MessagingBuilder builder = new MessagingBuilder(context, (id, modifies) -> {});
        PendingIntent nativeReply = PendingIntent.getBroadcast(context, 805,
                new Intent("com.oasisfeng.nevo.test.MEDIA_DEDUP").setPackage(context.getPackageName()), flags());
        PendingIntent otherActionIntent = PendingIntent.getBroadcast(context, 806,
                new Intent("com.oasisfeng.nevo.test.OTHER").setPackage(context.getPackageName()), flags());
        try {
            // Case 1: Repeated attach calls on the same media notification produce exactly one reply action.
            Notification repeatedMedia = car(context, nativeReply);
            repeatedMedia.tickerText = "Alice: [文件]";
            for (int i = 0; i < 3; i++) {
                check(builder.attachReplyAction(805, repeatedMedia), "attachReplyAction failed on iteration " + i);
                check(countFreeFormReplyActions(repeatedMedia) == 1, "reply actions count != 1 on iteration " + i);
                check(repeatedMedia.actions.length == 1, "repeated attach retained extra actions");
            }

            com.oasisfeng.nevo.sdk.NevoDecoratorService.LocalDecorator.markActionsSerialized(repeatedMedia);
            check(builder.attachReplyAction(805, repeatedMedia), "serialized media reply not refreshed");
            check(!com.oasisfeng.nevo.sdk.NevoDecoratorService.LocalDecorator.hasSerializedActions(repeatedMedia),
                    "media action update retained serialized flag");
            Notification rebuilt = Notification.Builder.recoverBuilder(context, repeatedMedia).build();
            android.os.Parcel parcel = android.os.Parcel.obtain();
            try {
                rebuilt.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                Notification outgoing = Notification.CREATOR.createFromParcel(parcel);
                check(outgoing.actions != null && outgoing.actions.length == 1, "serialized media gained duplicate actions");
                RemoteInput[] inputs = outgoing.actions[0].getRemoteInputs();
                check(inputs != null && inputs.length == 1 && "native-key".equals(inputs[0].getResultKey()),
                        "serialized media lost native result key");
            } finally { parcel.recycle(); }

            // Case 2: Actions array with existing reply actions and non-reply actions (interleaved).
            Notification.Action zoomAction = new Notification.Action.Builder(null, "Zoom", otherActionIntent).build();
            Notification.Action markReadAction = new Notification.Action.Builder(null, "MarkRead", otherActionIntent).build();
            Notification.Action oldReply1 = new Notification.Action.Builder(null, "OldReply1", nativeReply)
                    .addRemoteInput(new RemoteInput.Builder("old-key-1").setAllowFreeFormInput(true).build()).build();
            Notification.Action oldReply2 = new Notification.Action.Builder(null, "OldReply2", nativeReply)
                    .addRemoteInput(new RemoteInput.Builder("old-key-2").setAllowFreeFormInput(true).build()).build();

            // Case 3: Preserves data-only inputs, choices-only inputs, null actions, and actions without actionIntent.
            RemoteInput choicesOnlyInput = new RemoteInput.Builder("choices-key")
                    .setAllowFreeFormInput(false)
                    .setChoices(new CharSequence[]{"Yes", "No"})
                    .build();
            Notification.Action choicesAction = new Notification.Action.Builder(null, "Choices", otherActionIntent)
                    .addRemoteInput(choicesOnlyInput).build();

            RemoteInput dataOnlyInput = new RemoteInput.Builder("data-key")
                    .setAllowFreeFormInput(false)
                    .setAllowDataType("image/png", true)
                    .build();
            Notification.Action dataOnlyAction = new Notification.Action.Builder(null, "DataOnly", otherActionIntent)
                    .addRemoteInput(dataOnlyInput).build();

            Notification.Action noIntentAction = new Notification.Action.Builder(null, "NoIntent", null)
                    .addRemoteInput(new RemoteInput.Builder("free-key").setAllowFreeFormInput(true).build()).build();

            Notification complexMedia = car(context, nativeReply);
            complexMedia.tickerText = "Bob: [视频]";
            complexMedia.actions = new Notification.Action[]{
                    zoomAction,
                    null,
                    oldReply1,
                    choicesAction,
                    dataOnlyAction,
                    noIntentAction,
                    oldReply2,
                    markReadAction
            };

            check(builder.attachReplyAction(805, complexMedia), "attachReplyAction failed on complexMedia");
            check(countFreeFormReplyActions(complexMedia) == 1, "complexMedia did not have exactly 1 reply action");

            // Verify non-reply objects and their order are preserved.
            // Expected list: zoomAction, null, choicesAction, dataOnlyAction, noIntentAction, markReadAction, newModuleReplyAction
            Notification.Action[] afterActions = complexMedia.actions;
            check(afterActions != null && afterActions.length == 7, "unexpected actions length after dedup: " + (afterActions == null ? 0 : afterActions.length));
            check(afterActions[0] == zoomAction, "zoomAction identity or order lost");
            check(afterActions[1] == null, "null action element not preserved");
            check(afterActions[2] == choicesAction, "choicesAction identity or order lost");
            check(afterActions[3] == dataOnlyAction, "dataOnlyAction identity or order lost");
            check(afterActions[4] == noIntentAction, "noIntentAction identity or order lost");
            check(afterActions[5] == markReadAction, "markReadAction identity or order lost");
            check(isFreeFormReplyAction(afterActions[6]), "new attached reply action not placed at end");

            // Case 4: Notification with no CarExtender or non-chat ticker does not modify actions.
            Notification nonCarNotification = raw(context, "Hello", 100, 0);
            nonCarNotification.tickerText = "Bob: [文件]";
            Notification.Action[] rawActions = new Notification.Action[]{zoomAction};
            nonCarNotification.actions = rawActions;
            check(!builder.attachReplyAction(805, nonCarNotification), "nonCarNotification gained reply");
            check(nonCarNotification.actions == rawActions && nonCarNotification.actions.length == 1, "nonCar actions altered");

            Notification.CarExtender.UnreadConversation noReplyCar = new Notification.CarExtender.Builder("Alice")
                    .addMessage("hello").setLatestTimestamp(100).build();
            Notification noNativeReply = new Notification.Builder(context, "identity-test")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .extend(new Notification.CarExtender().setUnreadConversation(noReplyCar)).build();
            noNativeReply.tickerText = "Alice: [文件]";
            check(new Notification.CarExtender(noNativeReply).getUnreadConversation() != null,
                    "missing-native-reply fixture lost its car conversation");
            Notification.Action[] noReplyActions = new Notification.Action[]{zoomAction};
            noNativeReply.actions = noReplyActions;
            check(!builder.attachReplyAction(805, noNativeReply), "car without native reply gained an action");
            check(noNativeReply.actions == noReplyActions && noNativeReply.actions[0] == zoomAction,
                    "missing-native-reply guard altered actions");

            Notification nonChatNotification = car(context, nativeReply);
            nonChatNotification.tickerText = "好友请求";
            Notification.Action[] nonChatActions = new Notification.Action[]{zoomAction};
            nonChatNotification.actions = nonChatActions;
            check(!builder.attachReplyAction(805, nonChatNotification), "nonChat gained media reply");
            check(nonChatNotification.actions == nonChatActions && nonChatNotification.actions.length == 1, "nonChat actions altered");
        } finally {
            builder.close();
            nativeReply.cancel();
            otherActionIntent.cancel();
        }
    }

    static void animatedStickerMediaBranch(Context context) {
        PendingIntent nativeReply = PendingIntent.getBroadcast(context, 912,
                new Intent("com.oasisfeng.nevo.test.ANIMATED_STICKER").setPackage(context.getPackageName()), flags());
        PendingIntent otherActionIntent = PendingIntent.getBroadcast(context, 913,
                new Intent("com.oasisfeng.nevo.test.ANIMATED_STICKER_OTHER").setPackage(context.getPackageName()), flags());
        WeChatDecorator.Local local = new WeChatDecorator.Local("WeChatDecorator");
        local.onCreate(context.getSharedPreferences("identity-test", Context.MODE_PRIVATE));
        try {
            Notification n = car(context, nativeReply);
            n.tickerText = "Alice: [动画表情]";
            n.extras.putCharSequence(Notification.EXTRA_TEXT, "[动画表情]");

            Notification.Action nonReplyAction = new Notification.Action.Builder(null, "NonReply", otherActionIntent).build();
            n.actions = new Notification.Action[]{nonReplyAction};

            String originalTemplate = n.extras.getString(Notification.EXTRA_TEMPLATE);
            android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);

            // First apply: hits media branch
            com.oasisfeng.nevo.sdk.Decorating result = local.apply(manager, null, 912, n);
            check(result == com.oasisfeng.nevo.sdk.Decorating.Unprocessed, "animated sticker did not return Unprocessed");
            check("[动画表情]".contentEquals(n.extras.getCharSequence(Notification.EXTRA_TEXT)), "text changed on first apply");
            check(countFreeFormReplyActions(n) == 1, "free form reply action count != 1 on first apply");
            check(n.actions != null && n.actions.length == 2, "actions length != 2 on first apply");
            check(n.actions[0] == nonReplyAction, "non-reply action identity or order lost on first apply");
            check(isFreeFormReplyAction(n.actions[1]), "free form reply action not placed at expected position");

            Notification.Action firstReplyAction = n.actions[1];
            PendingIntent firstProxyIntent = firstReplyAction.actionIntent;
            RemoteInput firstRemoteInput = findFirstFreeFormRemoteInput(n);
            check(firstRemoteInput != null, "firstRemoteInput is null");
            String firstResultKey = firstRemoteInput.getResultKey();
            check("native-key".equals(firstResultKey), "first reply action lost native result key");

            // Mark actions serialized and apply again
            com.oasisfeng.nevo.sdk.NevoDecoratorService.LocalDecorator.markActionsSerialized(n);
            com.oasisfeng.nevo.sdk.Decorating secondResult = local.apply(manager, null, 912, n);
            check(secondResult == com.oasisfeng.nevo.sdk.Decorating.Unprocessed, "second apply did not return Unprocessed");
            check(countFreeFormReplyActions(n) == 1, "reply action count != 1 on second apply");
            check(n.actions != null && n.actions.length == 2, "actions length != 2 on second apply");
            check(n.actions[0] == nonReplyAction, "non-reply action identity or order lost on second apply");
            check(isFreeFormReplyAction(n.actions[1]), "free form reply action not placed at expected position on second apply");
            check(firstProxyIntent != null && firstProxyIntent.equals(n.actions[1].actionIntent), "proxy actionIntent changed on second apply");
            RemoteInput secondRemoteInput = findFirstFreeFormRemoteInput(n);
            check(secondRemoteInput != null && "native-key".equals(secondRemoteInput.getResultKey()), "second apply lost native result key");
            check(!com.oasisfeng.nevo.sdk.NevoDecoratorService.LocalDecorator.hasSerializedActions(n), "serialized flag remained after second apply");
            check("[动画表情]".contentEquals(n.extras.getCharSequence(Notification.EXTRA_TEXT)), "text changed on second apply");
            String secondTemplate = n.extras.getString(Notification.EXTRA_TEMPLATE);
            check((originalTemplate == null && secondTemplate == null) || (originalTemplate != null && originalTemplate.equals(secondTemplate)),
                    "template changed on second apply");

            // Change ticker to "Alice: [2条][动画表情]" and text to "[2条][动画表情]", mark serialized and apply
            n.tickerText = "Alice: [2条][动画表情]";
            n.extras.putCharSequence(Notification.EXTRA_TEXT, "[2条][动画表情]");
            com.oasisfeng.nevo.sdk.NevoDecoratorService.LocalDecorator.markActionsSerialized(n);
            com.oasisfeng.nevo.sdk.Decorating thirdResult = local.apply(manager, null, 912, n);
            check(thirdResult == com.oasisfeng.nevo.sdk.Decorating.Unprocessed, "third apply did not return Unprocessed");
            check(!com.oasisfeng.nevo.sdk.NevoDecoratorService.LocalDecorator.hasSerializedActions(n), "serialized flag remained after third apply");
            check(n.actions != null && n.actions.length == 2, "actions length != 2 on third apply");
            check(n.actions[0] == nonReplyAction, "non-reply action identity or order lost on third apply");
            check(isFreeFormReplyAction(n.actions[1]), "free form reply action not placed at expected position on third apply");
            check(firstProxyIntent != null && firstProxyIntent.equals(n.actions[1].actionIntent), "proxy actionIntent changed on third apply");
            RemoteInput thirdRemoteInput = findFirstFreeFormRemoteInput(n);
            check(thirdRemoteInput != null && "native-key".equals(thirdRemoteInput.getResultKey()), "third apply lost native result key");
            check(countFreeFormReplyActions(n) == 1, "reply action count != 1 on third apply");
            check("[2条][动画表情]".contentEquals(n.extras.getCharSequence(Notification.EXTRA_TEXT)), "text changed on third apply");
        } finally {
            local.onDestroy();
            nativeReply.cancel();
            otherActionIntent.cancel();
        }
    }

    static void animatedStickerReplyAfterUpdate(Context context) {
        final int id = 914;
        final int[] firstCount = {0};
        final int[] latestCount = {0};
        final Bundle[] receivedBundle = {null};
        final CountDownLatch dispatchedLatch = new CountDownLatch(1);
        final CountDownLatch recastLatch = new CountDownLatch(1);

        String actionFirst = "com.oasisfeng.nevo.test.ANIMATED_REPLY_FIRST";
        String actionLatest = "com.oasisfeng.nevo.test.ANIMATED_REPLY_LATEST";

        BroadcastReceiver nativeTarget = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (actionFirst.equals(intent.getAction())) {
                    firstCount[0]++;
                } else if (actionLatest.equals(intent.getAction())) {
                    latestCount[0]++;
                    receivedBundle[0] = RemoteInput.getResultsFromIntent(intent);
                    dispatchedLatch.countDown();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(actionFirst);
        filter.addAction(actionLatest);
        ContextCompat.registerReceiver(context, nativeTarget, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        PendingIntent firstNative = PendingIntent.getBroadcast(context, 914,
                new Intent(actionFirst).setPackage(context.getPackageName()), flags());
        PendingIntent latestNative = PendingIntent.getBroadcast(context, 915,
                new Intent(actionLatest).setPackage(context.getPackageName()), flags());

        final Notification[] latestNotification = {null};
        MessagingBuilder[] builderHolder = new MessagingBuilder[1];
        MessagingBuilder.Controller controller = new MessagingBuilder.Controller() {
            @Override public void recastNotification(int recastId, WeChatDecorator.ModifyNotification... modifies) {
                if (recastId == id && latestNotification[0] != null) {
                    for (WeChatDecorator.ModifyNotification m : modifies) m.modify(latestNotification[0]);
                    builderHolder[0].attachReplyAction(id, latestNotification[0]);
                    recastLatch.countDown();
                }
            }
        };
        MessagingBuilder builder = new MessagingBuilder(context, controller);
        builderHolder[0] = builder;

        try {
            // Notification 1: first message arrived, reply action attached
            Notification notif1 = car(context, firstNative);
            notif1.tickerText = "Alice: first";
            notif1.extras.putCharSequence(Notification.EXTRA_TEXT, "first");
            check(builder.attachReplyAction(id, notif1), "notif1 failed to attach reply");

            Notification.Action oldReplyAction = notif1.actions[0];
            PendingIntent oldProxy = oldReplyAction.actionIntent;
            RemoteInput oldInput = findFirstFreeFormRemoteInput(notif1);
            check(oldInput != null && "native-key".equals(oldInput.getResultKey()), "oldInput invalid on notif1");

            // Notification 2 arrives: animated sticker second message
            Notification notif2 = car(context, latestNative);
            notif2.tickerText = "Alice: [动画表情] second";
            notif2.extras.putCharSequence(Notification.EXTRA_TEXT, "[动画表情] second");
            latestNotification[0] = notif2;
            check(builder.attachReplyAction(id, notif2), "notif2 failed to attach reply");

            RemoteInput notif2Input = findFirstFreeFormRemoteInput(notif2);
            check(notif2Input != null && "native-key".equals(notif2Input.getResultKey()), "notif2 lost native result key");
            check(oldProxy != null && oldProxy.equals(notif2.actions[0].actionIntent), "oldProxy identity lost on notif2");
            check(countFreeFormReplyActions(notif2) == 1, "notif2 free form reply action count != 1");

            // Repeated attach notif2: reply count remains 1 and proxy identity preserved
            check(builder.attachReplyAction(id, notif2), "repeated attach notif2 failed");
            check(countFreeFormReplyActions(notif2) == 1, "repeated attach notif2 reply count != 1");
            check(oldProxy.equals(notif2.actions[0].actionIntent), "proxy identity lost after repeated attach");

            // Dispatch reply via oldProxy.send(context, 0, fillInIntent)
            Intent fillInIntent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putCharSequence("native-key", "动画表情后的回复🙂");
            RemoteInput.addResultsToIntent(new RemoteInput[]{oldInput}, fillInIntent, bundle);

            oldProxy.send(context, 0, fillInIntent);

            check(dispatchedLatch.await(5, TimeUnit.SECONDS), "native reply dispatch timed out");
            check(recastLatch.await(5, TimeUnit.SECONDS), "recastNotification callback timed out");

            check(firstCount[0] == 0, "firstCount != 0, was " + firstCount[0]);
            check(latestCount[0] == 1, "latestCount != 1, was " + latestCount[0]);
            check(receivedBundle[0] != null && "动画表情后的回复🙂".contentEquals(receivedBundle[0].getCharSequence("native-key")),
                    "unexpected reply content in native target");

            check("[动画表情] second".contentEquals(notif2.extras.getCharSequence(Notification.EXTRA_TEXT)),
                    "second notification text lost");
            check(countFreeFormReplyActions(notif2) == 1, "notif2 reply action count != 1 after recast");
            CharSequence[] history2 = notif2.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY);
            check(history2 != null && history2.length == 1 && "动画表情后的回复🙂".contentEquals(history2[0]),
                    "reply history not recorded on latest notification");
            check(notif1.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) == null,
                    "reply history was written into old notification");
            check(oldProxy.equals(notif2.actions[0].actionIntent), "currentProxy identity changed after reply recast");
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            builder.close();
            firstNative.cancel();
            latestNative.cancel();
            context.unregisterReceiver(nativeTarget);
        }
    }

    static void concurrentMediaReplyRecast(Context context) {
        final int id = 807;
        final int[] dispatchCount = {0};
        final Bundle[] receivedBundle = {null};
        final CountDownLatch dispatchedLatch = new CountDownLatch(1);
        final CountDownLatch recastLatch = new CountDownLatch(1);

        BroadcastReceiver nativeTarget = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                dispatchCount[0]++;
                receivedBundle[0] = RemoteInput.getResultsFromIntent(intent);
                dispatchedLatch.countDown();
            }
        };
        String testAction = "com.oasisfeng.nevo.test.CONCURRENT_MEDIA_REPLY";
        ContextCompat.registerReceiver(context, nativeTarget, new IntentFilter(testAction), ContextCompat.RECEIVER_NOT_EXPORTED);
        PendingIntent nativeReply = PendingIntent.getBroadcast(context, id,
                new Intent(testAction).setPackage(context.getPackageName()), flags());

        final Notification[] latestNotification = {null};
        MessagingBuilder[] builderHolder = new MessagingBuilder[1];
        MessagingBuilder.Controller controller = new MessagingBuilder.Controller() {
            @Override public void recastNotification(int recastId, WeChatDecorator.ModifyNotification... modifies) {
                if (recastId == id && latestNotification[0] != null) {
                    for (WeChatDecorator.ModifyNotification m : modifies) m.modify(latestNotification[0]);
                    builderHolder[0].attachReplyAction(id, latestNotification[0]);
                    recastLatch.countDown();
                }
            }
        };
        MessagingBuilder builder = new MessagingBuilder(context, controller);
        builderHolder[0] = builder;

        try {
            // Notification 1: first message arrived, reply action attached and user starts typing.
            Notification notif1 = car(context, nativeReply);
            notif1.tickerText = "Alice: first";
            notif1.extras.putCharSequence(Notification.EXTRA_TEXT, "first");
            check(builder.attachReplyAction(id, notif1), "notif1 failed to attach reply");

            // Notification 2 arrives for the same notification ID before reply is sent, replacing latest.
            Notification notif2 = car(context, nativeReply);
            notif2.tickerText = "Alice: [文件] second";
            notif2.extras.putCharSequence(Notification.EXTRA_TEXT, "[文件] second");
            check(builder.attachReplyAction(id, notif2), "notif2 failed to attach reply");
            latestNotification[0] = notif2;

            // Trigger reply dispatch via builder's real mReplyReceiver simulating reply to notif1.
            Intent proxy = new Intent().setData(android.net.Uri.fromParts("id", String.valueOf(id), null))
                    .putExtra("pending_intent", nativeReply)
                    .putExtra("result_key", "native-key");
            Bundle results = new Bundle();
            results.putCharSequence("native-key", "Test reply");
            RemoteInput.addResultsToIntent(new RemoteInput[]{new RemoteInput.Builder("native-key").build()}, proxy, results);

            java.lang.reflect.Field field = MessagingBuilder.class.getDeclaredField("mReplyReceiver");
            field.setAccessible(true);
            ((BroadcastReceiver) field.get(builder)).onReceive(context, proxy);

            check(dispatchedLatch.await(5, TimeUnit.SECONDS), "native reply dispatch timed out");
            check(recastLatch.await(5, TimeUnit.SECONDS), "recastNotification callback timed out");

            check(dispatchCount[0] == 1, "native reply dispatched " + dispatchCount[0] + " times, expected 1");
            check(receivedBundle[0] != null && "Test reply".contentEquals(receivedBundle[0].getCharSequence("native-key")),
                    "unexpected reply content in native target");

            // Verify latestNotification[0] (notif2):
            // 1. The callback modifies the latest notification and preserves its actual text.
            check(latestNotification[0] == notif2, "recast did not use latest notification");
            check("[文件] second".contentEquals(latestNotification[0].extras.getCharSequence(Notification.EXTRA_TEXT)),
                    "second notification content lost");
            check(notif1.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY) == null,
                    "reply history was written into the old notification");
            // 2. Exactly 1 reply action.
            check(countFreeFormReplyActions(latestNotification[0]) == 1,
                    "recast notification action count != 1, found: " + countFreeFormReplyActions(latestNotification[0]));
            // 3. ResultKey is native-key.
            RemoteInput activeInput = findFirstFreeFormRemoteInput(latestNotification[0]);
            check(activeInput != null && "native-key".equals(activeInput.getResultKey()),
                    "resultKey != native-key: " + (activeInput == null ? "null" : activeInput.getResultKey()));
            // 4. Remote input history recorded the reply.
            CharSequence[] history = latestNotification[0].extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY);
            check(history != null && history.length == 1 && "Test reply".contentEquals(history[0]),
                    "reply history not recorded on latest notification");
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            builder.close();
            nativeReply.cancel();
            context.unregisterReceiver(nativeTarget);
        }
    }

    private static int countFreeFormReplyActions(Notification n) {
        int count = 0;
        if (n.actions != null) {
            for (Notification.Action action : n.actions) {
                if (isFreeFormReplyAction(action)) count++;
            }
        }
        return count;
    }

    private static boolean isFreeFormReplyAction(Notification.Action action) {
        if (action == null || action.actionIntent == null) return false;
        RemoteInput[] inputs = action.getRemoteInputs();
        if (inputs != null) {
            for (RemoteInput input : inputs) {
                if (input != null && input.getAllowFreeFormInput()) return true;
            }
        }
        return false;
    }

    private static RemoteInput findFirstFreeFormRemoteInput(Notification n) {
        if (n.actions != null) {
            for (Notification.Action action : n.actions) {
                if (action != null && action.actionIntent != null && action.getRemoteInputs() != null) {
                    for (RemoteInput input : action.getRemoteInputs()) {
                        if (input != null && input.getAllowFreeFormInput()) return input;
                    }
                }
            }
        }
        return null;
    }

    static void nativeReplyForwarding(Context context) {
        final Bundle[] received = {null};
        CountDownLatch forwarded = new CountDownLatch(1), recorded = new CountDownLatch(1);
        BroadcastReceiver target = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                received[0] = RemoteInput.getResultsFromIntent(intent); forwarded.countDown();
            }
        };
        String action = "com.oasisfeng.nevo.test.FORWARD";
        ContextCompat.registerReceiver(context, target, new IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED);
        PendingIntent nativeReply = PendingIntent.getBroadcast(context, 804, new Intent(action).setPackage(context.getPackageName()), flags());
        Notification history = normalize(conversation(false), raw(context, "A", 100, 1), null);
        NotificationMessages.recordReply(history, "earlier", 150, "earlier");
        MessagingBuilder builder = new MessagingBuilder(context, (id, modifies) -> {
            for (WeChatDecorator.ModifyNotification modify : modifies) modify.modify(history);
            recorded.countDown();
        });
        try {
            Intent proxy = new Intent().setData(android.net.Uri.fromParts("id", "804", null))
                    .putExtra("pending_intent", nativeReply).putExtra("result_key", "native-key")
                    .putExtra("reply_prefix", "@Bob\u2005");
            Bundle results = new Bundle(); results.putCharSequence("native-key", "hello");
            RemoteInput.addResultsToIntent(new RemoteInput[]{new RemoteInput.Builder("native-key").build()}, proxy, results);
            java.lang.reflect.Field field = MessagingBuilder.class.getDeclaredField("mReplyReceiver"); field.setAccessible(true);
            ((BroadcastReceiver) field.get(builder)).onReceive(context, proxy);
            check(forwarded.await(5, TimeUnit.SECONDS), "native results not forwarded");
            check("@Bob\u2005hello".contentEquals(received[0].getCharSequence("native-key")), "mention prefix/result key lost");
            check(recorded.await(5, TimeUnit.SECONDS), "reply success not recorded");
            CharSequence[] replies = history.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY);
            check(replies.length == 2 && "earlier".contentEquals(replies[1]), "continuous reply history lost");
        } catch (Exception error) { throw new AssertionError(error); }
        finally { builder.close(); nativeReply.cancel(); context.unregisterReceiver(target); }
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
    }
    private static Notification car(Context context, PendingIntent reply) {
        Notification.CarExtender.UnreadConversation unread = new Notification.CarExtender.Builder("Alice")
                .addMessage("hello").setLatestTimestamp(100).setReplyAction(reply, new RemoteInput.Builder("native-key").build()).build();
        return new Notification.Builder(context, "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Alice").setContentText("hello").setWhen(100)
                .extend(new Notification.CarExtender().setUnreadConversation(unread)).build();
    }
    private static Conversation conversation(boolean group) {
        Conversation c = new Conversation(1); c.title = "Alice"; c.key = group ? "group@chatroom" : "alice";
        c.setType(group ? Conversation.TYPE_GROUP_CHAT : Conversation.TYPE_DIRECT_MESSAGE); return c;
    }
    private static Notification raw(Context context, String text, long time, long serverId) {
        Notification n = new Notification.Builder(context, "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Alice").setContentText(text).setWhen(time).build();
        if (serverId > 0) n.extras.putLong(NotificationMessages.SERVER_ID, serverId);
        return n;
    }
    private static Notification normalize(Conversation c, Notification n, Notification old) {
        MessagingStyle style = new MessagingStyle(new Person.Builder().setName("我").build());
        List<Message> messages = NotificationMessages.rebuild(c, n, old == null ? Collections.emptyList() : Collections.singletonList(old), true);
        for (Message message : messages) style.addMessage(message);
        NotificationMessages.stamp(c, n); MessagingBuilder.flatIntoExtras(style, n.extras);
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle"); return n;
    }
    private static void texts(Conversation c, Notification n, String... expected) {
        List<Message> messages = NotificationMessages.rebuild(c, n, Collections.emptyList(), true);
        check(messages.size() == expected.length, "unexpected history length " + messages.size());
        for (int i = 0; i < expected.length; i++) check(expected[i].contentEquals(messages.get(i).getText()), "history mismatch at " + i);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
