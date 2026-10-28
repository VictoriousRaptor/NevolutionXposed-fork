package com.oasisfeng.nevo.decorators.wechat;

import android.content.SharedPreferences;
import java.util.Objects;

/** Stores candidate IDs in the module app's private preference group, never in WeChat storage. */
public final class WeChatProfileCache implements WeChatProfileSelector.Cache {
    public static final String GROUP = "wechat_profile_candidates";
    private final SharedPreferences preferences;
    /** Caller supplies writable module-owned preferences for {@link #GROUP}. */
    public WeChatProfileCache(SharedPreferences preferences) { this.preferences = Objects.requireNonNull(preferences); }
    @Override public String read(String feature, WeChatProfileSelector.BuildId build, int revision) {
        return preferences.getString(WeChatProfileSelector.cacheKey(feature, build, revision), null);
    }
    @Override public void write(String feature, WeChatProfileSelector.BuildId build, int revision, String candidateId) {
        if (!preferences.edit().putString(WeChatProfileSelector.cacheKey(feature, build, revision), candidateId).commit())
            throw new IllegalStateException("Candidate cache write failed");
    }
    @Override public void remove(String feature, WeChatProfileSelector.BuildId build, int revision) {
        if (!preferences.edit().remove(WeChatProfileSelector.cacheKey(feature, build, revision)).commit())
            throw new IllegalStateException("Candidate cache removal failed");
    }
}
