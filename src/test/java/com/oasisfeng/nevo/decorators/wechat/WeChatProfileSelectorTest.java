package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.oasisfeng.nevo.decorators.wechat.WeChatProfileSelector.*;

public class WeChatProfileSelectorTest {
    private final MemoryCache cache = new MemoryCache();
    private final List<Candidate<String>> candidates = Arrays.asList(
            new Candidate<>("79", "8.0.79", 3182, "79"),
            new Candidate<>("77a", "8.0.77", 3141, "77a"),
            new Candidate<>("77b", "8.0.77", 3160, "77b"));
    private BuildId build(String name, long code) { return new BuildId(name, code, 100); }
    private WeChatProfileSelector selector() { return new WeChatProfileSelector(1); }
    @Test public void exactWinsDespiteOtherValidProfiles() {
        Selection<String> result = selector().select("reply", build("8.0.77", 3141), candidates, p -> true, cache);
        assertEquals(MatchKind.EXACT, result.kind); assertEquals("77a", result.profile);
        assertTrue(cache.ids.isEmpty());
    }
    @Test public void exactStillRequiresProbeAndDoesNotChooseOtherProfile() {
        assertEquals(MatchKind.NO_MATCH, selector().select("reply", build("8.0.77", 3141), candidates,
                p -> p.equals("77b"), cache).kind);
    }
    @Test public void unknown3183UsesUniqueSameNameProfile() {
        Selection<String> result = selector().select("reply", build("8.0.79", 3183), candidates, p -> true, cache);
        assertEquals(MatchKind.EXPERIMENTAL, result.kind); assertEquals("79", result.id);
    }
    @Test public void unknown77RequiresUniqueCompleteMatch() {
        assertEquals("77b", selector().select("reply", build("8.0.77", 9999), candidates,
                p -> p.equals("77b"), cache).profile);
    }
    @Test public void zeroAndAmbiguous77DoNotPersist() {
        assertEquals(MatchKind.NO_MATCH, selector().select("reply", build("8.0.77", 9999), candidates, p -> false, cache).kind);
        assertEquals(MatchKind.AMBIGUOUS, selector().select("reply", build("8.0.77", 9999), candidates, p -> true, cache).kind);
        assertTrue(cache.ids.isEmpty());
    }
    @Test public void crossedNameAndExceptionFailClosed() {
        int[] crossedProbes = {0};
        assertEquals(MatchKind.NO_MATCH, selector().select("reply", build("8.0.80", 3182), candidates,
                p -> { crossedProbes[0]++; return true; }, cache).kind);
        assertEquals(0, crossedProbes[0]);
        assertEquals(MatchKind.NO_MATCH, selector().select("reply", build("8.0.79", 3183), candidates,
                p -> { throw new LinkageError(); }, cache).kind);
    }
    @Test public void newSelectorRevalidatesPositiveCacheWithoutEnumeration() {
        BuildId b = build("8.0.77", 9999);
        selector().select("reply", b, candidates, p -> p.equals("77b"), cache);
        List<String> probed = new ArrayList<>();
        assertEquals("77b", selector().select("reply", b, candidates, p -> { probed.add(p); return true; }, cache).id);
        assertEquals(Collections.singletonList("77b"), probed);
    }
    @Test public void staleCacheReprobesAndReplaces() {
        BuildId b = build("8.0.77", 9999);
        selector().select("reply", b, candidates, p -> p.equals("77a"), cache);
        assertEquals("77b", selector().select("reply", b, candidates, p -> p.equals("77b"), cache).id);
        assertEquals("77b", cache.read("reply", b, 1));
    }
    @Test public void negativesMemoizedOnlyForCurrentProcess() {
        BuildId b = build("8.0.79", 3183); WeChatProfileSelector s = selector();
        s.select("reply", b, candidates, p -> false, cache);
        int[] repeatedProbes = {0};
        assertEquals(MatchKind.NO_MATCH, s.select("reply", b, candidates, p -> { repeatedProbes[0]++; return true; }, cache).kind);
        assertEquals(0, repeatedProbes[0]);
        assertEquals(MatchKind.EXPERIMENTAL, selector().select("reply", b, candidates, p -> true, cache).kind);
    }
    @Test public void identityIncludesFeatureLongCodeUpdateAndRevision() {
        BuildId b = build("8.0.79", 3183);
        selector().select("reply", b, candidates, p -> true, cache);
        for (BuildId changed : Arrays.asList(build("8.0.79", 3183L + (1L << 32)), new BuildId("8.0.79", 3183, 101)))
            assertEquals(MatchKind.NO_MATCH, selector().select("reply", changed, candidates, p -> false, cache).kind);
        assertEquals(MatchKind.NO_MATCH, new WeChatProfileSelector(2).select("reply", b, candidates, p -> false, cache).kind);
        assertEquals(MatchKind.NO_MATCH, selector().select("image", b, candidates, p -> false, cache).kind);
        assertEquals("79", cache.read("reply", b, 1));
    }
    private static class MemoryCache implements Cache {
        final Map<String, String> ids = new HashMap<>();
        private String key(String f, BuildId b, int r) { return WeChatProfileSelector.cacheKey(f, b, r); }
        public String read(String f, BuildId b, int r) { return ids.get(key(f,b,r)); }
        public void write(String f, BuildId b, int r, String id) { ids.put(key(f,b,r), id); }
        public void remove(String f, BuildId b, int r) { ids.remove(key(f,b,r)); }
    }
}
