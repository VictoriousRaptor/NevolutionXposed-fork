package com.oasisfeng.nevo.decorators.wechat;

import android.content.SharedPreferences;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test public void clientRoundTripsThroughProviderProtocol() {
        assertEquals("com.oasisfeng.nevo.xposed.profilecache", WeChatProfileCacheProvider.AUTHORITY);
        FakePreferences store = new FakePreferences();
        WeChatProfileCache backing = new WeChatProfileCache(store.preferences());
        AtomicReference<Map<String, Object>> written = new AtomicReference<>();
        List<String> operations = new ArrayList<>();
        WeChatProfileCacheClient client = new WeChatProfileCacheClient((method, request) -> {
            operations.add(method);
            if (method.equals("write")) written.set(new HashMap<>(request));
            return WeChatProfileCacheProvider.execute(method, request, backing);
        });
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.79", 3183, 100);
        client.write("reply", build, 1, "wechat-8.0.79/3182");
        assertEquals("reply", written.get().get("feature"));
        assertEquals("8.0.79", written.get().get("versionName"));
        assertEquals(3183L, written.get().get("versionCode"));
        assertEquals(100L, written.get().get("updateTime"));
        assertEquals(1, written.get().get("revision"));
        assertEquals("wechat-8.0.79/3182", written.get().get("candidateId"));
        assertEquals("wechat-8.0.79/3182", client.read("reply", build, 1));
        client.remove("reply", build, 1);
        assertNull(client.read("reply", build, 1));
        assertEquals(Arrays.asList("write", "read", "remove", "read"), operations);

        assertThrows(IllegalArgumentException.class,
                () -> WeChatProfileCacheProvider.execute("unknown", written.get(), backing));
        Map<String, Object> extra = new HashMap<>(written.get()); extra.put("extra", "x");
        assertThrows(IllegalArgumentException.class,
                () -> WeChatProfileCacheProvider.execute("write", extra, backing));
        Map<String, Object> wrongType = new HashMap<>(written.get()); wrongType.put("versionCode", "3183");
        assertThrows(IllegalArgumentException.class,
                () -> WeChatProfileCacheProvider.execute("write", wrongType, backing));
    }

    @Test public void failedProviderCommitAndIpcStillAllowLiveProbe() {
        FakePreferences store = new FakePreferences(); store.commitResult = false;
        WeChatProfileCache backing = new WeChatProfileCache(store.preferences());
        WeChatProfileCacheClient failedCommit = new WeChatProfileCacheClient(
                (method, request) -> WeChatProfileCacheProvider.execute(method, request, backing));
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.79", 3183, 100);
        assertThrows(IllegalStateException.class,
                () -> failedCommit.write("reply", build, 1, "wechat-8.0.79/3182"));
        assertNull(backing.read("reply", build, 1));
        WeChatProfileCacheClient unavailable = new WeChatProfileCacheClient((method, request) -> {
            throw new IllegalStateException("synthetic IPC failure");
        });
        assertEquals(WeChatProfileSelector.MatchKind.EXPERIMENTAL, new WeChatProfileSelector(1).select(
                "reply", build, Arrays.asList(new WeChatProfileSelector.Candidate<>(
                        "wechat-8.0.79/3182", "8.0.79", 3182, "profile")), p -> true, unavailable).kind);
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
