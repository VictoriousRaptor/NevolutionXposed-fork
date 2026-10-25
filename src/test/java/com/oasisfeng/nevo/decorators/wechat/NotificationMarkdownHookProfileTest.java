package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class NotificationMarkdownHookProfileTest {
    @Test public void resolvesTheAndroid16CleanupChain() throws Exception {
        NotificationMarkdownHookProfile profile = resolve(LegacyMessage.class, LegacyStyle.class);
        assertEquals(CharSequence.class, profile.cleanup.getReturnType());
        assertArrayEquals(new Class<?>[]{CharSequence.class}, profile.cleanup.getParameterTypes());
        assertEquals(4, profile.callers.length);
        assertNull(profile.textField);
    }

    @Test public void resolvesTheAndroid17CleanupChainAndWritableText() throws Exception {
        NotificationMarkdownHookProfile profile = resolve(CurrentMessage.class, CurrentStyle.class);
        assertEquals(void.class, profile.cleanup.getReturnType());
        assertEquals(0, profile.cleanup.getParameterCount());
        assertEquals(3, profile.callers.length);
        assertArrayEquals(new Class<?>[]{List.class, boolean.class}, profile.callers[0].getParameterTypes());
        CurrentMessage message = new CurrentMessage();
        profile.textField.set(message, "restored code");
        assertEquals("restored code", message.mText);
    }

    @Test public void rejectsMixedGenerationCallers() {
        assertThrows(NoSuchMethodException.class, () -> resolve(CurrentMessage.class, LegacyStyle.class));
        assertThrows(NoSuchMethodException.class, () -> resolve(LegacyMessage.class, CurrentStyle.class));
    }

    @Test public void rejectsAStaticCleanup() {
        assertThrows(NoSuchMethodException.class, () -> resolve(StaticCleanup.class, CurrentStyle.class));
    }

    @Test public void rejectsAnUnexpectedCleanupReturnType() {
        assertThrows(NoSuchMethodException.class, () -> resolve(WrongReturn.class, CurrentStyle.class));
    }

    @Test public void requiresAnInstanceCharSequenceTextField() {
        assertThrows(ReflectiveOperationException.class, () -> resolve(MissingText.class, CurrentStyle.class));
        assertThrows(ReflectiveOperationException.class, () -> resolve(WrongText.class, CurrentStyle.class));
        assertThrows(ReflectiveOperationException.class, () -> resolve(StaticText.class, CurrentStyle.class));
        assertThrows(ReflectiveOperationException.class, () -> resolve(FinalText.class, CurrentStyle.class));
    }

    @Test public void rejectsInstanceBundleSerialization() {
        assertThrows(NoSuchMethodException.class, () -> resolve(CurrentMessage.class, InstanceSerializer.class));
    }

    @Test public void rejectsUnexpectedCallerReturns() {
        assertThrows(NoSuchMethodException.class, () -> resolve(CurrentMessage.class, WrongView.class));
    }

    private static NotificationMarkdownHookProfile resolve(Class<?> message, Class<?> style) throws Exception {
        return NotificationMarkdownHookProfile.resolve(message, style, Extras.class, Views.class);
    }

    private static class Extras {}
    private static class Views {}
    private static class LegacyMessage {
        private CharSequence stripNonStyleSpans(CharSequence text) { return text; }
        public void ensureColorContrastOrStripStyling(int background) {}
    }
    private static class LegacyStyle {
        private static Extras[] getBundleArrayForMessages(List<?> messages, boolean process, int background) { return null; }
        public void addExtras(Extras extras, boolean process, int background) {}
        private Views makeMessagingView(int type) { return null; }
    }
    private static class CurrentMessage {
        private CharSequence mText;
        private void stripNonStyleSpans() {}
    }
    private static class CurrentStyle {
        private static Extras[] getBundleArrayForMessages(List<?> messages, boolean process) { return null; }
        public void addExtras(Extras extras, boolean process) {}
        private Views makeMessagingView(int type) { return null; }
    }
    private static class StaticCleanup { private static void stripNonStyleSpans() {} }
    private static class WrongReturn { private CharSequence stripNonStyleSpans() { return null; } }
    private static class MissingText { private void stripNonStyleSpans() {} }
    private static class WrongText { private String mText; private void stripNonStyleSpans() {} }
    private static class StaticText { private static CharSequence mText; private void stripNonStyleSpans() {} }
    private static class FinalText { private final CharSequence mText = "source"; private void stripNonStyleSpans() {} }
    private static class InstanceSerializer {
        private Extras[] getBundleArrayForMessages(List<?> messages, boolean process) { return null; }
        public void addExtras(Extras extras, boolean process) {}
        private Views makeMessagingView(int type) { return null; }
    }
    private static class WrongView {
        private static Extras[] getBundleArrayForMessages(List<?> messages, boolean process) { return null; }
        public void addExtras(Extras extras, boolean process) {}
        private Object makeMessagingView(int type) { return null; }
    }
}
