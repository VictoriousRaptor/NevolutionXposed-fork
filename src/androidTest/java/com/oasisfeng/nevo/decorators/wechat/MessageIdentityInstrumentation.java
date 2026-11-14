package com.oasisfeng.nevo.decorators.wechat;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationCompat.MessagingStyle.Message;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import com.oasisfeng.nevo.decorators.wechat.ConversationManager.Conversation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Framework-only instrumentation runner: no test-library download or host WeChat is required. */
public final class MessageIdentityInstrumentation extends Instrumentation {
    static final class Skipped extends RuntimeException {
        Skipped(String reason) { super(reason); }
    }

    private boolean verifyWeChat;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        verifyWeChat = arguments != null && "true".equals(arguments.getString("verify_wechat_profiles"));
        start();
    }

    @Override public void onStart() {
        if (verifyWeChat) {
            Bundle result = new Bundle();
            try {
                int checks = WeChatDescriptorChecks.verify(getTargetContext());
                result.putString("stream", "PASS installed WeChat descriptors: " + checks + " checks\n");
                finish(Activity.RESULT_OK, result);
            } catch (Throwable failure) {
                result.putString("stream", "FAIL installed WeChat descriptors: " + failure + "\n");
                finish(Activity.RESULT_CANCELED, result);
            }
            return;
        }
        if (Build.VERSION.SDK_INT < 28) {
            Bundle result = new Bundle();
            result.putString("shortMsg", "Framework instrumentation requires API 28 or newer");
            finish(Activity.RESULT_CANCELED, result);
            return;
        }
        runFrameworkTests();
    }

    @androidx.annotation.RequiresApi(28)
    private void runFrameworkTests() {
        com.oasisfeng.nevo.sdk.NevoDecoratorService.setAppContext(getTargetContext());
        String[] names = { "carExtensionWithoutConversation", "channelMigrationPreservesSettings",
                "personAndAttachmentRoundTrip", "staleTickerAndCarHistory", "unknownAndGroupNames",
                "repeatedRebuildAndPreview", "untrustedMissingSender", "reusedNotificationId",
                "rawPersonOnlyNotification", "nativeMessagingSelfIgnored", "rawColonText", "undatedSnapshots",
                "stableMessageIdRepublish", "stableMessageIdRawHistory", "stableMessageIdBundleHistory", "distinctVoiceMessages",
                "stableMessageIdSenderIsolation", "stableMessageIdConversationIsolation",
                "stableMessageIdPreservesAttachment",
                "rawMediaBetweenTextMessages", "rawMediaInGroupHistory", "rawMediaRespectsReadBoundary", "rawMediaSubmissionEndsHistory",
                "rawMediaAndNativeHistoryDoNotDuplicate", "rawMediaInBotHistory",
                "stablePeerAcrossTalkerAndAvatar", "submissionEndsRound", "capturedTargetPreservesNewer",
                "repeatedSubmissionPreservesNextRound", "stableIdsAcrossEndedRounds", "roundGenerationOrder",
                "nativeHistoryCannotCrossEnd", "staleSnapshotCannotRevive", "groupRoundSenderIsolation",
                "roundHistoryBound", "roundStableIdBound", "snapshotSharesRound", "controllerRetiresArchive",
                "controllerCapturedTargetPreservesNewer", "controllerWithoutActiveNotification", "dismissUsesNativeRead",
                "nativeReplyReadBeforeFailure", "syntheticReplyReadBeforeFailure", "nativeReplyDoesNotReuseReadTarget", "replyCompletionPresentation", "replyCompletionPublicationGuard",
                "classifiesRawFirstMessages", "classificationSurvivesNextMessage", "lateTalkerIsIsolated",
                "legacyGroupGuessIsRepaired", "nativeGroupEvidence", "replyInputsUseLocalizedReplyLabel",
                "replyActionSurvivesArchiveRemoval", "recallSequence", "recallWithoutIdentity", "recallSameTextAndTime",
                "recallLegacySelfAndRoundMetadata", "recallReusedNotificationId", "emptyRecallStaysSuppressed",
                "replyEligibility", "nativeReplyForwarding", "replyAlertsAfterIncoming",
                "mediaReplyActionDeduplication", "concurrentMediaReplySubmission",
                "animatedStickerMediaBranch", "animatedStickerReplyAfterUpdate",
                "markdownStylesAndParcel", "markdownRebuildAndDisable", "markdownIdentityRecall", "markdownBuilderAndPreview",
                "markdownCodeStylesAndParcel", "markdownCodeSourceAndIncoming",
                "markdownFrameworkCodeCleanup", "markdownRestorationScope", "markdownPartialCodeStyles",
                "markdownHeadingsAndInlineStyles", "markdownHeadingRebuildAndDisable", "markdownFrameworkHeadingCleanup",
                "imagePreviewFitStartAndActions", "verifiedImageIdentitySurvivesPreviewRoundTrip",
                "pictureAndPlaceholderAreExclusive", "imageMessageIdentitySurvivesLaterText",
                "imagePreviewMessagingAlignment", "imagePreviewOffloadedPictureIcon", "imagePreviewDiscardClearsLayout", "imagePreviewModulePackageLookupUnavailable", "imageWaitPreferenceSync",
                "notificationNativeAvatar", "notificationAppearanceScope", "notificationCollapsedAppName",
                "privateTextReplacesNativeViewsFromFirstMessage", "privateCompactHeadsUpRenderAndReapply",
                "mediaHistoryUpdatesImmediately", "groupMediaKeepsSenders", "mediaMarkersKeepOriginalText",
                "mediaReplyActionsArePreserved", "mediaHistorySurvivesRebuild", "mediaAfterReplyUsesExistingRound",
                "clickUsesRemovedNativeRead", "removalReadGuards", "cancelledRemovalReadReturnsNormally",
                "removalFailuresStillResetHistory", "removalResetFailureDoesNotSuppressRead",
                "imageRemovalCapabilityAndCache", "imageReapplyCapability", "conversationDataCapability", "messagingDataCapability",
                "initialAvatarCancellationAndReplacement", "initialAvatarRecallAndAlert" };
        Runnable[] tests = { () -> NotificationCompatibilityInstrumentation.carExtensionWithoutConversation(getTargetContext()),
                NotificationCompatibilityInstrumentation::channelMigrationPreservesSettings,
                this::personAndAttachmentRoundTrip, this::staleTickerAndCarHistory, this::unknownAndGroupNames,
                this::repeatedRebuildAndPreview, this::untrustedMissingSender, this::reusedNotificationId,
                this::rawPersonOnlyNotification, this::nativeMessagingSelfIgnored, this::rawColonText, this::undatedSnapshots,
                this::stableMessageIdRepublish, this::stableMessageIdRawHistory, this::stableMessageIdBundleHistory, this::distinctVoiceMessages,
                this::stableMessageIdSenderIsolation, this::stableMessageIdConversationIsolation,
                this::stableMessageIdPreservesAttachment,
                this::rawMediaBetweenTextMessages, this::rawMediaInGroupHistory, this::rawMediaRespectsReadBoundary,
                this::rawMediaSubmissionEndsHistory, this::rawMediaAndNativeHistoryDoNotDuplicate,
                this::rawMediaInBotHistory,
                this::stablePeerAcrossTalkerAndAvatar,
                () -> ConversationRoundInstrumentation.submissionEndsRound(getTargetContext()),
                () -> ConversationRoundInstrumentation.capturedTargetPreservesNewer(getTargetContext()),
                () -> ConversationRoundInstrumentation.repeatedSubmissionPreservesNextRound(getTargetContext()),
                () -> ConversationRoundInstrumentation.stableIdsAcrossEndedRounds(getTargetContext()),
                () -> ConversationRoundInstrumentation.generationOrder(getTargetContext()),
                () -> ConversationRoundInstrumentation.nativeHistoryCannotCrossEnd(getTargetContext()),
                () -> ConversationRoundInstrumentation.staleSnapshotCannotRevive(getTargetContext()),
                () -> ConversationRoundInstrumentation.groupSenderIsolation(getTargetContext()),
                () -> ConversationRoundInstrumentation.historyBound(getTargetContext()),
                ConversationRoundInstrumentation::stableIdBound,
                () -> ConversationRoundInstrumentation.snapshotSharesRound(getTargetContext()),
                () -> ConversationRoundInstrumentation.controllerRetiresArchive(getTargetContext()),
                () -> ConversationRoundInstrumentation.controllerCapturedTargetPreservesNewer(getTargetContext()),
                () -> ConversationRoundInstrumentation.controllerWithoutActiveNotification(getTargetContext()),
                this::dismissUsesNativeRead,
                () -> ReplyReadInstrumentation.nativeReplyReadBeforeFailure(getTargetContext()),
                () -> ReplyReadInstrumentation.syntheticReplyReadBeforeFailure(getTargetContext()),
                () -> ReplyReadInstrumentation.nativeReplyDoesNotReuseReadTarget(getTargetContext()),
                () -> ConversationRoundInstrumentation.replyCompletionPresentation(getTargetContext()),
                () -> ConversationRoundInstrumentation.replyCompletionPublicationGuard(getTargetContext()),
                this::classifiesRawFirstMessages, this::classificationSurvivesNextMessage, this::lateTalkerIsIsolated,
                this::legacyGroupGuessIsRepaired, this::nativeGroupEvidence,
                this::replyInputsUseLocalizedReplyLabel,
                () -> com.oasisfeng.nevo.sdk.NotificationArchiveInstrumentation.replyActionSurvivesRemovalAndRebuild(getTargetContext()),
                () -> Plan123Instrumentation.recallSequence(getTargetContext()),
                () -> Plan123Instrumentation.recallWithoutIdentity(getTargetContext()),
                () -> Plan123Instrumentation.recallSameTextAndTime(getTargetContext()),
                () -> Plan123Instrumentation.recallLegacySelfAndRoundMetadata(getTargetContext()),
                () -> Plan123Instrumentation.recallReusedNotificationId(getTargetContext()),
                () -> Plan123Instrumentation.emptyRecallStaysSuppressed(getTargetContext()),
                () -> Plan123Instrumentation.replyEligibility(getTargetContext()),
                () -> Plan123Instrumentation.nativeReplyForwarding(getTargetContext()),
                () -> Plan123Instrumentation.replyAlertsAfterIncoming(getTargetContext()),
                () -> Plan123Instrumentation.mediaReplyActionDeduplication(getTargetContext()),
                () -> Plan123Instrumentation.concurrentMediaReplySubmission(getTargetContext()),
                () -> Plan123Instrumentation.animatedStickerMediaBranch(getTargetContext()),
                () -> Plan123Instrumentation.animatedStickerReplyAfterUpdate(getTargetContext()),
                NotificationMarkdownInstrumentation::stylesSurviveParcelAndExistingSpans,
                () -> NotificationMarkdownInstrumentation.repeatedRebuildAndDisable(getTargetContext()),
                () -> NotificationMarkdownInstrumentation.identityAndRecallUseSource(getTargetContext()),
                () -> NotificationMarkdownInstrumentation.sourceSurvivesBuilderAndPreview(getTargetContext()),
                NotificationMarkdownInstrumentation::codeStylesSurviveParcel,
                () -> NotificationMarkdownInstrumentation.codeSourceAndIncomingSurviveRebuild(getTargetContext()),
                NotificationMarkdownInstrumentation::frameworkCodeCleanupRoundTrip,
                NotificationMarkdownInstrumentation::restorationRejectsUnownedOrChangedText,
                NotificationMarkdownInstrumentation::restorationCompletesPartialStyles,
                NotificationMarkdownInstrumentation::headingsKeepSystemSizeAndInlineStyles,
                () -> NotificationMarkdownInstrumentation.headingRebuildAndDisable(getTargetContext()),
                NotificationMarkdownInstrumentation::frameworkHeadingCleanupRoundTrip,
                () -> ImagePreviewLayoutInstrumentation.fitStartAndActions(this),
                () -> ImagePreviewLayoutInstrumentation.verifiedImageIdentitySurvivesPreviewRoundTrip(this),
                () -> ImagePreviewLayoutInstrumentation.pictureAndPlaceholderAreExclusive(this),
                () -> ImagePreviewLayoutInstrumentation.imageMessageIdentitySurvivesLaterText(this),
                () -> ImagePreviewLayoutInstrumentation.messagingImageUsesSystemContentInset(this),
                () -> ImagePreviewLayoutInstrumentation.offloadedPictureIconStillRenders(this),
                () -> ImagePreviewLayoutInstrumentation.discardClearsLayout(getTargetContext()),
                () -> ImagePreviewLayoutInstrumentation.modulePackageLookupUnavailable(getTargetContext()),
                () -> com.oasisfeng.nevo.xposed.RemotePreferenceStoreInstrumentation.imageWaitSync(getTargetContext()),
                () -> NotificationAppearanceInstrumentation.nativeAvatarAndGroupScope(getTargetContext()),
                () -> NotificationAppearanceInstrumentation.ownershipAndPreviewMetadata(getTargetContext()),
                () -> NotificationAppearanceInstrumentation.collapsedAppNameOnly(this),
                () -> NotificationAppearanceInstrumentation.privateTextReplacesNativeViewsFromFirstMessage(getTargetContext()),
                () -> PrivateCompactHeadsUpInstrumentation.renderAndReapply(this),
                () -> MediaConversationInstrumentation.mediaHistoryUpdatesImmediately(getTargetContext()),
                () -> MediaConversationInstrumentation.groupMediaKeepsSenders(getTargetContext()),
                () -> MediaConversationInstrumentation.mediaMarkersKeepOriginalText(getTargetContext()),
                () -> MediaConversationInstrumentation.mediaReplyActionsArePreserved(getTargetContext()),
                () -> MediaConversationInstrumentation.mediaHistorySurvivesRebuild(getTargetContext()),
                () -> MediaConversationInstrumentation.mediaAfterReplyUsesExistingRound(getTargetContext()),
                () -> com.oasisfeng.nevo.xposed.NotificationRemovalReadInstrumentation.clickUsesRemovedNativeRead(getTargetContext()),
                () -> com.oasisfeng.nevo.xposed.NotificationRemovalReadInstrumentation.removalReadGuards(getTargetContext()),
                () -> com.oasisfeng.nevo.xposed.NotificationRemovalReadInstrumentation.cancelledRemovalReadReturnsNormally(getTargetContext()),
                () -> com.oasisfeng.nevo.xposed.NotificationRemovalReadInstrumentation.removalFailuresStillResetHistory(getTargetContext()),
                () -> com.oasisfeng.nevo.xposed.NotificationRemovalReadInstrumentation.removalResetFailureDoesNotSuppressRead(getTargetContext()),
                () -> ImageRenderCapabilitiesInstrumentation.remoteViewsRemovalAndCache(this),
                () -> ImageRenderCapabilitiesInstrumentation.remoteViewsReapplyFlag(getTargetContext()),
                ImageRenderCapabilitiesInstrumentation::conversationMessageData,
                ImageRenderCapabilitiesInstrumentation::messagingMessageData,
                () -> InitialAvatarInstrumentation.cancellationAndReplacement(this),
                () -> InitialAvatarInstrumentation.recallAndAlert(getTargetContext()) };
        if (names.length != tests.length) {
            Bundle result = new Bundle();
            result.putString("shortMsg", "Framework test names and cases differ in length");
            finish(Activity.RESULT_CANCELED, result);
            return;
        }
        int failures = 0, skipped = 0;
        for (int i = 0; i < tests.length; i++) {
            Bundle status = new Bundle();
            status.putString("class", getClass().getName());
            status.putString("test", names[i]);
            status.putInt("numtests", tests.length);
            status.putInt("current", i + 1);
            sendStatus(1, status);
            try { tests[i].run(); sendStatus(0, status); }
            catch (Skipped unsupported) {
                skipped++;
                status.putString("skip", unsupported.getMessage());
                sendStatus(-3, status);
            }
            catch (Throwable error) {
                failures++;
                status.putString("stack", android.util.Log.getStackTraceString(error));
                sendStatus(-2, status);
            }
        }
        Bundle result = new Bundle();
        result.putString("stream", "Identity instrumentation: " + (tests.length - failures - skipped)
                + "/" + tests.length + " passed, " + skipped + " skipped\n");
        if (failures != 0) result.putString("shortMsg", failures + " identity tests failed");
        finish(failures == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void personAndAttachmentRoundTrip() {
        Person peer = new Person.Builder().setName("Alice").setKey("alice-key").setUri("peer:alice")
                .setBot(true).setImportant(true)
                .setIcon(IconCompat.createWithBitmap(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))).build();
        Message original = new Message("photo", 123, peer).setData("image/jpeg", android.net.Uri.parse("content://test/photo"));
        original.getExtras().putString("custom", "kept");
        Bundle bundle = parcel(NotificationMessages.toBundle(original));
        bundle.remove("sender");
        Message restored = NotificationMessages.read(bundle, conversation(false), false, false);
        check(restored != null && restored.getPerson() != null, "person-only message became self");
        check("alice-key".equals(restored.getPerson().getKey()), "key lost");
        check("peer:alice".equals(restored.getPerson().getUri()), "URI lost");
        check(restored.getPerson().isBot() && restored.getPerson().isImportant(), "person flags lost");
        check(restored.getPerson().getIcon() != null, "avatar lost");
        check(original.getDataUri().equals(restored.getDataUri()), "attachment lost");
        check("image/jpeg".equals(restored.getDataMimeType()), "MIME lost");
        check("kept".equals(restored.getExtras().getString("custom")), "extras lost");
    }

    private void staleTickerAndCarHistory() {
        Notification n = notification("明天见", 200);
        n.tickerText = "Alice: 你好";
        Bundle car = new Bundle();
        Bundle conversation = new Bundle();
        Bundle old = new Bundle(); old.putString("text", "你好");
        Bundle latest = new Bundle(); latest.putString("text", "明天见");
        conversation.putParcelableArray("messages", new Bundle[]{old, latest});
        conversation.putStringArray("participants", new String[]{"Alice"});
        conversation.putLong("timestamp", 200);
        car.putBundle("car_conversation", conversation);
        n.extras.putBundle("android.car.EXTENSIONS", car);
        check(new Notification.CarExtender(n).getUnreadConversation().getMessages().length == 2, "invalid car fixture");
        List<Message> messages = NotificationMessages.rebuild(conversation(false), n, Collections.emptyList());
        check(messages.size() == 1 && "明天见".contentEquals(messages.get(0).getText()), "stale ticker replaced current text");
        check(messages.get(0).getPerson() != null, "peer became self");
    }

    private void unknownAndGroupNames() {
        Conversation group = conversation(true);
        Message named = NotificationMessages.incoming(group, notification("群名: hello", 100));
        check(named.getPerson() != null && "群名".contentEquals(named.getPerson().getName()), "group name became self");
        Message unknown = NotificationMessages.incoming(group, notification("hello", 100));
        check(unknown.getPerson() != null && unknown.getPerson().getIcon() == null, "unknown uses self avatar");
    }



    private void repeatedRebuildAndPreview() {
        Conversation c = conversation(false);
        Notification n = normalized(c, notification("first", 100));
        Notification recovered = Notification.Builder.recoverBuilder(getTargetContext(), n).build();
        List<Message> first = NotificationMessages.rebuild(c, recovered, Collections.singletonList(n));
        check(first.size() == 1 && first.get(0).getPerson() != null, "recoverBuilder changed incoming identity");
        Notification preview = Notification.Builder.recoverBuilder(getTargetContext(), recovered)
                .setStyle(new Notification.BigPictureStyle().bigPicture(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))).build();
        NotificationMessages.copyIdentityExtras(recovered, preview);
        preview.extras = parcel(preview.extras);
        List<Message> second = NotificationMessages.rebuild(c, preview, Arrays.asList(n, recovered));
        check(second.size() == 1 && "first".contentEquals(second.get(0).getText()), "preview duplicated incoming history");
        check(second.get(0).getPerson() != null && second.get(0).getTimestamp() == 100, "preview changed incoming identity/time");
    }

    private void untrustedMissingSender() {
        Bundle b = new Bundle(); b.putCharSequence("text", "unknown");
        check(NotificationMessages.read(b, conversation(false), false, true) == null, "untrusted historical sender retained");
        check(NotificationMessages.read(b, conversation(false), false, false).getPerson() != null, "missing sender became self");
        b.putCharSequence("sender", "");
        check(NotificationMessages.read(b, conversation(false), false, false).getPerson() != null, "empty sender became self");
    }

    private void reusedNotificationId() {
        Conversation old = conversation(false);
        old.key = "old-key";
        Notification archived = normalized(old, notification("old text", 100));
        Conversation current = conversation(false); current.key = "new-key";
        List<Message> messages = NotificationMessages.rebuild(current, notification("new text", 200), Collections.singletonList(archived));
        check(messages.size() == 1, "same title mixed different talkers");
        ConversationManager manager = new ConversationManager();
        manager.getConversation(1).setType(Conversation.TYPE_GROUP_CHAT);
        manager.resetConversation(1);
        check(manager.getConversation(1).getType() == Conversation.TYPE_UNKNOWN, "reused ID kept group state");
    }

    private void rawPersonOnlyNotification() {
        Notification n = notification("hello", 100);
        Message m = new Message("hello", 100, new Person.Builder().setName("Alice").setKey("key").build());
        Bundle b = NotificationMessages.toBundle(m); b.remove("sender");
        n.extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Parcelable[]{b});
        Conversation c = conversation(false);
        List<Message> messages = NotificationMessages.rebuild(c, n, Collections.emptyList());
        check(messages.size() == 1 && c.notificationPeerId.equals(messages.get(0).getPerson().getKey()), "raw person-only message duplicated or lost canonical identity");
    }

    private void nativeMessagingSelfIgnored() {
        Notification n = notification("reply", 100);
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        n.extras.putCharSequence("android.selfDisplayName", "我");
        if (Build.VERSION.SDK_INT >= 28) n.extras.putParcelable(Notification.EXTRA_MESSAGING_PERSON, new android.app.Person.Builder().setName("我").build());
        Bundle b = new Bundle(); b.putCharSequence("text", "reply"); b.putLong("time", 100);
        n.extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Parcelable[]{b});
        List<Message> messages = NotificationMessages.rebuild(conversation(false), n, Collections.emptyList());
        check(messages.isEmpty(), "legacy native self body entered incoming transcript");
        Conversation c = conversation(false);
        n.extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Parcelable[]{b,
                NotificationMessages.toBundle(new Message("Incoming", 101, NotificationMessages.peer(c, null)))});
        messages = NotificationMessages.rebuild(c, n, Collections.emptyList());
        check(messages.size() == 1 && messages.get(0).getPerson() != null
                && "Incoming".contentEquals(messages.get(0).getText()), "native incoming identity was removed with legacy self");
    }

    private void rawColonText() {
        Message message = NotificationMessages.incoming(conversation(false), notification("https: example", 100));
        check("https: example".contentEquals(message.getText()), "body prefix removed");
    }

    private void undatedSnapshots() {
        Conversation c = conversation(false);
        Notification old = normalized(c, notification("undated", 0));
        Notification copy = Notification.Builder.recoverBuilder(getTargetContext(), old).build();
        check(NotificationMessages.rebuild(c, copy, Collections.singletonList(old)).size() == 1, "undated snapshot multiplied");
        List<Message> messages = NotificationMessages.rebuild(c, notification("current", 200), Arrays.asList(old, copy));
        check(messages.size() == 2 && "current".contentEquals(messages.get(1).getText()), "undated history covered current incoming");
    }

    private void rawMediaBetweenTextMessages() {
        Conversation c = conversation(false);
        Notification a = normalized(c, notification("A", 100));
        String[] markers = { "[动画表情]", "[表情]", "[图片]", "[视频]", "[文件]", "[链接]", "[音乐]",
                "[位置]", "[红包]", "[转账]", "[小程序]", "[语音]" };
        List<Notification> archive = new ArrayList<>();
        archive.add(a);
        for (int i = 0; i < markers.length; i++) {
            Notification media = archivedRaw(markers[i], 200 + i * 100);
            media.tickerText = "Alice: " + markers[i];
            archive.add(media);
        }
        Notification latest = notification("C", 200 + markers.length * 100);
        archive.add(latest);
        List<Message> messages = NotificationMessages.rebuild(c, latest, archive);
        check(messages.size() == markers.length + 2, "raw media left a gap in private history");
        check("A".contentEquals(messages.get(0).getText()), "first private text was lost");
        for (int i = 0; i < markers.length; i++)
            check(markers[i].contentEquals(messages.get(i + 1).getText()), "private media history gap at " + i);
        check("C".contentEquals(messages.get(messages.size() - 1).getText()), "last private text was lost");
    }

    private void rawMediaInGroupHistory() {
        Conversation c = conversation(true);
        Notification a = notification("Bob: A", 100);
        a.extras.putCharSequence(Notification.EXTRA_TITLE, "群名");
        a = normalized(c, a);
        Notification sticker = archivedRaw("Carol: [动画表情]", 200);
        sticker.extras.putCharSequence(Notification.EXTRA_TITLE, "群名");
        sticker.tickerText = "群名: Carol: [动画表情]";
        Notification latest = notification("Dave: C", 300);
        latest.extras.putCharSequence(Notification.EXTRA_TITLE, "群名");
        List<Message> messages = NotificationMessages.rebuild(c, latest, Arrays.asList(a, sticker, latest));
        check(messages.size() == 3, "raw media left a gap in group history");
        String[] expected = { "A", "[动画表情]", "C" };
        String[] senders = { "Bob", "Carol", "Dave" };
        for (int i = 0; i < expected.length; i++) {
            check(expected[i].contentEquals(messages.get(i).getText()), "group history gap at " + i);
            check(senders[i].contentEquals(messages.get(i).getPerson().getName()), "group sender changed at " + i);
        }
    }

    private void rawMediaRespectsReadBoundary() {
        Conversation c = conversation(false);
        Notification a = notification("A", 100);
        a.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 1);
        a = normalized(c, a);
        Notification sticker = notification("[动画表情]", 200);
        sticker.tickerText = "Alice: [动画表情]";
        sticker.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 2);
        Notification latest = notification("C", 300);
        latest.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 3);
        latest.extras.putLong(WeChatNotificationRemoval.READ_THROUGH_TOKEN, 2);
        List<Message> messages = NotificationMessages.rebuild(c, latest, Arrays.asList(a, sticker, latest));
        check(messages.size() == 1 && "C".contentEquals(messages.get(0).getText()),
                "read boundary restored earlier raw media");
    }

    private void rawMediaSubmissionEndsHistory() {
        Conversation c = conversation(false);
        Notification a = notification("A", 100);
        a.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 1);
        a = normalized(c, a);
        Notification sticker = notification("[动画表情]", 200);
        sticker.tickerText = "Alice: [动画表情]";
        sticker.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 2);
        sticker = normalized(c, sticker, Collections.singletonList(a));
        checkTexts(c, sticker, "A", "[动画表情]");
        NotificationMessages.clearReadHistory(c, sticker, 2);
        checkTexts(c, sticker);
        Notification next = notification("C", 0);
        next.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, 3);
        checkTexts(c, normalized(c, next, Arrays.asList(a, sticker)), "C");
    }

    private void rawMediaAndNativeHistoryDoNotDuplicate() {
        Conversation c = conversation(false);
        Notification a = normalized(c, notification("A", 100));
        Notification sticker = archivedRaw("[动画表情]", 200);
        sticker.tickerText = "Alice: [动画表情]";
        Notification latest = notification("C", 300);
        latest.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        latest.extras.putCharSequence(androidx.core.app.NotificationCompat.EXTRA_SELF_DISPLAY_NAME, "我");
        Person peer = NotificationMessages.peer(c, null);
        latest.extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Bundle[]{
                NotificationMessages.toBundle(new Message("[动画表情]", 200, peer)),
                NotificationMessages.toBundle(new Message("C", 300, peer)) });
        List<Message> messages = NotificationMessages.rebuild(c, latest, Arrays.asList(a, sticker, latest));
        check(messages.size() == 3 && "A".contentEquals(messages.get(0).getText())
                && "[动画表情]".contentEquals(messages.get(1).getText())
                && "C".contentEquals(messages.get(2).getText()), "native history duplicated raw media");
    }

    private void rawMediaInBotHistory() {
        Conversation c = new Conversation(1);
        c.title = "服务号";
        c.setType(Conversation.TYPE_BOT_MESSAGE);
        Notification a = notification("A", 100);
        a.extras.putCharSequence(Notification.EXTRA_TITLE, "服务号");
        a = normalized(c, a);
        Notification media = archivedRaw("[文件]", 200);
        media.extras.putCharSequence(Notification.EXTRA_TITLE, "服务号");
        media.tickerText = "服务号: [文件]";
        Notification latest = notification("C", 300);
        latest.extras.putCharSequence(Notification.EXTRA_TITLE, "服务号");
        List<Message> messages = NotificationMessages.rebuild(c, latest, Arrays.asList(a, media, latest));
        check(messages.size() == 3 && "A".contentEquals(messages.get(0).getText())
                && "[文件]".contentEquals(messages.get(1).getText())
                && "C".contentEquals(messages.get(2).getText()), "bot history skipped raw media");
    }

    /** Local.apply assigns the generation before caching even an unnormalized notification. */
    private Notification archivedRaw(String text, long time) {
        Notification n = notification(text, time);
        n.extras.putLong(WeChatNotificationRemoval.NOTIFICATION_ROUND_TOKEN, WeChatNotificationRemoval.nextToken());
        return n;
    }

    private Notification notification(String text, long time) {
        return new Notification.Builder(getTargetContext(), "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Alice").setContentText(text).setWhen(time).build();
    }

    private Notification identifiedNotification(String text, long time, long serverId) {
        Notification n = notification(text, time);
        n.tickerText = "Alice: " + text;
        n.extras.putLong(NotificationMessages.SERVER_ID, serverId);
        return n;
    }

    private void stableMessageIdRepublish() {
        for (long time : new long[] { 305, 100, 0 }) {
            Conversation c = conversation(false);
            Notification old = normalized(c, identifiedNotification("[语音]", 100, 7));
            old.extras = parcel(old.extras);
            // Resolving the talker after the first notification must keep the canonical sender.
            c.key = "resolved-peer";
            Notification latest = normalized(c, identifiedNotification("[语音]", time, 7), Collections.singletonList(old));
            Notification recovered = Notification.Builder.recoverBuilder(getTargetContext(), latest).build();
            NotificationMessages.copyIdentityExtras(latest, recovered);
            recovered.extras = parcel(recovered.extras);
            List<Message> messages = NotificationMessages.rebuild(c, recovered, Collections.singletonList(latest));
            check(messages.size() == 1, "republished stable voice ID duplicated history");
            Message voice = messages.get(0);
            check(voice.getTimestamp() == 100 && voice.getExtras().getLong(NotificationMessages.SERVER_ID) == 7,
                    "republish replaced the original message time or lost the server ID");
            check(voice.getPerson() != null && "[语音]".contentEquals(voice.getText()), "voice identity or text changed");
        }
    }

    private void stableMessageIdRawHistory() {
        Conversation c = conversation(false);
        Notification first = identifiedNotification("[语音]", 100, 7);
        Notification duplicate = identifiedNotification("[语音]", 305, 7);
        Notification latest = identifiedNotification("[语音]", 400, 8);
        List<Message> messages = NotificationMessages.rebuild(c, latest, Arrays.asList(first, duplicate, latest));
        check(messages.size() == 2, "raw stable-ID copies were not collapsed within one archive source");
        check(messages.get(0).getTimestamp() == 100 && messages.get(1).getTimestamp() == 400,
                "raw voice deduplication lost the first occurrence or a distinct voice");
    }

    private void distinctVoiceMessages() {
        long[][] cases = { { 7, 8, 100 }, { 0, 0, 305 }, { 7, 0, 100 }, { 0, 7, 100 }, { -1, -1, 305 } };
        for (long[] values : cases) {
            Conversation c = conversation(false);
            Notification old = normalized(c, identifiedNotification("[语音]", 100, values[0]));
            Notification latest = identifiedNotification("[语音]", values[2], values[1]);
            List<Message> messages = NotificationMessages.rebuild(c, latest, Collections.singletonList(old));
            check(messages.size() == 2, "consecutive or unidentified voice messages were merged");
        }
    }

    private void stableMessageIdBundleHistory() {
        for (long currentId : new long[] { 7, 8, 0 }) {
            Conversation c = conversation(false);
            Message previous = new Message("[语音]", 100, NotificationMessages.peer(c, null));
            previous.getExtras().putLong(NotificationMessages.SERVER_ID, 7);
            Notification latest = identifiedNotification("[语音]", 100, currentId);
            latest.extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Bundle[] { NotificationMessages.toBundle(previous) });
            List<Message> messages = NotificationMessages.rebuild(c, latest, Collections.emptyList());
            check(messages.size() == (currentId == 7 ? 1 : 2),
                    "raw bundle history matched distinct or missing IDs by voice text/time");
        }
    }

    private void stableMessageIdSenderIsolation() {
        Conversation c = conversation(true);
        Notification first = identifiedNotification("Bob: [语音]", 100, 7);
        first.extras.putCharSequence(Notification.EXTRA_TITLE, c.title);
        Notification old = normalized(c, first);
        Notification latest = identifiedNotification("Carol: [语音]", 100, 7);
        latest.extras.putCharSequence(Notification.EXTRA_TITLE, c.title);
        List<Message> messages = NotificationMessages.rebuild(c, latest, Collections.singletonList(old));
        check(messages.size() == 2 && "Bob".contentEquals(messages.get(0).getPerson().getName())
                && "Carol".contentEquals(messages.get(1).getPerson().getName()), "stable ID merged different group senders");
    }

    private void stableMessageIdConversationIsolation() {
        Conversation first = conversation(false);
        first.key = "peer-a";
        Notification old = normalized(first, identifiedNotification("[语音]", 100, 7));
        Conversation second = conversation(false);
        second.key = "peer-b";
        Notification latest = identifiedNotification("[语音]", 305, 7);
        List<Message> messages = NotificationMessages.rebuild(second, latest, Collections.singletonList(old));
        check(messages.size() == 1 && messages.get(0).getTimestamp() == 305,
                "stable ID reused another conversation's message");
    }



    private void stableMessageIdPreservesAttachment() {
        Conversation c = conversation(false);
        Notification old = normalized(c, identifiedNotification("[图片]", 100, 7));
        Bundle image = (Bundle) old.extras.getParcelableArray(Notification.EXTRA_MESSAGES)[0];
        android.net.Uri uri = android.net.Uri.parse("content://test/stable-image");
        image.putString("type", "image/jpeg");
        image.putParcelable("uri", uri);
        old.extras = parcel(old.extras);
        Notification latest = normalized(c, identifiedNotification("[图片]", 305, 7), Collections.singletonList(old));
        latest.extras = parcel(latest.extras);
        List<Message> messages = NotificationMessages.rebuild(c, latest, Collections.emptyList());
        check(messages.size() == 1 && messages.get(0).getTimestamp() == 100
                && "image/jpeg".equals(messages.get(0).getDataMimeType()) && uri.equals(messages.get(0).getDataUri()),
                "stable-ID repost lost the original media attachment");
    }

    private Notification normalized(Conversation c, Notification n) {
        return normalized(c, n, Collections.emptyList());
    }
    private Notification normalized(Conversation c, Notification n, List<Notification> archive) {
        MessagingStyle style = new MessagingStyle(new Person.Builder().setName("我").build());
        for (Message m : NotificationMessages.rebuild(c, n, archive)) style.addMessage(m);
        NotificationMessages.stamp(c, n);
        NotificationMessages.flatIntoExtras(style, n.extras);
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        return n;
    }
    private void stablePeerAcrossTalkerAndAvatar() {
        Conversation c = conversation(false);
        Notification old = normalized(c, notification("A", 100));
        String stable = c.notificationPeerId;
        c.key = "resolved-talker";
        c.icon = IconCompat.createWithBitmap(Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888));
        Notification latest = normalized(c, notification("C", 200), Collections.singletonList(old));
        List<Message> messages = NotificationMessages.rebuild(c, latest, Collections.emptyList());
        check(messages.size() == 2, "peer history missing");
        for (Message m : messages) {
            check(stable.equals(m.getPerson().getKey()), "talker changed sender identity");
            check(m.getPerson().getIcon() != null, "updated avatar not shared by old messages");
        }
        Conversation restored = conversation(false);
        NotificationMessages.rebuild(restored, latest, Collections.emptyList());
        check(stable.equals(restored.notificationPeerId), "snapshot lost stable sender ID");
        Conversation fresh = conversation(false);
        check(!stable.equals(NotificationMessages.peer(fresh, null).getKey()), "replacement conversation reused identity");
    }

    private void dismissUsesNativeRead() {
        Context context = getTargetContext();
        java.util.concurrent.CountDownLatch read = new java.util.concurrent.CountDownLatch(2);
        android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) { read.countDown(); }
        };
        String action = "nevo.test.ORIGINAL_READ";
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, new android.content.IntentFilter(action),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        PendingIntent original = PendingIntent.getBroadcast(context, 731,
                new Intent(action).setPackage(context.getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        WeChatDecorator.Local local = new WeChatDecorator.Local("WeChatDecorator");
        try {
            local.onCreate(context.getSharedPreferences("identity-test", Context.MODE_PRIVATE));
            android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
            String[] texts = { "A", "[动画表情]" };
            Notification[] notifications = new Notification[texts.length];
            for (int i = 0; i < texts.length; i++) {
                Notification.CarExtender.UnreadConversation unread = new Notification.CarExtender.Builder("Alice")
                        .addMessage(texts[i]).setLatestTimestamp(100).setReadPendingIntent(original).build();
                Notification n = new Notification.Builder(context, "identity-test").setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Alice").setContentText(texts[i]).setTicker("Alice: " + texts[i]).setWhen(100)
                        .extend(new Notification.CarExtender().setUnreadConversation(unread)).build();
                local.apply(manager, null, 731 + i, n);
                check(original.equals(n.deleteIntent), "delete action does not target native read directly");
                Notification rebuilt = Notification.Builder.recoverBuilder(context, n).build();
                NotificationMessages.copyIdentityExtras(n, rebuilt);
                local.apply(manager, null, 731 + i, rebuilt);
                check(original.equals(rebuilt.deleteIntent), "rebuild replaced native read with a proxy");
                notifications[i] = rebuilt;
            }
            local.onDestroy();
            for (Notification n : notifications) n.deleteIntent.send();
            check(read.await(5, java.util.concurrent.TimeUnit.SECONDS), "native read depends on the decorator receiver");
        } catch (Exception failure) { throw new AssertionError(failure); }
        finally {
            local.onDestroy(); context.unregisterReceiver(receiver); original.cancel();
        }
    }

    private void checkTexts(Conversation c, Notification n, String... expected) {
        List<Message> messages = NotificationMessages.rebuild(c, n, Collections.emptyList());
        check(messages.size() == expected.length, "unexpected round length: " + messages.size());
        for (int i = 0; i < expected.length; i++) check(expected[i].contentEquals(messages.get(i).getText()), "wrong round content at " + i);
    }
    private static Conversation conversation(boolean group) {
        Conversation c = new Conversation(1); c.title = group ? "群名" : "Alice";
        c.setType(group ? Conversation.TYPE_GROUP_CHAT : Conversation.TYPE_DIRECT_MESSAGE);
        return c;
    }

    private Conversation classified(ConversationManager manager, Notification n) {
        Conversation cached = manager.getConversation(1);
        cached.title = "Alice";
        return manager.snapshot(cached, n);
    }

    private void classifiesRawFirstMessages() {
        for (String body : new String[]{"你好", "a", "[2条]你好", "提示: hello"}) {
            for (boolean withTicker : new boolean[]{true, false}) {
                Notification n = notification(body, 100);
                n.tickerText = withTicker ? body : null;
                Conversation c = classified(new ConversationManager(), n);
                check(!c.isGroupChat(), "body/ticker promoted cold-start notification to group");
                List<Message> messages = NotificationMessages.rebuild(c, n, Collections.emptyList());
                check(messages.size() == 1 && "Alice".contentEquals(messages.get(0).getPerson().getName()), "cold-start peer became unknown");
            }
        }
    }

    private void classificationSurvivesNextMessage() {
        ConversationManager manager = new ConversationManager();
        Notification first = notification("hello", 100); first.tickerText = "Alice: hello";
        Conversation initial = classified(manager, first);
        first = normalized(initial, first);
        Notification second = notification("你好", 200); second.tickerText = "你好";
        Conversation next = classified(manager, second);
        List<Message> messages = NotificationMessages.rebuild(next, second, Collections.singletonList(first));
        check(messages.size() == 2 && !next.isGroupChat(), "second message changed to group");
        for (Message message : messages) {
            check("Alice".contentEquals(message.getPerson().getName()), "peer became unknown");
            check(initial.notificationPeerId.equals(message.getPerson().getKey()), "stable peer ID lost");
        }
        manager.acceptTalker(manager.getConversation(1), "alice");
        Notification contradictory = notification("Bob: hello", 300);
        contradictory.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        contradictory.extras.putBoolean("android.isGroupConversation", true);
        check(classified(manager, contradictory).getType() == Conversation.TYPE_DIRECT_MESSAGE, "weaker group evidence replaced verified private talker");
    }

    private void lateTalkerIsIsolated() {
        ConversationManager manager = new ConversationManager();
        Notification n = notification("hello", 100);
        Conversation view = classified(manager, n);
        view.onTalkerResolved.accept("room@chatroom");
        check(view.getType() == Conversation.TYPE_UNKNOWN && view.key == null, "callback changed in-flight view");
        check(!n.extras.containsKey(NotificationMessages.KEY), "callback mutated notification extras");
        check(classified(manager, notification("next", 200)).isGroupChat(), "next build did not consume resolved key");
        manager.resetConversation(1);
        view.onTalkerResolved.accept("room@chatroom");
        check(classified(manager, notification("new contact", 300)).getType() == Conversation.TYPE_UNKNOWN, "stale callback contaminated reused ID");
        Conversation current = manager.getConversation(1);
        check(manager.acceptTalker(current, "alice"), "valid callback rejected");
        check(!manager.acceptTalker(current, "bob"), "conflicting callback overwrote identity");
        ConversationManager pending = new ConversationManager();
        Conversation stale = classified(pending, notification("old request", 100));
        Conversation fresh = classified(pending, notification("new request", 200));
        stale.onTalkerResolved.accept("old@chatroom");
        check(pending.getConversation(1).knownKey() == null, "older generation callback accepted");
        fresh.onTalkerResolved.accept("alice");
        check(classified(pending, notification("new", 300)).getType() == Conversation.TYPE_DIRECT_MESSAGE, "latest callback rejected");
    }

    private void legacyGroupGuessIsRepaired() {
        Notification old = notification("hello", 100);
        old.extras.putBoolean(NotificationMessages.STORED, true);
        old.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        old.extras.putBoolean("android.isGroupConversation", true);
        old.extras.putCharSequence("android.conversationTitle", "Alice");
        Message placeholder = new Message("hello", 100, new Person.Builder().setName("未知发送者").build());
        old.extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Bundle[]{NotificationMessages.toBundle(placeholder)});
        ConversationManager manager = new ConversationManager();
        Conversation c = classified(manager, old);
        check(!c.isGroupChat(), "module-generated legacy group flag treated as native proof");
        MessagingStyle style = new MessagingStyle(new Person.Builder().setName("我").build());
        for (Message message : NotificationMessages.rebuild(c, old, Collections.emptyList())) style.addMessage(message);
        style.setGroupConversation(c.isGroupChat()).setConversationTitle(null);
        NotificationMessages.flatIntoExtras(style, old.extras);
        check("Alice".contentEquals(style.getMessages().get(0).getPerson().getName()), "old unknown placeholder not repaired");
        check(!old.extras.getBoolean("android.isGroupConversation") && !old.extras.containsKey("android.conversationTitle"), "stale group layout survived");
    }

    private void nativeGroupEvidence() {
        Notification n = notification("hello", 100);
        n.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        n.extras.putBoolean("android.isGroupConversation", true);
        ConversationManager manager = new ConversationManager();
        Conversation c = classified(manager, n);
        check(c.isGroupChat(), "explicit native group ignored");
        check("未知发送者".contentEquals(NotificationMessages.incoming(c, n).getPerson().getName()), "real unknown group member incorrectly renamed");
        NotificationMessages.stamp(c, n);
        n.extras.putBoolean(NotificationMessages.STORED, true);
        Conversation restored = classified(new ConversationManager(), n);
        check(restored.isGroupChat(), "verified evidence lost on snapshot recovery");
        Notification noFlag = notification("hello", 200);
        noFlag.extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification$MessagingStyle");
        check(classified(manager, noFlag).isGroupChat(), "missing flag treated as explicit direct");
    }

    private void replyInputsUseLocalizedReplyLabel() {
        Context context = getTargetContext();
        int replyResource = context.getResources().getIdentifier("action_reply", "string", context.getPackageName());
        check(replyResource != 0, "action_reply resource not found");
        String expected = context.getString(replyResource);
        MessagingBuilder builder = new MessagingBuilder(context, new MessagingBuilder.Controller() {});
        try {
            PendingIntent reply = PendingIntent.getBroadcast(context, 501,
                    new Intent("com.oasisfeng.nevo.test.REPLY").setPackage(context.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            RemoteInput original = new RemoteInput.Builder("reply")
                    .setAllowFreeFormInput(true).setLabel("Alice").build();
            Notification.CarExtender.UnreadConversation carConversation =
                    new Notification.CarExtender.Builder("Alice")
                            .addMessage("hello").setLatestTimestamp(100).setReplyAction(reply, original).build();

            Notification fromCar = carNotification(carConversation);
            Conversation carPeer = conversation(false); carPeer.key = "alice";
            check(builder.buildConversation(carPeer, 1, fromCar, Collections.emptyList()) != null,
                    "car reply action was not built");
            checkReplyLabel(fromCar, expected);

            Notification fromActions = notification("hello", 100);
            fromActions.tickerText = "Alice: hello";
            fromActions.actions = new Notification.Action[]{ new Notification.Action.Builder(null, "Reply", reply)
                    .addRemoteInput(original).build() };
            Conversation actionPeer = conversation(false); actionPeer.key = "alice";
            check(builder.buildConversation(actionPeer, 2, fromActions, Collections.emptyList()) != null,
                    "actions reply was not built");
            checkReplyLabel(fromActions, expected);

            Notification.CarExtender.UnreadConversation fallbackConversation =
                    new Notification.CarExtender.Builder("Alice")
                            .addMessage("hello").setLatestTimestamp(100).setReplyAction(reply, null).build();
            Notification fromFallback = carNotification(fallbackConversation);
            Conversation fallbackPeer = conversation(false); fallbackPeer.key = "alice";
            check(builder.buildConversation(fallbackPeer, 3, fromFallback, Collections.emptyList()) != null,
                    "fallback reply action was not built");
            checkReplyLabel(fromFallback, expected);

            Notification media = carNotification(carConversation);
            check(builder.buildConversation(carPeer, 4, media, Collections.emptyList()) != null,
                    "media reply action was not built");
            checkReplyLabel(media, expected);
        } finally {
            builder.close();
        }
    }

    private Notification carNotification(Notification.CarExtender.UnreadConversation conversation) {
        return new Notification.Builder(getTargetContext(), "identity-test")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Alice").setContentText("hello")
                .setTicker("Alice: hello")
                .extend(new Notification.CarExtender().setUnreadConversation(conversation)).build();
    }

    private static void checkReplyLabel(Notification notification, String expected) {
        boolean found = false;
        if (notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                RemoteInput[] inputs = action.getRemoteInputs();
                if (inputs == null) continue;
                for (RemoteInput input : inputs) {
                    if (!input.getAllowFreeFormInput()) continue;
                    found = true;
                    check(input.getLabel() != null && expected.contentEquals(input.getLabel()),
                            "reply label was " + input.getLabel() + ", expected " + expected);
                }
            }
        }
        check(found, "no free-form reply input found");
    }

    private static Bundle parcel(Bundle bundle) {
        Parcel p = Parcel.obtain();
        try { p.writeBundle(bundle); p.setDataPosition(0); return p.readBundle(MessageIdentityInstrumentation.class.getClassLoader()); }
        finally { p.recycle(); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
