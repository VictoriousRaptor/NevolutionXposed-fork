package com.oasisfeng.nevo.decorators.wechat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import com.oasisfeng.nevo.xposed.BuildConfig;

/** Module-owned writable cache endpoint. The hooked WeChat process cannot edit libxposed remote preferences. */
public final class WeChatProfileCacheProvider extends ContentProvider {
    static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".profilecache";
    static final String READ = "read", WRITE = "write", REMOVE = "remove";
    static final String FEATURE = "feature", VERSION_NAME = "versionName", VERSION_CODE = "versionCode";
    static final String UPDATE_TIME = "updateTime", REVISION = "revision", CANDIDATE_ID = "candidateId";
    static final String OK = "ok";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) throw new IllegalStateException("Cache provider unavailable");
        int uid = Binder.getCallingUid();
        if (!authorizedCaller(uid, Process.myUid(), context.getPackageManager().getPackagesForUid(uid)))
            throw new SecurityException("Cache caller not allowed");
        if (arg != null || extras == null || !(READ.equals(method) || WRITE.equals(method) || REMOVE.equals(method)))
            throw new IllegalArgumentException("Invalid cache operation");
        if (extras.size() != (WRITE.equals(method) ? 6 : 5)
                || !(extras.get(FEATURE) instanceof String) || !(extras.get(VERSION_NAME) instanceof String)
                || !(extras.get(VERSION_CODE) instanceof Long) || !(extras.get(UPDATE_TIME) instanceof Long)
                || !(extras.get(REVISION) instanceof Integer)
                || (WRITE.equals(method) && !(extras.get(CANDIDATE_ID) instanceof String)))
            throw new IllegalArgumentException("Invalid cache payload");

        final String feature, name, candidate;
        final long code, update;
        final int revision;
        try {
            feature = extras.getString(FEATURE);
            name = extras.getString(VERSION_NAME);
            code = extras.getLong(VERSION_CODE, -1);
            update = extras.getLong(UPDATE_TIME, -1);
            revision = extras.getInt(REVISION, -1);
            candidate = WRITE.equals(method) ? extras.getString(CANDIDATE_ID) : null;
        } catch (ClassCastException invalid) {
            throw new IllegalArgumentException("Invalid cache value type", invalid);
        }
        if (!validKey(feature, name, code, update, revision)
                || (WRITE.equals(method) && !validCandidateId(candidate)))
            throw new IllegalArgumentException("Invalid cache key or candidate");
        WeChatProfileCache cache = new WeChatProfileCache(
                context.getSharedPreferences(WeChatProfileCache.GROUP, Context.MODE_PRIVATE));
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId(name, code, update);
        Bundle result = new Bundle();
        switch (method) {
            case READ:
                result.putString(CANDIDATE_ID, cache.read(feature, build, revision));
                break;
            case WRITE:
                cache.write(feature, build, revision, candidate);
                result.putBoolean(OK, true);
                break;
            case REMOVE:
                cache.remove(feature, build, revision);
                result.putBoolean(OK, true);
                break;
            default: throw new AssertionError(method);
        }
        return result;
    }

    @Override public Bundle call(String authority, String method, String arg, Bundle extras) {
        if (!AUTHORITY.equals(authority)) throw new IllegalArgumentException("Invalid cache authority");
        return call(method, arg, extras);
    }

    static boolean authorizedCaller(int callingUid, int ownUid, String[] callerPackages) {
        if (callingUid == ownUid) return true;
        if (callerPackages == null) return false;
        for (String name : callerPackages) if (WeChatDecorator.WECHAT_PACKAGE.equals(name)) return true;
        return false;
    }

    static boolean validKey(String feature, String versionName, long versionCode, long updateTime, int revision) {
        return validText(feature, 32, false) && validText(versionName, 32, false)
                && versionCode >= 0 && updateTime >= 0 && revision >= 0 && revision <= 1_000_000;
    }

    static boolean validCandidateId(String id) { return validText(id, 80, true); }

    private static boolean validText(String text, int maxLength, boolean allowSlash) {
        if (text == null || text.isEmpty() || text.length() > maxLength) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c == '.' || c == '_' || c == '-' || (allowSlash && c == '/'))) return false;
        }
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
