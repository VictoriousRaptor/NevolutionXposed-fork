package com.oasisfeng.nevo.decorators.wechat;

import android.content.SharedPreferences;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class WeChatProfileCacheTest {
    @Test public void newProcessSelectionRevalidatesStoredCandidateAndSeparatesBuilds() {
        FakePreferences store = new FakePreferences();
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.79", 3183, 100);
        WeChatProfileSelector.Candidate<String> candidate = new WeChatProfileSelector.Candidate<>(
                "wechat-8.0.79/3182", "8.0.79", 3182, "profile");
        WeChatProfileSelector.Candidate<String> other = new WeChatProfileSelector.Candidate<>(
                "wechat-8.0.79/other", "8.0.79", 3180, "other");
        WeChatProfileCache first = new WeChatProfileCache(store.preferences());
        assertEquals(WeChatProfileSelector.MatchKind.EXPERIMENTAL, new WeChatProfileSelector(1).select(
                "reply", build, Arrays.asList(candidate, other), p -> p.equals("profile"), first).kind);
        assertEquals(candidate.id, first.read("reply", build, 1));

        int[] probes = { 0 };
        WeChatProfileCache next = new WeChatProfileCache(store.preferences());
        assertEquals(candidate.id, new WeChatProfileSelector(1).select("reply", build,
                Arrays.asList(candidate, other), p -> { probes[0]++; return true; }, next).id);
        assertEquals(1, probes[0]);
        assertEquals(WeChatProfileSelector.MatchKind.AMBIGUOUS, new WeChatProfileSelector(1).select(
                "reply", new WeChatProfileSelector.BuildId("8.0.79", 3183, 101),
                Arrays.asList(candidate, other), p -> true, next).kind);
        assertNull(next.read("reply", build, 2));
    }

    @Test public void failedCommitDoesNotClaimPersistence() {
        FakePreferences store = new FakePreferences();
        store.commitResult = false;
        WeChatProfileCache cache = new WeChatProfileCache(store.preferences());
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.79", 3183, 100);
        assertThrows(IllegalStateException.class, () -> cache.write("reply", build, 1, "wechat-8.0.79/3182"));
        assertEquals(WeChatProfileSelector.MatchKind.EXPERIMENTAL, new WeChatProfileSelector(1).select(
                "reply", build, Arrays.asList(new WeChatProfileSelector.Candidate<>(
                        "wechat-8.0.79/3182", "8.0.79", 3182, "profile")), p -> true, cache).kind);
    }

    private static final class FakePreferences {
        final Map<String, String> values = new HashMap<>();
        boolean commitResult = true;

        SharedPreferences preferences() {
            return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { SharedPreferences.class }, (proxy, method, args) -> {
                        if (method.getName().equals("getString")) return values.getOrDefault(args[0], (String) args[1]);
                        if (method.getName().equals("edit")) return editor();
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        SharedPreferences.Editor editor() {
            Map<String, String> changes = new HashMap<>();
            return (SharedPreferences.Editor) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { SharedPreferences.Editor.class }, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "putString": changes.put((String) args[0], (String) args[1]); return proxy;
                            case "remove": changes.put((String) args[0], null); return proxy;
                            case "commit":
                                if (commitResult) for (Map.Entry<String, String> entry : changes.entrySet()) {
                                    if (entry.getValue() == null) values.remove(entry.getKey());
                                    else values.put(entry.getKey(), entry.getValue());
                                }
                                return commitResult;
                            default: throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
}
