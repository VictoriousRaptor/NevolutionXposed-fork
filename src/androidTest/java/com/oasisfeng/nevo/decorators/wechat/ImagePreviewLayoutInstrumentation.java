package com.oasisfeng.nevo.decorators.wechat;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.oasisfeng.nevo.xposed.R;

import java.util.concurrent.atomic.AtomicReference;

/** Framework/resource checks of the production layout, without sending WeChat messages. */
final class ImagePreviewLayoutInstrumentation {
    static void fitStartAndActions(Instrumentation instrumentation) {
        Context module = instrumentation.getTargetContext();
        verify(instrumentation, module, module);
        verifyGroup(instrumentation, module);
        try {
            Context wechat = module.createPackageContext("com.tencent.mm", Context.CONTEXT_IGNORE_SECURITY);
            verify(instrumentation, wechat, module);
        } catch (android.content.pm.PackageManager.NameNotFoundException absent) {
            throw new AssertionError("Installed WeChat context required for cross-package resource check", absent);
        }
    }

    static void messagingImageUsesSystemContentInset(Instrumentation instrumentation) {
        Context module = instrumentation.getTargetContext();
        int insetId = module.getResources().getIdentifier("notification_2025_content_margin_start", "dimen", "android");
        if (insetId == 0) return; // Older framework layout; this regression targets the 2025 conversation template.
        int expectedInset = module.getResources().getDimensionPixelSize(insetId);
        for (boolean group : new boolean[]{false, true}) {
            Notification current = new Notification.Builder(module, "synthetic-image-alignment")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setStyle(new Notification.MessagingStyle(new Person.Builder().setName("Synthetic self").build())
                            .setConversationTitle("Synthetic chat").setGroupConversation(group)
                            .addMessage("[图片]", 100, new Person.Builder().setName("Synthetic peer").build()))
                    .build();
            Notification preview = ImagePreviewLoader.buildPreviewNotification(module, module.getApplicationInfo(),
                    current, Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888), group ? 83 : 82);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> {
                try {
                    RemoteViews original = Notification.Builder.recoverBuilder(module, preview).createBigContentView();
                    int conversation = module.getResources().getIdentifier(
                            "notification_2025_template_expanded_conversation", "layout", "android");
                    int messaging = module.getResources().getIdentifier(
                            "notification_2025_template_expanded_messaging", "layout", "android");
                    check(original.getLayoutId() == conversation || original.getLayoutId() == messaging,
                            "synthetic preview did not use the 2025 MessagingStyle layout");
                    RemoteViews expanded = WeChatNotificationAppearance.withImagePreview(original, module.getResources(),
                            module.getApplicationInfo(), preview);
                    View root = expanded.apply(module, new FrameLayout(module));
                    ImageView image = root.findViewById(R.id.wechat_preview_image);
                    check(image != null && image.getPaddingStart() == expectedInset && image.getPaddingEnd() == 0,
                            "image did not align to the system content inset: expected=" + expectedInset
                                    + " actual=" + (image == null ? "missing" : image.getPaddingStart()));
                } catch (Throwable error) { failure.set(error); }
            });
            if (failure.get() != null) throw new AssertionError("Messaging image alignment failed", failure.get());
        }
        Notification fallback = ImagePreviewLoader.buildPreviewNotification(module, module.getApplicationInfo(),
                new Notification.Builder(module, "synthetic-image-alignment")
                        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Synthetic chat").build(),
                Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888), 84);
        check(fallback.bigContentView != null && fallback.bigContentView.getLayoutId() == R.layout.wechat_image_preview,
                "non-MessagingStyle fallback layout changed");
    }

    static void offloadedPictureIconStillRenders(Instrumentation instrumentation) {
        Context module = instrumentation.getTargetContext();
        Bitmap bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888);
        Notification current = new Notification.Builder(module, "synthetic-offloaded-preview")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(new Notification.MessagingStyle(new Person.Builder().setName("Synthetic self").build())
                        .setConversationTitle("Synthetic chat").setGroupConversation(false)
                        .addMessage("[图片]", 100, new Person.Builder().setName("Synthetic peer").build()))
                .build();
        Notification preview = ImagePreviewLoader.buildPreviewNotification(module, module.getApplicationInfo(), current, bitmap, 81);
        preview.extras.remove(Notification.EXTRA_PICTURE);
        preview.extras.putParcelable(Notification.EXTRA_PICTURE_ICON, Icon.createWithBitmap(bitmap));
        preview = roundTrip(preview);
        Notification offloaded = preview;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                RemoteViews original = Notification.Builder.recoverBuilder(module, offloaded).createBigContentView();
                RemoteViews expanded = WeChatNotificationAppearance.withImagePreview(original, module.getResources(),
                        module.getApplicationInfo(), offloaded);
                View root = expanded.apply(module, new FrameLayout(module));
                ImageView image = root.findViewById(R.id.wechat_preview_image);
                check(image != null && image.getDrawable() != null, "offloaded picture icon was omitted");
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Offloaded picture rendering failed", failure.get());
    }

    private static void verifyGroup(Instrumentation instrumentation, Context module) {
        Notification.MessagingStyle style = new Notification.MessagingStyle(new Person.Builder().setName("Synthetic self").build())
                .setConversationTitle("Synthetic group").setGroupConversation(true)
                .addMessage("Synthetic first", 97, new Person.Builder().setName("Synthetic sender A").build())
                .addMessage("Synthetic second", 98, new Person.Builder().setName("Synthetic sender B").build())
                .addMessage("Synthetic third", 99, new Person.Builder().setName("Synthetic sender C").build())
                .addMessage("[图片]", 100, new Person.Builder().setName("Synthetic sender").build());
        Notification current = new Notification.Builder(module, "synthetic-group-preview")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setLargeIcon(Icon.createWithBitmap(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)))
                .setStyle(style).build();
        Notification preview = roundTrip(ImagePreviewLoader.buildPreviewNotification(module, module.getApplicationInfo(), current,
                Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888), 80));
        check(WeChatNotificationAppearance.isGroupMessaging(preview), "group preview lost messaging template");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                Notification.Builder builder = Notification.Builder.recoverBuilder(module, preview).setStyle(style);
                NotificationAppearanceHookProfile.Avatar avatar = NotificationAppearanceHookProfile.avatar(
                        Notification.MessagingStyle.class, Notification.Style.class, Notification.Builder.class,
                        Context.class, Notification.class, Icon.class, RemoteViews.class);
                WeChatNotificationAppearance.useNativeAvatar(avatar, style, preview);
                check(avatar.conversationType.getInt(style) == 1, "group picture did not use conversation layout");
                RemoteViews original = builder.createBigContentView();
                RemoteViews expanded = WeChatNotificationAppearance.withImagePreview(original, module.getResources(),
                        module.getApplicationInfo(), preview);
                check(expanded != original, "group image mutated the original template");
                View root = expanded.apply(module, new FrameLayout(module));
                ImageView image = root.findViewById(R.id.wechat_preview_image);
                check(image != null && image.getScaleType() == ImageView.ScaleType.FIT_START, "group preview lost fitStart");
                float density = module.getResources().getDisplayMetrics().density;
                root.measure(View.MeasureSpec.makeMeasureSpec(Math.round(320 * density), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(Math.round(480 * density), View.MeasureSpec.AT_MOST));
                root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
                Rect imageBounds = new Rect();
                image.getDrawingRect(imageBounds);
                ((android.view.ViewGroup) root).offsetDescendantRectToMyCoords(image, imageBounds);
                check(image.getHeight() > 0 && imageBounds.bottom <= root.getHeight(), "group history clipped picture");
                check(root.findViewById(R.id.wechat_preview_title) == null, "group preview added a custom title");
                int titleId = module.getResources().getIdentifier("title", "id", "android");
                TextView title = root.findViewById(titleId);
                check(title != null && "Synthetic group".contentEquals(title.getText()), "group conversation title changed");
                check(original.apply(module, new FrameLayout(module)).findViewById(R.id.wechat_preview_image) == null,
                        "cached messaging template retained image");
                expanded.reapply(module, root);
                int columnId = module.getResources().getIdentifier("notification_main_column", "id", "android");
                android.view.ViewGroup column = root.findViewById(columnId);
                int images = 0;
                for (int i = 0; i < column.getChildCount(); i++)
                    if (column.getChildAt(i).getId() == R.id.wechat_preview_image) images++;
                check(images == 1, "reapply duplicated the group picture");
                new ImagePreviewLoader(null).discard("synthetic-group", 780, preview);
                RemoteViews cleared = WeChatNotificationAppearance.withImagePreview(original, module.getResources(),
                        module.getApplicationInfo(), preview);
                cleared.reapply(module, root);
                check(root.findViewById(R.id.wechat_preview_image) == null, "discarded group preview returned");
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Group messaging image rendering failed", failure.get());
    }

    private static void verify(Instrumentation instrumentation, Context owner, Context module) {
        PendingIntent reply = PendingIntent.getBroadcast(module, 777,
                new Intent("synthetic.preview.reply").setPackage(module.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        Notification current = new Notification.Builder(owner, "synthetic-preview")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Synthetic chat")
                .setContentText("[图片]").setWhen(100)
                .setLargeIcon(Icon.createWithBitmap(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)))
                .setStyle(new Notification.MessagingStyle(new Person.Builder().setName("Synthetic self").build())
                        .setConversationTitle("Synthetic chat").setGroupConversation(false)
                        .addMessage("[图片]", 100, new Person.Builder().setName("Synthetic peer").build()))
                .addAction(new Notification.Action.Builder(null, "Reply", reply)
                        .addRemoteInput(new RemoteInput.Builder("synthetic-input").build()).build()).build();
        current.extras.putString(NotificationMessages.KEY, "synthetic-chat");
        Bitmap bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888);
        Notification preview = roundTrip(ImagePreviewLoader.buildPreviewNotification(owner, module.getApplicationInfo(), current, bitmap, 77));
        check(ImagePreviewLoader.isPreview(preview), "preview marker lost");
        check("[图片]".contentEquals(preview.extras.getCharSequence(Notification.EXTRA_TEXT)), "placeholder changed");
        check("synthetic-chat".equals(preview.extras.getString(NotificationMessages.KEY)), "identity lost");
        check((preview.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0, "preview update would alert again");
        check(preview.actions != null && preview.actions.length == 1
                && preview.actions[0].actionIntent.equals(reply)
                && "synthetic-input".equals(preview.actions[0].getRemoteInputs()[0].getResultKey()), "reply action changed");
        check("android.app.Notification$MessagingStyle".equals(preview.extras.getString(Notification.EXTRA_TEMPLATE)),
                "private image replaced the messaging template");
        check(preview.bigContentView == null, "private image installed a custom title layout");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                Notification.MessagingStyle style = new Notification.MessagingStyle(
                        new Person.Builder().setName("Synthetic self").build())
                        .setConversationTitle("Synthetic chat").setGroupConversation(false)
                        .addMessage("[图片]", 100, new Person.Builder().setName("Synthetic peer").build());
                NotificationAppearanceHookProfile.Avatar avatar = NotificationAppearanceHookProfile.avatar(
                        Notification.MessagingStyle.class, Notification.Style.class, Notification.Builder.class,
                        Context.class, Notification.class, Icon.class, RemoteViews.class);
                WeChatNotificationAppearance.useNativeAvatar(avatar, style, preview);
                check(avatar.conversationType.getInt(style) == 1, "private picture lost native avatar badge layout");
                RemoteViews original = Notification.Builder.recoverBuilder(owner, preview).setStyle(style).createBigContentView();
                View root = WeChatNotificationAppearance.withImagePreview(original, module.getResources(),
                        module.getApplicationInfo(), preview).apply(module, new FrameLayout(module));
                int width = Math.round(320 * module.getResources().getDisplayMetrics().density);
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(width * 2, View.MeasureSpec.AT_MOST));
                root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
                ImageView image = root.findViewById(R.id.wechat_preview_image);
                check(image != null && image.getScaleType() == ImageView.ScaleType.FIT_START, "fitStart missing");
                RectF area = new RectF(image.getDrawable().getBounds());
                image.getImageMatrix().mapRect(area);
                check(Math.abs(area.left) < 1 && Math.abs(area.top) < 1, "image is not top-left aligned");
                check(area.right <= image.getWidth() + 1 && area.bottom <= image.getHeight() + 1, "image cropped");
                check(Math.abs(area.width() / area.height() - .5f) < .01f, "aspect ratio changed");
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Production expanded layout failed", failure.get());
    }

    static void discardClearsLayout(Context context) {
        Notification current = new Notification.Builder(context, "synthetic-preview")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Synthetic chat")
                .setContentText("[图片]").build();
        Notification preview = ImagePreviewLoader.buildPreviewNotification(context, context.getApplicationInfo(), current,
                Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888), 78);
        preview.extras.putParcelable(Notification.EXTRA_PICTURE_ICON,
                Icon.createWithBitmap(Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)));
        new ImagePreviewLoader(null).discard("synthetic", 778, preview);
        check(!ImagePreviewLoader.isPreview(preview), "discard kept preview marker");
        check(preview.bigContentView == null, "discard kept image layout");
        check(!preview.extras.containsKey(Notification.EXTRA_PICTURE), "discard kept picture");
        check(!preview.extras.containsKey(Notification.EXTRA_PICTURE_ICON), "discard kept offloaded picture icon");
    }

    static void modulePackageLookupUnavailable(Context context) {
        // A nonexistent package guarantees lookup denial even though this runner uses the module UID.
        ApplicationInfo hidden = new ApplicationInfo(context.getApplicationInfo());
        hidden.packageName = "synthetic.hidden.preview";
        try {
            context.getPackageManager().getApplicationInfo(hidden.packageName, 0);
            throw new AssertionError("Synthetic hidden package unexpectedly exists");
        } catch (PackageManager.NameNotFoundException expected) { /* Reproduce the failed lookup. */ }
        try {
            new RemoteViews(hidden.packageName, R.layout.wechat_image_preview);
            throw new AssertionError("Package-name constructor should fail in this environment");
        } catch (IllegalArgumentException expected) { /* The production bug used this constructor. */ }
        Notification current = new Notification.Builder(context, "synthetic-preview")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentText("[图片]").build();
        Notification preview = roundTrip(ImagePreviewLoader.buildPreviewNotification(context, hidden, current,
                Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888), 79));
        check(ImagePreviewLoader.isPreview(preview), "blocked lookup prevented image preview creation");
        check(preview.bigContentView != null && hidden.packageName.equals(preview.bigContentView.getPackage()),
                "explicit ApplicationInfo was not preserved");
        check(preview.bigContentView.getLayoutId() == R.layout.wechat_image_preview, "layout id changed");
        check(preview.extras.containsKey(Notification.EXTRA_PICTURE), "blocked lookup lost picture");
    }

    private static Notification roundTrip(Notification notification) {
        Parcel parcel = Parcel.obtain();
        try {
            notification.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return Notification.CREATOR.createFromParcel(parcel);
        } finally { parcel.recycle(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
