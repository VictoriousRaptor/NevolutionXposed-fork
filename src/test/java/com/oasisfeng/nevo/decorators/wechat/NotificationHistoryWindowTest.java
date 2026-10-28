package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NotificationHistoryWindowTest {
    private static final class Event {
        final String name, conversation;
        final boolean normalized, chat;

        Event(String name, String conversation, boolean normalized, boolean chat) {
            this.name = name;
            this.conversation = conversation;
            this.normalized = normalized;
            this.chat = chat;
        }

        @Override public String toString() { return name; }
    }

    private static Event chat(String name) { return new Event(name, "peer", false, true); }
    private static Event snapshot(String name) { return new Event(name, "peer", true, true); }

    private static List<Event> prior(List<Event> archive, Event current) {
        return NotificationHistoryWindow.prior(archive, current,
                event -> event.normalized, event -> "peer".equals(event.conversation) && event.chat);
    }

    @Test public void keepsEveryRawChatTypeBetweenTextSnapshots() {
        Event a = snapshot("A text"), sticker = chat("B sticker"), image = chat("C image"),
                video = chat("D video"), current = chat("E text");
        assertEquals(Arrays.asList(a, sticker, image, video),
                prior(Arrays.asList(a, sticker, image, video, current), current));
    }

    @Test public void startsAtLatestNormalizedSnapshotAndSkipsOtherConversations() {
        Event old = snapshot("old"), media = chat("old media"), latest = snapshot("latest"),
                other = new Event("other", "another peer", false, true),
                misc = new Event("misc", "peer", false, false), current = chat("current");
        assertEquals(Arrays.asList(latest), prior(Arrays.asList(old, media, latest, other, misc, current), current));
    }

    @Test public void preservesRawOnlyConversationAndStopsAtCurrentGeneration() {
        Event first = chat("first"), current = chat("current"), later = chat("later");
        assertEquals(Arrays.asList(first), prior(Arrays.asList(first, current, later), current));
        assertEquals(Arrays.asList(first), prior(Arrays.asList(first), current));
        assertEquals(Arrays.asList(), prior(Arrays.asList(first, snapshot("done")), snapshot("new current")));
    }
}
