package com.oasisfeng.nevo.decorators.wechat;

import android.content.pm.PackageInfo;
import android.os.Build;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Per-process selection session. Each feature must consistently use one profile type and candidate list. */
public final class WeChatProfileSelector {
    public enum MatchKind { EXACT, EXPERIMENTAL, NO_MATCH, AMBIGUOUS }
    public static final class BuildId {
        public final String versionName;
        public final long versionCode, lastUpdateTime;
        public BuildId(String versionName, long versionCode, long lastUpdateTime) {
            this.versionName = versionName; this.versionCode = versionCode; this.lastUpdateTime = lastUpdateTime;
        }
        public static BuildId fromPackageInfo(PackageInfo info) {
            return new BuildId(info.versionName, Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode,
                    info.lastUpdateTime);
        }
    }
    public static final class Candidate<P> {
        public final String id, versionName;
        public final long verifiedCode;
        public final P profile;
        public Candidate(String id, String versionName, long verifiedCode, P profile) {
            this.id = Objects.requireNonNull(id); this.versionName = Objects.requireNonNull(versionName);
            this.verifiedCode = verifiedCode; this.profile = Objects.requireNonNull(profile);
        }
    }
    public interface Probe<P> { boolean valid(P profile) throws Throwable; }
    public interface Cache {
        String read(String feature, BuildId build, int revision);
        void write(String feature, BuildId build, int revision, String candidateId);
        void remove(String feature, BuildId build, int revision);
    }
    public static final class Selection<P> {
        public final P profile;
        public final String id;
        public final MatchKind kind;
        private Selection(Candidate<P> candidate, MatchKind kind) {
            profile = candidate == null ? null : candidate.profile;
            id = candidate == null ? null : candidate.id;
            this.kind = kind;
        }
    }
    private final int revision;
    private final Map<String, Selection<?>> results = new HashMap<>();
    public WeChatProfileSelector(int adapterRevision) { revision = adapterRevision; }

    @SuppressWarnings("unchecked")
    public synchronized <P> Selection<P> select(String feature, BuildId build, List<Candidate<P>> candidates,
            Probe<P> probe, Cache cache) {
        String key = cacheKey(feature, build, revision);
        Selection<?> previous = results.get(key);
        if (previous != null) return (Selection<P>) previous;
        Selection<P> result = choose(feature, build, candidates, probe, cache);
        results.put(key, result);
        return result;
    }
    private <P> Selection<P> choose(String feature, BuildId build, List<Candidate<P>> candidates,
            Probe<P> probe, Cache cache) {
        for (Candidate<P> candidate : candidates)
            if (candidate.versionName.equals(build.versionName) && candidate.verifiedCode == build.versionCode) {
                boolean accepted = valid(probe, candidate);
                return new Selection<>(accepted ? candidate : null, accepted ? MatchKind.EXACT : MatchKind.NO_MATCH);
            }
        String cached = null;
        try { cached = cache.read(feature, build, revision); } catch (RuntimeException ignored) { }
        if (cached != null) {
            for (Candidate<P> candidate : candidates)
                if (candidate.id.equals(cached) && candidate.versionName.equals(build.versionName)
                        && valid(probe, candidate)) {
                    diagnostic("cache_revalidated", feature);
                    return new Selection<>(candidate, MatchKind.EXPERIMENTAL);
                }
            try { cache.remove(feature, build, revision); } catch (RuntimeException ignored) { }
        }
        Candidate<P> matched = null;
        for (Candidate<P> candidate : candidates) {
            if (!candidate.versionName.equals(build.versionName) || !valid(probe, candidate)) continue;
            if (matched != null) return new Selection<>(null, MatchKind.AMBIGUOUS);
            matched = candidate;
        }
        if (matched == null) return new Selection<>(null, MatchKind.NO_MATCH);
        try {
            cache.write(feature, build, revision, matched.id);
            diagnostic("candidate_persisted", feature);
        } catch (RuntimeException ignored) { }
        return new Selection<>(matched, MatchKind.EXPERIMENTAL);
    }
    private static <P> boolean valid(Probe<P> probe, Candidate<P> candidate) {
        try { return probe.valid(candidate.profile); } catch (Throwable ignored) { return false; }
    }
    private static void diagnostic(String stage, String feature) {
        try { android.util.Log.i("WeChat.ProfileCache", "stage=" + stage + " feature=" + feature); }
        catch (RuntimeException ignored) { }
    }
    static String cacheKey(String feature, BuildId build, int revision) {
        String name = build.versionName;
        return feature.length() + ":" + feature + ":" + (name == null ? -1 : name.length()) + ":" + name
                + ":" + build.versionCode + ":" + build.lastUpdateTime + ":" + revision;
    }
}
