package com.oasisfeng.nevo.decorators.wechat;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.Objects;
import java.util.HashMap;
import java.util.Map;

/** WeChat-process IPC client for the module's private candidate cache. */
public final class WeChatProfileCacheClient implements WeChatProfileSelector.Cache {
    private static final String TAG = "WeChat.ProfileCache";
    interface Transport { Map<String, Object> call(String method, Map<String, Object> request); }
    private final Transport transport;

    public WeChatProfileCacheClient(Context context) {
        this(new ResolverTransport(Objects.requireNonNull(context).getContentResolver()));
    }
    WeChatProfileCacheClient(Transport transport) { this.transport = Objects.requireNonNull(transport); }

    @Override public String read(String feature, WeChatProfileSelector.BuildId build, int revision) {
        return (String) call(WeChatProfileCacheProvider.READ, feature, build, revision, null)
                .get(WeChatProfileCacheProvider.CANDIDATE_ID);
    }

    @Override public void write(String feature, WeChatProfileSelector.BuildId build, int revision, String candidateId) {
        ensureAcknowledged(WeChatProfileCacheProvider.WRITE,
                call(WeChatProfileCacheProvider.WRITE, feature, build, revision, candidateId));
    }

    @Override public void remove(String feature, WeChatProfileSelector.BuildId build, int revision) {
        ensureAcknowledged(WeChatProfileCacheProvider.REMOVE,
                call(WeChatProfileCacheProvider.REMOVE, feature, build, revision, null));
    }

    private static void ensureAcknowledged(String stage, Map<String, Object> result) {
        if (Boolean.TRUE.equals(result.get(WeChatProfileCacheProvider.OK))) return;
        IllegalStateException failure = new IllegalStateException("Candidate cache operation not acknowledged");
        logFailure(stage, failure.getClass().getSimpleName());
        throw failure;
    }

    private Map<String, Object> call(String stage, String feature, WeChatProfileSelector.BuildId build,
            int revision, String id) {
        Map<String, Object> request = new HashMap<>();
        request.put(WeChatProfileCacheProvider.FEATURE, feature);
        request.put(WeChatProfileCacheProvider.VERSION_NAME, build.versionName);
        request.put(WeChatProfileCacheProvider.VERSION_CODE, build.versionCode);
        request.put(WeChatProfileCacheProvider.UPDATE_TIME, build.lastUpdateTime);
        request.put(WeChatProfileCacheProvider.REVISION, revision);
        if (id != null) request.put(WeChatProfileCacheProvider.CANDIDATE_ID, id);
        try {
            Map<String, Object> result = transport.call(stage, request);
            if (result == null) throw new IllegalStateException("Cache provider returned no result");
            return result;
        } catch (RuntimeException failure) {
            logFailure(stage, failure.getClass().getSimpleName());
            throw failure;
        }
    }

    private static void logFailure(String stage, String type) {
        // Android's host-test Log stub may throw; logging must never replace the IPC failure.
        try { Log.w(TAG, "stage=" + stage + " type=" + type); }
        catch (RuntimeException ignored) { }
    }

    private static final class ResolverTransport implements Transport {
        private static final Uri CACHE_URI = Uri.parse("content://" + WeChatProfileCacheProvider.AUTHORITY);
        private final ContentResolver resolver;
        ResolverTransport(ContentResolver resolver) { this.resolver = Objects.requireNonNull(resolver); }
        @Override public Map<String, Object> call(String method, Map<String, Object> request) {
            Bundle args = new Bundle();
            args.putString(WeChatProfileCacheProvider.FEATURE, (String) request.get(WeChatProfileCacheProvider.FEATURE));
            args.putString(WeChatProfileCacheProvider.VERSION_NAME,
                    (String) request.get(WeChatProfileCacheProvider.VERSION_NAME));
            args.putLong(WeChatProfileCacheProvider.VERSION_CODE,
                    (Long) request.get(WeChatProfileCacheProvider.VERSION_CODE));
            args.putLong(WeChatProfileCacheProvider.UPDATE_TIME,
                    (Long) request.get(WeChatProfileCacheProvider.UPDATE_TIME));
            args.putInt(WeChatProfileCacheProvider.REVISION,
                    (Integer) request.get(WeChatProfileCacheProvider.REVISION));
            if (request.containsKey(WeChatProfileCacheProvider.CANDIDATE_ID))
                args.putString(WeChatProfileCacheProvider.CANDIDATE_ID,
                        (String) request.get(WeChatProfileCacheProvider.CANDIDATE_ID));
            Bundle result = resolver.call(CACHE_URI, method, null, args);
            if (result == null) return null;
            Map<String, Object> response = new HashMap<>();
            if (WeChatProfileCacheProvider.READ.equals(method))
                response.put(WeChatProfileCacheProvider.CANDIDATE_ID,
                        result.getString(WeChatProfileCacheProvider.CANDIDATE_ID));
            else response.put(WeChatProfileCacheProvider.OK, result.getBoolean(WeChatProfileCacheProvider.OK));
            return response;
        }
    }
}
