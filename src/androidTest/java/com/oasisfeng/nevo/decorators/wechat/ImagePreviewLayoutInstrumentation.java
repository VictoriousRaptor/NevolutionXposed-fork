package com.oasisfeng.nevo.decorators.wechat;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Parcel;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.R;

import java.util.concurrent.atomic.AtomicReference;

/** Framework/resource checks of the production layout, without sending WeChat messages. */
final class ImagePreviewLayoutInstrumentation {
    static void fitStartAndActions(Instrumentation instrumentation) {
        Context module = instrumentation.getTargetContext();
        verify(instrumentation, module, module);
        try {
            Context wechat = module.createPackageContext("com.tencent.mm", Context.CONTEXT_IGNORE_SECURITY);
            verify(instrumentation, wechat, module);
        } catch (android.content.pm.PackageManager.NameNotFoundException absent) {
            throw new AssertionError("Installed WeChat context required for cross-package resource check", absent);
        }
    }

    private static void verify(Instrumentation instrumentation, Context owner, Context module) {
        PendingIntent reply = PendingIntent.getBroadcast(module, 777,
                new Intent("synthetic.preview.reply").setPackage(module.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        Notification current = new Notification.Builder(owner, "synthetic-preview")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Synthetic chat")
                .setContentText("[图片]").setWhen(100)
                .addAction(new Notification.Action.Builder(null, "Reply", reply)
                        .addRemoteInput(new RemoteInput.Builder("synthetic-input").build()).build()).build();
        current.extras.putString(NotificationMessages.KEY, "synthetic-chat");
        Bitmap bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888);
        Notification preview = roundTrip(ImagePreviewLoader.buildPreviewNotification(owner, current, bitmap, 77));
        check(ImagePreviewLoader.isPreview(preview), "preview marker lost");
        check("[图片]".contentEquals(preview.extras.getCharSequence(Notification.EXTRA_TEXT)), "placeholder changed");
        check("synthetic-chat".equals(preview.extras.getString(NotificationMessages.KEY)), "identity lost");
        check((preview.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0, "preview update would alert again");
        check(preview.actions != null && preview.actions.length == 1
                && preview.actions[0].actionIntent.equals(reply)
                && "synthetic-input".equals(preview.actions[0].getRemoteInputs()[0].getResultKey()), "reply action changed");
        check(preview.bigContentView != null && BuildConfig.APPLICATION_ID.equals(preview.bigContentView.getPackage()),
                "layout resources incorrectly owned by notification host");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                View root = Notification.Builder.recoverBuilder(owner, preview).createBigContentView()
                        .apply(module, new FrameLayout(module));
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
        Notification preview = ImagePreviewLoader.buildPreviewNotification(context, current,
                Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888), 78);
        new ImagePreviewLoader(null).discard("synthetic", 778, preview);
        check(!ImagePreviewLoader.isPreview(preview), "discard kept preview marker");
        check(preview.bigContentView == null, "discard kept image layout");
        check(!preview.extras.containsKey(Notification.EXTRA_PICTURE), "discard kept picture");
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
