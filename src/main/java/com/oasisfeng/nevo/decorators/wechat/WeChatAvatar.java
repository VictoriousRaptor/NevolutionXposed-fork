package com.oasisfeng.nevo.decorators.wechat;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Uses WeChat's account-aware avatar service; never guesses an account directory or contact name. */
final class WeChatAvatar {
    private Method lookup;

    static boolean supported(String name, long code) {
        return "8.0.79".equals(name) && (code == 3182 || code == 3183);
    }

    static Method resolve(Class<?> owner, Class<?> bitmap) throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod("a", String.class);
        if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != bitmap) throw new NoSuchMethodException("Avatar signature");
        return method;
    }

    void initialize(Context context, ClassLoader loader) {
        lookup = null;
        try {
            WeChatProfileSelector.BuildId build = WeChatProfileSelector.BuildId.fromPackageInfo(
                    context.getPackageManager().getPackageInfo("com.tencent.mm", 0));
            if (!supported(build.versionName, build.versionCode)) return;
            lookup = resolve(Class.forName("com.tencent.mm.modelavatar.g", false, loader), Bitmap.class);
            Log.i("WeChat.Avatar", "source=lookup_ready");
        } catch (Exception failure) { logFailure(failure); }
    }

    boolean available() { return lookup != null; }

    Bitmap load(String talker) {
        if (lookup == null || talker == null || talker.isEmpty() || talker.contains("@")) return null;
        try {
            Bitmap bitmap = (Bitmap) lookup.invoke(null, talker);
            // WeChat owns and may recycle its cached bitmap. Keep an independent notification copy.
            return bitmap == null || bitmap.isRecycled() ? null : bitmap.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Exception failure) {
            lookup = null;
            logFailure(failure);
            return null;
        }
    }

    private static void logFailure(Exception failure) {
        Log.w("WeChat.Avatar", "source=lookup_unavailable type=" + failure.getClass().getSimpleName());
    }
}
