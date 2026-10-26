package com.oasisfeng.nevo.decorators.wechat;

import android.content.pm.ApplicationInfo;
import android.widget.RemoteViews;

import java.lang.reflect.Constructor;
import java.util.Objects;

/** Creates module-owned layouts without querying the module package from the host process. */
final class ImagePreviewRemoteViews {
    private ImagePreviewRemoteViews() {}

    static RemoteViews create(ApplicationInfo moduleInfo, int layout) {
        try {
            // This constructor is hidden, including on API 26. Do not link a newer SDK overload
            // or retry the String constructor: the host may be unable to query the module package.
            return create(RemoteViews.class, ApplicationInfo.class, moduleInfo, layout);
        } catch (ReflectiveOperationException unavailable) {
            throw new IllegalStateException("Module image layout constructor unavailable", unavailable);
        }
    }

    static <T, A> T create(Class<T> viewsType, Class<A> infoType, A moduleInfo, int layout)
            throws ReflectiveOperationException {
        Objects.requireNonNull(moduleInfo, "Module ApplicationInfo is required");
        Constructor<T> constructor = viewsType.getDeclaredConstructor(infoType, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(moduleInfo, layout);
    }
}
