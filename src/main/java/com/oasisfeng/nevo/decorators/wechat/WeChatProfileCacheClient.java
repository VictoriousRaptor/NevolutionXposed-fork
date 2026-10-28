package com.oasisfeng.nevo.decorators.wechat;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.Objects;

/** WeChat-process IPC client for the module's private candidate cache. */
public final class WeChatProfileCacheClient implements WeChatProfileSelector.Cache {
    private static final String TAG = "WeChat.ProfileCache";
    private static final Uri CACHE_URI = Uri.parse("content://" + WeChatProfileCacheProvider.AUTHORITY);
    private final ContentResolver resolver;

    public WeChatProfileCacheClient(Context context) {
        resolver = Objects.requireNonNull(context).getContentResolver();
    }

    @Override public String read(String feature, WeChatProfileSelector.BuildId build, int revision) {
        return call(WeChatProfileCacheProvider.READ, feature, build, revision, null)
                .getString(WeChatProfileCacheProvider.CANDIDATE_ID);
    }

    @Override public void write(String feature, WeChatProfileSelector.BuildId build, int revision, String candidateId) {
        ensureAcknowledged(WeChatProfileCacheProvider.WRITE,
                call(WeChatProfileCacheProvider.WRITE, feature, build, revision, candidateId));
    }

    @Override public void remove(String feature, WeChatProfileSelector.BuildId build, int revision) {
        ensureAcknowledged(WeChatProfileCacheProvider.REMOVE,
                call(WeChatProfileCacheProvider.REMOVE, feature, build, revision, null));
    }

    private static void ensureAcknowledged(String stage, Bundle result) {
        if (result.getBoolean(WeChatProfileCacheProvider.OK)) return;
        Log.w(TAG, "stage=" + stage + " type=MissingAcknowledgment");
        throw new IllegalStateException("Candidate cache operation not acknowledged");
    }

    private Bundle call(String stage, String feature, WeChatProfileSelector.BuildId build, int revision, String id) {
        Bundle args = new Bundle();
        args.putString(WeChatProfileCacheProvider.FEATURE, feature);
        args.putString(WeChatProfileCacheProvider.VERSION_NAME, build.versionName);
        args.putLong(WeChatProfileCacheProvider.VERSION_CODE, build.versionCode);
        args.putLong(WeChatProfileCacheProvider.UPDATE_TIME, build.lastUpdateTime);
        args.putInt(WeChatProfileCacheProvider.REVISION, revision);
        if (id != null) args.putString(WeChatProfileCacheProvider.CANDIDATE_ID, id);
        try {
            Bundle result = resolver.call(CACHE_URI, stage, null, args);
            if (result == null) throw new IllegalStateException("Cache provider returned no result");
            return result;
        } catch (RuntimeException failure) {
            Log.w(TAG, "stage=" + stage + " type=" + failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
