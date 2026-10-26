package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.*;

/** Exercises the production constructor resolver without an Android runtime. */
public class ImagePreviewRemoteViewsTest {
    @Test public void createsLayoutWhenHostCannotQueryModulePackage() throws Throwable {
        ModuleInfo info = new ModuleInfo();
        LegacyViews views = create(LegacyViews.class, info, 123);
        assertSame(info, views.info);
        assertEquals(123, views.layout);
    }

    @Test public void acceptsPublicApplicationInfoConstructor() throws Throwable {
        ModuleInfo info = new ModuleInfo();
        PublicViews views = create(PublicViews.class, info, 456);
        assertSame(info, views.info);
        assertEquals(456, views.layout);
    }

    @Test public void missingExactSignatureDoesNotFallBackToPackageLookup() {
        assertThrows(NoSuchMethodException.class, () -> create(PackageOnlyViews.class, new ModuleInfo(), 123));
        assertThrows(NoSuchMethodException.class, () -> create(WrongInfoViews.class, new ModuleInfo(), 123));
    }

    @Test public void constructorFailureDoesNotFallBackToPackageLookup() {
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> create(BrokenViews.class, new ModuleInfo(), 123));
        assertTrue(failure.getCause() instanceof UnsupportedOperationException);
        assertEquals("synthetic constructor failure", failure.getCause().getMessage());
    }

    @Test public void missingModuleInfoFailsBeforeConstruction() {
        assertThrows(NullPointerException.class, () -> create(LegacyViews.class, null, 123));
    }

    private static <T> T create(Class<T> type, ModuleInfo info, int layout) throws Throwable {
        return ImagePreviewRemoteViews.create(type, ModuleInfo.class, info, layout);
    }

    static final class ModuleInfo {}

    static class LegacyViews {
        final ModuleInfo info;
        final int layout;
        protected LegacyViews(ModuleInfo info, int layout) { this.info = info; this.layout = layout; }
        public LegacyViews(String hiddenPackage, int layout) {
            throw new IllegalArgumentException("No such package synthetic.hidden.module");
        }
    }

    static final class PublicViews extends LegacyViews {
        public PublicViews(ModuleInfo info, int layout) { super(info, layout); }
    }

    static final class PackageOnlyViews {
        public PackageOnlyViews(String hiddenPackage, int layout) {
            throw new AssertionError("Package lookup must not be retried");
        }
    }

    static final class WrongInfoViews {
        public WrongInfoViews(Object info, int layout) {}
    }

    static final class BrokenViews {
        protected BrokenViews(ModuleInfo info, int layout) {
            throw new UnsupportedOperationException("synthetic constructor failure");
        }
        public BrokenViews(String hiddenPackage, int layout) {
            throw new AssertionError("Package lookup must not be retried");
        }
    }
}
