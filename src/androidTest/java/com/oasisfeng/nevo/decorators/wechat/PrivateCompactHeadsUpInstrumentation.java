package com.oasisfeng.nevo.decorators.wechat;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Synthetic compact heads-up rendering: never posts a notification or starts another app. */
@androidx.annotation.RequiresApi(28)
final class PrivateCompactHeadsUpInstrumentation {
    private static final String TAG = "PrivateCompactHeadsUpTest";
    private static final String PEER = "Synthetic peer", BODY = "Synthetic message";
    private static final String[] SUPPORTED_LAYOUTS = {
            "notification_template_material_messaging_compact_heads_up",
            "notification_template_material_compact_heads_up_base"
    };
    private static final Set<String> REPORTED_UNAVAILABLE_SCOPES = new HashSet<>();

    static void renderAndReapply(Instrumentation instrumentation) {
        Context context = instrumentation.getTargetContext();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                Notification incoming = notice(context, false, false);

                // Exercise the framework's real Builder result. Newer framework templates are
                // intentionally unsupported and must be returned unchanged by the production hook.
                checkBuilderContract(context, incoming, BODY);

                // Keep the detailed rewrite checks tied to both templates that production supports,
                // even when this Android release's Builder emits a different layout.
                for (String layout : SUPPORTED_LAYOUTS)
                    checkTemplateCorrectionAndReapply(context, layout, incoming, BODY);

                Notification rebuilt = Notification.Builder.recoverBuilder(context, roundTrip(incoming)).build();
                checkFixed(context, rebuilt, BODY);
                Notification preview = ImagePreviewLoader.buildPreviewNotification(context, context.getApplicationInfo(), rebuilt,
                        Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888), 791);
                checkFixed(context, preview, BODY);

                Notification replyOnly = notice(context, false, true);
                checkFixed(context, replyOnly, "Synthetic reply");
                androidx.core.app.NotificationCompat.MessagingStyle restored =
                        androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(replyOnly);
                check(restored != null && restored.getMessages().get(0).getPerson() == null,
                        "self reply acquired a fake peer");

                Notification updated = notice(context, false, false);
                Notification.MessagingStyle updatedStyle = new Notification.MessagingStyle(
                        new Person.Builder().setName("Synthetic self").build()).setGroupConversation(false)
                        .addMessage(BODY, 100, new Person.Builder().setName(PEER).build())
                        .addMessage("Second message", 200, new Person.Builder().setName(PEER).build())
                        .addMessage("Synthetic reply", 300, (Person) null);
                updated = Notification.Builder.recoverBuilder(context, updated).setStyle(updatedStyle).build();
                checkTemplatesDisplay(context, updated, "Second message");
                androidx.core.app.NotificationCompat.MessagingStyle updatedTranscript =
                        androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(updated);
                check(updatedTranscript != null && updatedTranscript.getMessages().size() == 3
                                && BODY.contentEquals(updatedTranscript.getMessages().get(0).getText())
                                && "Second message".contentEquals(updatedTranscript.getMessages().get(1).getText())
                                && "Synthetic reply".contentEquals(updatedTranscript.getMessages().get(2).getText()),
                        "consecutive messages or self reply changed in transcript");

                Notification group = notice(context, true, false);
                checkKnownTemplateFallback(context, group, "group compact template was changed");
                Notification bot = notice(context, false, false);
                bot.extras.putInt(ConversationClassification.TYPE, ConversationTypePolicy.BOT);
                checkKnownTemplateFallback(context, bot, "bot compact template was changed");
                Notification unnormalized = notice(context, false, false);
                unnormalized.extras.remove(NotificationMessages.STORED);
                checkKnownTemplateFallback(context, unnormalized, "unnormalized notification was changed");

                PendingIntent syntheticFullScreen = PendingIntent.getActivity(context, 791,
                        new Intent().setClassName(context.getPackageName(), context.getPackageName() + ".SyntheticActivity"),
                        PendingIntent.FLAG_IMMUTABLE);
                try {
                    Notification fullScreen = roundTrip(incoming);
                    fullScreen.fullScreenIntent = syntheticFullScreen;
                    checkKnownTemplateFallback(context, fullScreen, "full-screen notification was changed");
                } finally { syntheticFullScreen.cancel(); }

