package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import static org.junit.Assert.*;

public class NotificationAppearanceHookProfileTest {
    @Test public void resolvesBothExpandedEntryPointNames() throws Exception {
        assertEquals("createBigContentView", header(LegacyBuilder.class).render.getName());
        assertEquals("createExpandedContentView", header(CurrentBuilder.class).render.getName());
    }

    @Test public void collapsedHeaderUsesOnlyTheCompactRenderer() throws Exception {
        assertEquals("createContentView", collapsed(CompactBuilder.class).render.getName());
        assertThrows(NoSuchMethodException.class, () -> collapsed(WrongCompactBuilder.class));
    }

    @Test public void compactHeadsUpIsASeparateZeroArgumentEntryPoint() throws Exception {
        NotificationAppearanceHookProfile.Header profile = compactHeadsUp(CompactHeadsUpBuilder.class);
        assertEquals("createCompactHeadsUpContentView", profile.render.getName());
        assertArrayEquals(new Class<?>[0], profile.render.getParameterTypes());
        assertThrows(NoSuchMethodException.class, () -> compactHeadsUp(CompactBuilder.class));
    }

    @Test public void compactHeadsUpRejectsWrongReturnStaticAndOverloadedMethods() {
        assertThrows(NoSuchMethodException.class, () -> compactHeadsUp(WrongHeadsUpReturn.class));
        assertThrows(NoSuchMethodException.class, () -> compactHeadsUp(StaticHeadsUp.class));
        assertThrows(NoSuchMethodException.class, () -> compactHeadsUp(OverloadedHeadsUp.class));
    }

    @Test public void compactHeadsUpRequiresExactInstanceBuilderFields() {
        assertThrows(NoSuchFieldException.class, () -> compactHeadsUp(WrongHeadsUpContext.class));
        assertThrows(NoSuchFieldException.class, () -> compactHeadsUp(StaticHeadsUpNotice.class));
    }

    @Test public void rejectsWrongModernSignatureRatherThanTryingALegacyMethod() {
        assertThrows(NoSuchMethodException.class, () -> header(WrongReturnBuilder.class));
        assertThrows(NoSuchMethodException.class, () -> header(StaticBuilder.class));
    }

    @Test public void requiresExactInstanceBuilderFields() {
        assertThrows(NoSuchFieldException.class, () -> header(WrongContextBuilder.class));
        assertThrows(NoSuchFieldException.class, () -> header(StaticNotificationBuilder.class));
    }

    @Test public void resolvesWritableAvatarFieldsAndExactRenderSignature() throws Exception {
        NotificationAppearanceHookProfile.Avatar profile = avatar(Style.class);
        assertArrayEquals(new Class<?>[]{int.class}, profile.render.getParameterTypes());
        Style style = new Style();
        Icon icon = new Icon();
        profile.shortcutIcon.set(style, icon);
        profile.conversationType.setInt(style, 1);
        assertSame(icon, style.mShortcutIcon);
        assertEquals(1, style.mConversationType);
    }

    @Test public void rejectsAnUnsupportedAvatarLayout() {
        assertThrows(NoSuchFieldException.class, () -> avatar(FinalIconStyle.class));
        assertThrows(NoSuchFieldException.class, () -> avatar(WrongConstantsStyle.class));
        assertThrows(NoSuchMethodException.class, () -> avatar(StaticRenderStyle.class));
    }

    @Test public void presentationOwnershipRequiresBothWeChatAndModuleMarker() {
        assertTrue(WeChatNotificationAppearance.owns("com.tencent.mm", "com.tencent.mm"));
        assertFalse(WeChatNotificationAppearance.owns("example.other", "com.tencent.mm"));
        assertFalse(WeChatNotificationAppearance.owns("com.tencent.mm", null));
        assertFalse(WeChatNotificationAppearance.owns(null, "com.tencent.mm"));
    }

    private static NotificationAppearanceHookProfile.Header header(Class<?> builder) throws Exception {
        return NotificationAppearanceHookProfile.header(builder, Context.class, Notice.class, Views.class);
    }

