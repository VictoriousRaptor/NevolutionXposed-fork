package com.oasisfeng.nevo.xposed;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Manual display comparisons, packaged exclusively in the debug source set. */
public final class ImageScaleTestActivity extends AppCompatActivity {
    private static final String CHANNEL = "image_scale_debug";
    private static final String TAG = "image_scale_debug";
    private static final int FIRST_ID = 9300;
    private static final int CASE_COUNT = 6;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private Bitmap square, full;
    private int pendingCase = -1;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) postPendingCase();
                else {
                    pendingCase = -1;
                    Toast.makeText(this, R.string.scale_test_permission, Toast.LENGTH_LONG).show();
                }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.scale_test_title);
        if (state != null) pendingCase = state.getInt("pendingCase", -1);
        TextView loading = new TextView(this);
        loading.setText(R.string.scale_test_loading);
        loading.setPadding(dp(16), dp(16), dp(16), dp(16));
        setContentView(loading);
        loader.execute(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap squareImage = BitmapFactory.decodeResource(getResources(), R.drawable.scale_test_lenna_square, options);
            Bitmap fullImage = BitmapFactory.decodeResource(getResources(), R.drawable.scale_test_lenna_full, options);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (squareImage == null || fullImage == null) {
                    loading.setText(R.string.scale_test_failed);
                    return;
                }
                square = squareImage;
                full = fullImage;
                showComparisons();
                if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) postPendingCase();
            });
        });
    }

    private void showComparisons() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(content);
        TextView description = new TextView(this);
        description.setText(R.string.scale_test_description);
        content.addView(description);
        for (int index = 0; index < CASE_COUNT; index++) {
            final int caseIndex = index;
            Bitmap bitmap = image(index);
            TextView title = new TextView(this);
            title.setText(title(index));
            title.setTextSize(18);
            title.setPadding(0, dp(20), 0, dp(4));
            content.addView(title);
            TextView details = new TextView(this);
            details.setText(getString(R.string.scale_test_dimensions, bitmap.getWidth(), bitmap.getHeight()));
            content.addView(details);
            ImageView preview = new ImageView(this);
            preview.setContentDescription(title(index));
            preview.setBackgroundColor(Color.rgb(224, 224, 224));
            preview.setScaleType(index >= 4 ? ImageView.ScaleType.FIT_START
                    : index % 2 == 0 ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
            preview.setImageBitmap(bitmap);
            content.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)));
            Button send = new Button(this);
            send.setText(R.string.scale_test_send);
            send.setOnClickListener(view -> requestNotification(caseIndex));
            content.addView(send);
        }
        Button clear = new Button(this);
        clear.setText(R.string.scale_test_clear);
        clear.setOnClickListener(view -> {
            NotificationManager manager = getSystemService(NotificationManager.class);
            for (int index = 0; index < CASE_COUNT; index++) manager.cancel(TAG, FIRST_ID + index);
        });
        content.addView(clear);
        setContentView(scroll);
    }

    private void requestNotification(int index) {
        pendingCase = index;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS);
        else postPendingCase();
    }

    @SuppressLint("MissingPermission") // Permission is requested before publishing, including after recreation.
    private void postPendingCase() {
        if (pendingCase < 0 || pendingCase >= CASE_COUNT || square == null || full == null) return;
        final int index = pendingCase;
        pendingCase = -1;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.scale_test_title), NotificationManager.IMPORTANCE_HIGH));
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL).getImportance()
                == NotificationManager.IMPORTANCE_NONE) {
            Toast.makeText(this, R.string.scale_test_permission, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL));
            return;
        }
        RemoteViews expanded = new RemoteViews(getPackageName(), index >= 4 ? R.layout.scale_test_notification_start
                : index % 2 == 0 ? R.layout.scale_test_notification_fit : R.layout.scale_test_notification_crop);
        expanded.setTextViewText(R.id.scale_test_caption, title(index));
        expanded.setImageViewBitmap(R.id.scale_test_picture, image(index));
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_scale_test)
                .setContentTitle(title(index)).setContentText(getString(R.string.scale_test_placeholder))
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomBigContentView(expanded)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, ImageScaleTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE).build();
        manager.cancel(TAG, FIRST_ID + index);
        manager.notify(TAG, FIRST_ID + index, notification);
        Toast.makeText(this, R.string.scale_test_sent, Toast.LENGTH_SHORT).show();
    }

    private static boolean isSquare(int index) { return index < 2 || index == 4; }

    private Bitmap image(int index) { return isSquare(index) ? square : full; }

    private String title(int index) {
        return getString(isSquare(index) ? R.string.scale_test_square : R.string.scale_test_full)
                + " · " + (index >= 4 ? "fitStart" : index % 2 == 0 ? "fitCenter" : "centerCrop");
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("pendingCase", pendingCase);
    }

    @Override protected void onDestroy() {
        loader.shutdownNow();
        super.onDestroy();
    }
}