                RemoteViews unsupported = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
                check(WeChatNotificationAppearance.privateCompactHeadsUp(unsupported, context.getResources(), incoming) == unsupported,
                        "unknown compact layout did not fall back");
                checkFixed(context, noticeWithBody(context, PEER + " is part of the message"),
                        PEER + " is part of the message");
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Private compact heads-up rendering failed", failure.get());
    }

    private static void checkBuilderContract(Context context, Notification notification, String body)
            throws ReflectiveOperationException {
        RemoteViews original = compact(context, notification);
        String supportedLayout = supportedLayoutName(context, original.getLayoutId());
        if (supportedLayout == null) {
            RemoteViews result = WeChatNotificationAppearance.privateCompactHeadsUp(
                    original, context.getResources(), notification);
            check(result == original, "unknown Builder template did not return the original RemoteViews");
            result.apply(context, new FrameLayout(context));
            return;
        }
        checkTemplateCorrection(context, supportedLayout, original, notification, body);
    }

    private static void checkTemplateCorrectionAndReapply(Context context, String layoutName,
                                                           Notification notification, String body) {
        RemoteViews original = frameworkTemplate(context, layoutName);
        if (original == null) return;
        checkTemplateCorrection(context, layoutName, original, notification, body);
    }

    private static void checkTemplateCorrection(Context context, String layoutName, RemoteViews original,
                                               Notification notification, String body) {
        if (!hasRewriteResources(context, layoutName)) {
            check(WeChatNotificationAppearance.privateCompactHeadsUp(original, context.getResources(), notification) == original,
                    layoutName + " did not fall back when required framework resources were absent");
            return;
        }

        int secondaryId = id(context, "header_text_secondary");
        int headerId = optionalId(context, "header_text");
        View before = original.apply(context, new FrameLayout(context));
        boolean hasHeaderView = headerId != 0 && before.findViewById(headerId) != null;
        if (headerId != 0 && !hasHeaderView)
            reportUnavailable("header-view:" + layoutName, layoutName
                    + " does not expose header_text; only the optional duplicate-header visibility assertion is unavailable");

        // Reproduce a contaminated final view even when this framework renders the fields correctly.
        original.setTextViewText(secondaryId, PEER + " " + BODY);
        if (hasHeaderView) {
            original.setTextViewText(headerId, PEER);
            original.setViewVisibility(headerId, View.VISIBLE);
        }
        RemoteViews fixed = WeChatNotificationAppearance.privateCompactHeadsUp(
                original, context.getResources(), notification);
        check(fixed != original, layoutName + " private compact template was not handled");

        View originalRoot = original.apply(context, new FrameLayout(context));
        check((PEER + " " + BODY).contentEquals(text(originalRoot, context, "header_text_secondary")),
                layoutName + " rewrite mutated the original RemoteViews");
        if (hasHeaderView)
            check(originalRoot.findViewById(headerId).getVisibility() == View.VISIBLE,
                    layoutName + " rewrite mutated the original header visibility");

        View root = fixed.apply(context, new FrameLayout(context));
        checkText(root, context, body);
        checkHeaderHidden(root, headerId, hasHeaderView, layoutName);

        // Simulate a stale sender being restored in an existing tree and verify reapply corrects it.
        ((TextView) root.findViewById(secondaryId)).setText(PEER + " " + BODY);
        if (hasHeaderView) {
            ((TextView) root.findViewById(headerId)).setText(PEER);
            root.findViewById(headerId).setVisibility(View.VISIBLE);
        }
        fixed.reapply(context, root);
        checkText(root, context, body);
        checkHeaderHidden(root, headerId, hasHeaderView, layoutName);
    }

    private static void checkFixed(Context context, Notification notification, String body)
            throws ReflectiveOperationException {
        checkTemplatesDisplay(context, notification, body);
        androidx.core.app.NotificationCompat.MessagingStyle style =
                androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
        check(style != null && body.contentEquals(style.getMessages().get(0).getText()), "transcript was edited");
    }

    private static void checkTemplatesDisplay(Context context, Notification notification, String body)
            throws ReflectiveOperationException {
        checkBuilderContract(context, notification, body);
        for (String layoutName : SUPPORTED_LAYOUTS) {
            RemoteViews original = frameworkTemplate(context, layoutName);
            if (original == null) continue;
            if (!hasRewriteResources(context, layoutName)) {
                check(WeChatNotificationAppearance.privateCompactHeadsUp(original, context.getResources(), notification) == original,
                        layoutName + " did not fall back when required framework resources were absent");
                continue;
            }
            RemoteViews fixed = WeChatNotificationAppearance.privateCompactHeadsUp(
                    original, context.getResources(), notification);
            check(fixed != original, layoutName + " rebuilt private compact template was not handled");
            checkText(fixed.apply(context, new FrameLayout(context)), context, body);
        }
    }

    private static void checkKnownTemplateFallback(Context context, Notification notification, String reason) {
        for (String layoutName : SUPPORTED_LAYOUTS) {
            RemoteViews original = frameworkTemplate(context, layoutName);
            if (original == null) continue;
            if (!hasRewriteResources(context, layoutName)) {
                reportUnavailable("guard:" + layoutName, reason + " was not independently exercised for " + layoutName
                        + " because a required framework resource is absent");
                continue;
            }
            check(WeChatNotificationAppearance.privateCompactHeadsUp(
                    original, context.getResources(), notification) == original, reason + " (" + layoutName + ")");
        }
    }

    private static void checkText(View root, Context context, String body) {
        String title = text(root, context, "title").toString().replaceAll("[\\u200e\\u200f\\u202a-\\u202e]", "");
        check(title.startsWith(PEER) && title.indexOf(PEER, PEER.length()) < 0, "peer repeated in title");
        check(body.contentEquals(text(root, context, "header_text_secondary")),
                "compact body contains an added sender or lost the original message");
    }

    private static void checkHeaderHidden(View root, int headerId, boolean hasHeaderView, String layoutName) {
        if (!hasHeaderView) return;
        View header = root.findViewById(headerId);
        check(header != null && header.getVisibility() == View.GONE,
                layoutName + " duplicate header remained visible");
    }

    private static CharSequence text(View root, Context context, String name) {
        View view = root.findViewById(id(context, name));
        check(view instanceof TextView, "framework compact view is not a TextView: " + name);
        return ((TextView) view).getText();
    }

    private static int id(Context context, String name) {
        int id = optionalId(context, name);
        check(id != 0, "missing framework compact view ID: " + name);
        return id;
    }

    private static int optionalId(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", "android");
    }

    private static boolean hasRewriteResources(Context context, String layoutName) {
        List<String> missing = new ArrayList<>();
        if (optionalId(context, "title") == 0) missing.add("id/title");
        if (optionalId(context, "header_text_secondary") == 0) missing.add("id/header_text_secondary");
        if (context.getResources().getIdentifier("notification_messaging_title_template", "string", "android") == 0)
            missing.add("string/notification_messaging_title_template");
        if (missing.isEmpty()) return true;
        reportUnavailable("rewrite-resources:" + layoutName + ":" + missing,
                layoutName + " rewrite scope is unavailable because required framework resources are absent: " + missing);
        return false;
    }

    private static RemoteViews frameworkTemplate(Context context, String layoutName) {
        int layoutId = frameworkLayoutId(context, layoutName);
        if (layoutId == 0) {
            reportUnavailable("layout:" + layoutName,
                    layoutName + " rewrite scope is unavailable because this API has no such framework layout");
            return null;
        }
        return new RemoteViews("android", layoutId);
    }

    private static String supportedLayoutName(Context context, int layoutId) {
        if (layoutId == 0) return null;
        for (String name : SUPPORTED_LAYOUTS)
            if (frameworkLayoutId(context, name) == layoutId) return name;
        return null;
    }

    private static int frameworkLayoutId(Context context, String name) {
        return context.getResources().getIdentifier(name, "layout", "android");
    }

    private static void reportUnavailable(String key, String message) {
        if (REPORTED_UNAVAILABLE_SCOPES.add(key)) android.util.Log.i(TAG, "scope unavailable: " + message);
    }

    private static RemoteViews compact(Context context, Notification notification) throws ReflectiveOperationException {
        NotificationAppearanceHookProfile.Header profile = NotificationAppearanceHookProfile.compactHeadsUp(
                Notification.Builder.class, Context.class, Notification.class, RemoteViews.class);
        return (RemoteViews) profile.render.invoke(Notification.Builder.recoverBuilder(context, notification));
    }

    private static Notification notice(Context context, boolean group, boolean replyOnly) {
        Notification.MessagingStyle style = new Notification.MessagingStyle(new Person.Builder().setName("Synthetic self").build())
                .setGroupConversation(group).setConversationTitle(group ? "Synthetic group" : replyOnly ? PEER : null)
                .addMessage(replyOnly ? "Synthetic reply" : BODY, 100,
                        replyOnly ? null : new Person.Builder().setName(PEER).setKey("synthetic-peer-key").build());
        Notification notification = new Notification.Builder(context, "synthetic-compact")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setStyle(style).build();
        notification.extras.putBoolean(NotificationMessages.STORED, true);
        notification.extras.putString(NotificationMessages.TITLE, group ? "Synthetic group" : PEER);
        return notification;
    }

    private static Notification noticeWithBody(Context context, String body) {
        Notification notification = notice(context, false, false);
        Notification.MessagingStyle replacement = new Notification.MessagingStyle(
                new Person.Builder().setName("Synthetic self").build()).setGroupConversation(false)
                .addMessage(body, 100, new Person.Builder().setName(PEER).build());
        return Notification.Builder.recoverBuilder(context, notification).setStyle(replacement).build();
    }

    private static Notification roundTrip(Notification notification) {
        Parcel parcel = Parcel.obtain();
        try {
            notification.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return Notification.CREATOR.createFromParcel(parcel);
        } finally { parcel.recycle(); }
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    private PrivateCompactHeadsUpInstrumentation() {}
}