    private static NotificationAppearanceHookProfile.Header collapsed(Class<?> builder) throws Exception {
        return NotificationAppearanceHookProfile.collapsed(builder, Context.class, Notice.class, Views.class);
    }

    private static NotificationAppearanceHookProfile.Avatar avatar(Class<?> style) throws Exception {
        return NotificationAppearanceHookProfile.avatar(style, BaseStyle.class, LegacyBuilder.class,
                Context.class, Notice.class, Icon.class, Views.class);
    }

    private static NotificationAppearanceHookProfile.Header compactHeadsUp(Class<?> builder) throws Exception {
        return NotificationAppearanceHookProfile.compactHeadsUp(builder, Context.class, Notice.class, Views.class);
    }

    private static class Context {}
    private static class Notice {}
    private static class Views {}
    private static class Icon {}
    private static class LegacyBuilder {
        private Context mContext;
        private Notice mN;
        public Views createBigContentView() { return null; }
    }
    private static class CurrentBuilder {
        private Context mContext;
        private Notice mN;
        private Views createExpandedContentView() { return null; }
    }
    private static class CompactBuilder {
        private Context mContext;
        private Notice mN;
        private Views createContentView() { return null; }
    }
    private static class WrongCompactBuilder {
        private Context mContext;
        private Notice mN;
        private String createContentView() { return null; }
    }
    private static class CompactHeadsUpBuilder {
        private Context mContext;
        private Notice mN;
        private Views createCompactHeadsUpContentView() { return null; }
    }
    private static class WrongHeadsUpReturn extends CompactHeadsUpBuilder {
        private String createCompactHeadsUpContentView() { return null; }
    }
    private static class StaticHeadsUp extends CompactHeadsUpBuilder {
        private static Views createCompactHeadsUpContentView() { return null; }
    }
    private static class OverloadedHeadsUp extends CompactHeadsUpBuilder {
        private Views createCompactHeadsUpContentView(boolean increasedHeight) { return null; }
    }
    private static class WrongHeadsUpContext {
        private String mContext;
        private Notice mN;
        private Views createCompactHeadsUpContentView() { return null; }
    }
    private static class StaticHeadsUpNotice {
        private Context mContext;
        private static Notice mN;
        private Views createCompactHeadsUpContentView() { return null; }
    }
    private static class WrongReturnBuilder {
        private String createExpandedContentView() { return null; }
        public Views createBigContentView() { return null; }
    }
    private static class StaticBuilder { private static Views createExpandedContentView() { return null; } }
    private static class WrongContextBuilder {
        private String mContext;
        private Notice mN;
        private Views createExpandedContentView() { return null; }
    }
    private static class StaticNotificationBuilder {
        private Context mContext;
        private static Notice mN;
        private Views createExpandedContentView() { return null; }
    }
    private static class BaseStyle { protected LegacyBuilder mBuilder; }
    private static class Style extends BaseStyle {
        private static final int CONVERSATION_TYPE_LEGACY = 0, CONVERSATION_TYPE_NORMAL = 1;
        private int mConversationType;
        private Icon mShortcutIcon;
        private Views makeMessagingView(int type) { return null; }
    }
    private static class FinalIconStyle extends BaseStyle {
        private static final int CONVERSATION_TYPE_LEGACY = 0, CONVERSATION_TYPE_NORMAL = 1;
        private int mConversationType;
        private final Icon mShortcutIcon = null;
        private Views makeMessagingView(int type) { return null; }
    }
    private static class WrongConstantsStyle extends BaseStyle {
        private static final int CONVERSATION_TYPE_LEGACY = 0, CONVERSATION_TYPE_NORMAL = 4;
    }
    private static class StaticRenderStyle extends BaseStyle {
        private static final int CONVERSATION_TYPE_LEGACY = 0, CONVERSATION_TYPE_NORMAL = 1;
        private static Views makeMessagingView(int type) { return null; }
    }
}
