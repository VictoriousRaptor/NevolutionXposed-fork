package com.oasisfeng.nevo.xposed;

import android.content.Context;
import android.content.SharedPreferences;

import com.oasisfeng.nevo.decorators.wechat.ImagePreviewPolicy;

import java.lang.reflect.Method;

/** Uses isolated Android preferences; never changes the user's module settings. */
public final class RemotePreferenceStoreInstrumentation {
    public static void imageWaitSync(Context context) {
        Context storage = context.createDeviceProtectedStorageContext();
        SharedPreferences local = storage.getSharedPreferences("image-wait-sync-local-test", Context.MODE_PRIVATE);
        SharedPreferences remote = storage.getSharedPreferences("image-wait-sync-remote-test", Context.MODE_PRIVATE);
        String key = ImagePreviewPolicy.PREF_WAIT_MS;
        String removedKey = "WeChatDecorator.hide_expanded_app_name";
        try {
            local.edit().clear().putString(key, "7500").putBoolean("WeChatDecorator.enabled", false)
                    .putBoolean(removedKey, true).putInt("settings_schema_version", 10).commit();
            remote.edit().clear().putString(key, "5000").putBoolean(removedKey, false)
                    .putInt("settings_schema_version", 10).commit();
            Method sync = RemotePreferenceStore.class.getDeclaredMethod("synchronize", SharedPreferences.class, SharedPreferences.class);
            sync.setAccessible(true);
            sync.invoke(null, local, remote);
            check("7500".equals(local.getString(key, null)) && "7500".equals(remote.getString(key, null)), "local wait not synchronized");
            check(!remote.getBoolean("WeChatDecorator.enabled", true), "boolean choice changed during string sync");
            check(!local.contains(removedKey) && !remote.contains(removedKey), "removed app-name setting retained in storage");

            local.edit().clear().commit();
            remote.edit().clear().putString(key, "5000").commit();
            sync.invoke(null, local, remote);
            check("5000".equals(local.getString(key, null)), "remote wait lost when local value absent");

            local.edit().putString(key, "30001").commit();
            sync.invoke(null, local, remote);
            check("1500".equals(local.getString(key, null)) && "1500".equals(remote.getString(key, null)), "invalid wait not normalized");

            local.edit().clear().commit();
            remote.edit().clear().putInt(key, 5000).commit();
            sync.invoke(null, local, remote);
            check("1500".equals(local.getString(key, null)) && "1500".equals(remote.getString(key, null)), "wrong stored type not normalized");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        finally {
            local.edit().clear().commit();
            remote.edit().clear().commit();
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
