package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;

import java.util.Arrays;

/** Framework regressions without posting notifications or changing system channels. */
final class NotificationCompatibilityInstrumentation {
    static void carExtensionWithoutConversation(Context context) {
        PendingIntent originalDelete = PendingIntent.getBroadcast(context, 778,
                new Intent("synthetic.delete").setPackage(context.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, "synthetic-compatibility")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setDeleteIntent(originalDelete)
                .extend(new Notification.CarExtender().setColor(0xff123456)).build();
        check(notification.extras.getBundle("android.car.EXTENSIONS") != null, "missing car extension");
        check(new Notification.CarExtender(notification).getUnreadConversation() == null, "unexpected conversation");
        new WeChatDecorator.Local("WeChatDecorator").apply(null, "synthetic", 778, notification);
        check(originalDelete.equals(notification.deleteIntent), "missing conversation changed delete action");

        PendingIntent nativeRead = PendingIntent.getBroadcast(context, 779,
                new Intent("synthetic.read").setPackage(context.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Bundle partialConversation = new Bundle();
        partialConversation.putParcelable("on_read", nativeRead);
        notification.extras.getBundle("android.car.EXTENSIONS").putBundle("car_conversation", partialConversation);
        check(new Notification.CarExtender(notification).getUnreadConversation() == null, "partial conversation was accepted");
        new WeChatDecorator.Local("WeChatDecorator").apply(null, "synthetic", 779, notification);
        check(nativeRead.equals(notification.deleteIntent), "partial conversation lost native read action");
    }

    static void channelMigrationPreservesSettings() {
        NotificationChannel source = new NotificationChannel("synthetic-old", "Old", NotificationManager.IMPORTANCE_HIGH);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build();
        source.setSound(null, attributes);
        source.enableLights(true);
        source.setLightColor(0xff123456);
        source.enableVibration(true); // Default vibration has no custom pattern.
        NotificationChannel copy = WeChatDecorator.Local.cloneChannel(source, "synthetic-new", "New");
        check(copy.getSound() == null, "silent channel gained sound");
        check(attributes.equals(copy.getAudioAttributes()), "audio attributes changed");
        check(copy.shouldShowLights() && copy.getLightColor() == source.getLightColor(), "lights changed");
        check(copy.shouldVibrate(), "default vibration was disabled");

        source.setVibrationPattern(new long[]{0, 40, 20, 40});
        source.enableVibration(false);
        source.enableLights(false);
        copy = WeChatDecorator.Local.cloneChannel(source, "synthetic-disabled", "Disabled");
        check(!copy.shouldVibrate(), "disabled vibration was enabled by its stored pattern");
        check(!copy.shouldShowLights(), "disabled lights were enabled");
        check(Arrays.equals(source.getVibrationPattern(), copy.getVibrationPattern()), "vibration pattern changed");
        if (Build.VERSION.SDK_INT >= 35) {
            source.setVibrationEffect(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
            copy = WeChatDecorator.Local.cloneChannel(source, "synthetic-effect", "Effect");
            check(source.getVibrationEffect().equals(copy.getVibrationEffect()), "vibration effect changed");
            check(copy.shouldVibrate(), "vibration effect was disabled");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
