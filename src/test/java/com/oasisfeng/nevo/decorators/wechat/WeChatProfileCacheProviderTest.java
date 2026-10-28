package com.oasisfeng.nevo.decorators.wechat;

import android.content.SharedPreferences;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class WeChatProfileCacheProviderTest {
    @Test public void callerMustBeModuleOrActualWeChatUid() {
        assertTrue(WeChatProfileCacheProvider.authorizedCaller(100, 100, null));
        assertTrue(WeChatProfileCacheProvider.authorizedCaller(200, 100, new String[] { "com.tencent.mm" }));
        assertFalse(WeChatProfileCacheProvider.authorizedCaller(201, 100, new String[] { "com.tencent.mm.fake" }));
        assertFalse(WeChatProfileCacheProvider.authorizedCaller(201, 100, null));
    }

    @Test public void protocolRejectsUnboundedOrMalformedFields() {
        assertTrue(WeChatProfileCacheProvider.validKey("reply", "8.0.79", 3183, 100, 1));
        assertFalse(WeChatProfileCacheProvider.validKey("reply;other", "8.0.79", 3183, 100, 1));
        assertFalse(WeChatProfileCacheProvider.validKey("reply", "8.0.79\nother", 3183, 100, 1));
        assertFalse(WeChatProfileCacheProvider.validKey("reply", "8.0.79", 3183, 100, -1));
        assertTrue(WeChatProfileCacheProvider.validCandidateId("wechat-8.0.79/3182"));
        assertFalse(WeChatProfileCacheProvider.validCandidateId("other\nentry"));
        assertFalse(WeChatProfileCacheProvider.validCandidateId("x".repeat(81)));
    }

    @Test public void freshSelectorAndCacheRevalidatePersistedCandidate() {
        FakePreferences store = new FakePreferences();
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.79", 3183, 100);
        WeChatProfileSelector.Candidate<String> candidate = new WeChatProfileSelector.Candidate<>(
                "wechat-8.0.79/3182", "8.0.79", 3182, "profile");
        WeChatProfileSelector.Candidate<String> other = new WeChatProfileSelector.Candidate<>(
                "wechat-8.0.79/other", "8.0.79", 3180, "other");
        assertEquals(WeChatProfileSelector.MatchKind.EXPERIMENTAL, new WeChatProfileSelector(1).select(
                "reply", build, Arrays.asList(candidate, other), p -> p.equals("profile"),
                new WeChatProfileCache(store.preferences())).kind);
        int[] calls = { 0 };
        assertEquals(WeChatProfileSelector.MatchKind.EXPERIMENTAL, new WeChatProfileSelector(1).select(
                "reply", build, Arrays.asList(candidate, other), p -> { calls[0]++; return true; },
                new WeChatProfileCache(store.preferences())).kind);
        assertEquals(1, calls[0]);
        assertEquals("wechat-8.0.79/3182", store.values.get(WeChatProfileSelector.cacheKey("reply", build, 1)));
        calls[0] = 0;
        assertEquals(WeChatProfileSelector.MatchKind.AMBIGUOUS, new WeChatProfileSelector(1).select(
                "reply", new WeChatProfileSelector.BuildId("8.0.79", 3183, 101),
                Arrays.asList(candidate, other), p -> { calls[0]++; return true; },
                new WeChatProfileCache(store.preferences())).kind);
        assertEquals(2, calls[0]);
    }

    @Test public void failedCommitDoesNotClaimPersistence() {
        FakePreferences store = new FakePreferences();
        store.commitResult = false;
        WeChatProfileCache cache = new WeChatProfileCache(store.preferences());
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.79", 3183, 100);
        assertThrows(IllegalStateException.class, () -> cache.write("reply", build, 1, "wechat-8.0.79/3182"));
        assertNull(cache.read("reply", build, 1));
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
            final Map<String, String> additions = new HashMap<>();
            return (SharedPreferences.Editor) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { SharedPreferences.Editor.class }, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "putString": additions.put((String) args[0], (String) args[1]); return proxy;
                            case "remove": additions.put((String) args[0], null); return proxy;
                            case "commit":
                                if (commitResult) for (Map.Entry<String, String> entry : additions.entrySet()) {
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
