package com.oasisfeng.nevo.decorators.wechat;
import org.junit.Test;
import static org.junit.Assert.*;
public class WeChatImageProfileProbeTest {
    @Test public void build3200ResolvesCompleteImageEvents() throws Exception {
        WeChatImageProfile profile = WeChatImageProfile.forVersion("8.0.79", 3200);
        assertEquals("sa0.l", profile.flow);
        WeChatImageEvents.Resolved resolved = WeChatImageEvents.resolve(profile,
                name -> eventClassFor(profile, name));
        assertNotNull(resolved.initial);
        java.util.List<java.lang.reflect.Method> hooked = new java.util.ArrayList<>();
        WeChatImageEvents events = new WeChatImageEvents();
        assertTrue(events.installResolved(resolved, (method, callback) -> hooked.add(method)));
        assertEquals(5, hooked.size());
        assertTrue(hooked.contains(resolved.serverId));
        assertTrue(hooked.contains(resolved.pathMethod));
        assertNotNull(WeChatImageDownloader.resolve(profile, name -> downloadClassFor(profile, name)));
    }
    @Test public void independentSameNameChoicesAndExactPriority() {
        WeChatProfileSelector selector = new WeChatProfileSelector(1);
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.79",3183,100);
        assertEquals(WeChatProfileSelector.MatchKind.EXACT,
            selector.select("image_events",build,WeChatImageProfile.CANDIDATES,p -> true,null).kind);
        assertEquals(WeChatProfileSelector.MatchKind.NO_MATCH,
            selector.select("image_download",build,WeChatImageProfile.CANDIDATES,p -> false,null).kind);
        assertEquals(WeChatProfileSelector.MatchKind.AMBIGUOUS,
            selector.select("image_events",new WeChatProfileSelector.BuildId("8.0.77",9999,100),WeChatImageProfile.CANDIDATES,p -> true,null).kind);
        assertEquals("8.0.77/3160", selector.select("image_events",
            new WeChatProfileSelector.BuildId("8.0.77",3160,100),WeChatImageProfile.CANDIDATES,p -> true,null).profile.label);
    }
    @Test public void missingEventAndDownloadDescriptorsReject() {
        WeChatImageProfile profile = WeChatImageProfile.forVersion("8.0.79",3182);
        assertThrows(NoSuchMethodError.class, () -> WeChatImageEvents.resolve(profile,name -> Object.class));
        assertThrows(NoSuchMethodError.class, () -> WeChatImageDownloader.resolve(profile,name -> Object.class));
    }


    @Test public void actual3183DescriptorsSelectEachComponentIndependently() {
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.79",3183,100);
        WeChatProfileSelector selector = new WeChatProfileSelector(1);
        assertEquals(WeChatProfileSelector.MatchKind.EXACT, selector.select("image_events",build,
            WeChatImageProfile.CANDIDATES,p -> WeChatImageEvents.resolve(p,WeChatImageProfileProbeTest::eventClass) != null,null).kind);
        assertEquals(WeChatProfileSelector.MatchKind.EXACT, selector.select("image_download",build,
            WeChatImageProfile.CANDIDATES,p -> WeChatImageDownloader.resolve(p,WeChatImageProfileProbeTest::downloadClass) != null,null).kind);
        assertEquals(WeChatProfileSelector.MatchKind.NO_MATCH, new WeChatProfileSelector(1).select("image_download",build,
            WeChatImageProfile.CANDIDATES,p -> WeChatImageDownloader.resolve(p,name -> name.equals(profile().download)
                ? BadDownload.class : downloadClass(name)) != null,null).kind);
    }

    @Test public void full77DescriptorsMustHaveUniqueComponentMatch() {
        WeChatProfileSelector.BuildId build = new WeChatProfileSelector.BuildId("8.0.77",9999,100);
        assertEquals(WeChatProfileSelector.MatchKind.AMBIGUOUS, new WeChatProfileSelector(1).select("image_events",build,
            WeChatImageProfile.CANDIDATES,p -> WeChatImageEvents.resolve(p,n -> eventClassFor(p,n)) != null,null).kind);
        assertEquals(WeChatProfileSelector.MatchKind.AMBIGUOUS, new WeChatProfileSelector(1).select("image_download",build,
            WeChatImageProfile.CANDIDATES,p -> WeChatImageDownloader.resolve(p,n -> downloadClassFor(p,n)) != null,null).kind);
        assertEquals("8.0.77/3160", new WeChatProfileSelector(1).select("image_events",build,
            WeChatImageProfile.CANDIDATES,p -> !p.label.endsWith("3141") && WeChatImageEvents.resolve(p,n -> eventClassFor(p,n)) != null,null).profile.label);
        assertEquals("8.0.77/3141", new WeChatProfileSelector(1).select("image_download",build,
            WeChatImageProfile.CANDIDATES,p -> !p.label.endsWith("3160") && WeChatImageDownloader.resolve(p,n -> downloadClassFor(p,n)) != null,null).profile.label);
    }
    static WeChatImageProfile profile() { return WeChatImageProfile.forVersion("8.0.79",3182); }
    static Class<?> eventClass(String name) throws ClassNotFoundException {
        return eventClassFor(profile(),name);
    }
    static Class<?> eventClassFor(WeChatImageProfile p,String name) throws ClassNotFoundException {
        if (name.equals(p.state)) return State.class;
        if (name.equals(p.simple)) return Simple.class;
        if (name.equals(p.message)) return Message.class;
        if (name.equals(p.flow)) return Flow.class;
        if (name.equals(p.result)) return Result.class;
        if (name.equals(p.remote)) return Remote.class;
        if (name.equals(p.local)) return Local.class;
        if (name.equals(p.pathOwner)) return Paths.class;
        if (name.equals(p.vfs)) return Vfs.class;
        throw new ClassNotFoundException(name);
    }
    @Test public void eventFullProbeAndBadStateGetter() throws Exception {
        assertNotNull(WeChatImageEvents.resolve(profile(), WeChatImageProfileProbeTest::eventClass));
        assertThrows(IllegalStateException.class, () -> WeChatImageEvents.resolve(profile(),
            name -> name.equals(profile().state) ? BadState.class : eventClass(name)));
    }
    @Test public void partialHookFailureStaysUnavailableAcrossRetry() throws Throwable {
        WeChatImageEvents events = new WeChatImageEvents();
        WeChatImageEvents.Resolved r = WeChatImageEvents.resolve(profile(),WeChatImageProfileProbeTest::eventClass);
        java.util.List<com.oasisfeng.nevo.xposed.compat.XC_MethodHook> callbacks = new java.util.ArrayList<>();
        assertFalse(events.installResolved(r,(method,callback) -> {
            callbacks.add(callback);
            if (callbacks.size() == 2) throw new IllegalStateException("synthetic hook failure");
        }));
        assertFalse(events.isAvailable());
        assertTrue(events.installResolved(r,(method,callback) -> {}));
        // A failed installation's callbacks must remain inert even after a later successful retry.
        java.lang.reflect.Method after = callbacks.get(0).getClass().getDeclaredMethod("afterHookedMethod",
            com.oasisfeng.nevo.xposed.compat.XC_MethodHook.MethodHookParam.class);
        after.setAccessible(true);
        after.invoke(callbacks.get(0),new Object[]{null});
    }
    public static class State { public Object d(String key) { throw new AssertionError(); } public Object f(String key) { throw new AssertionError("probe invoked state"); } }
    public static class BadState { public String f(String key) { return "wrong"; } }
    public static class SimpleBase { public int d; }
    public static class Simple extends SimpleBase {
        public String getString(int at) { throw new AssertionError(); }
        public long getLong(int at) { throw new AssertionError(); }
        public int getInteger(int at) { throw new AssertionError(); }
    }
    public static class MessageBase { public long field_msgSvrId; }
    public static class Message extends MessageBase {
        public String Q0() { throw new AssertionError(); }
        public int A0() { throw new AssertionError(); }
        public long K0() { throw new AssertionError(); }
        public long J0() { throw new AssertionError(); }
        public String N0() { throw new AssertionError(); }
        public int C0() { throw new AssertionError(); }
        public long I0() { throw new AssertionError(); }
        public int z0() { throw new AssertionError(); }
        public long H0() { throw new AssertionError(); }
        public long getMsgId() { throw new AssertionError(); }
        public long getCreateTime() { throw new AssertionError(); }
        public int getType() { throw new AssertionError(); }
    }
    public static class Result {} public static class Remote {} public static class Local {}
    public static class Flow {
        public Result l(State state) { throw new AssertionError(); }
        public Result handleDataFromRemote(State state,Remote remote) { throw new AssertionError(); }
        public Result handleDataFromFile(State state,Local local) { throw new AssertionError(); }
    }
    public static class Paths { public String N2(Message msg,String path,boolean flag) { throw new AssertionError(); } public String d3(Message msg,String path,boolean flag) { throw new AssertionError(); } public String I3(Message msg,String path,boolean flag) { throw new AssertionError(); } public String s4(Message msg,String path,boolean flag) { throw new AssertionError(); } }
    public static class Vfs { public static String i(String path,boolean flag) { throw new AssertionError(); } }

    static Class<?> downloadClass(String name) throws ClassNotFoundException {
        return downloadClassFor(profile(),name);
    }
    static Class<?> downloadClassFor(WeChatImageProfile p,String name) throws ClassNotFoundException {
        if (name.equals(p.kernel)) return Kernel.class;
        if (name.equals(p.core)) return Core.class;
        if (name.equals(p.database)) return Database.class;
        if (name.equals(p.manager)) return Manager.class;
        if (name.equals(p.serviceBase)) return ServiceBase.class;
        if (name.equals(p.service)) return ImageService.class;
        if (name.equals(p.implementation)) return ServiceImpl.class;
        if (name.equals(p.downloadInterface)) return DownloadService.class;
        if (name.equals(p.download)) return DownloadImpl.class;
        if (name.equals(p.callback)) return Callback.class;
        if (name.equals("com.tencent.mm.plugin.msg.MsgIdTalker")) return Pair.class;
        if (name.equals("com.tencent.mm.plugin.gif.MMWXGFJNI")) return Decoder.class;
        throw new ClassNotFoundException(name);
    }
    @Test public void downloadProbeNeverInvokesQueryOrDownloadAndRejectsBadMethod() throws Exception {
        assertNotNull(WeChatImageDownloader.resolve(profile(),WeChatImageProfileProbeTest::downloadClass));
        assertThrows(IllegalStateException.class, () -> WeChatImageDownloader.resolve(profile(),
            name -> name.equals(profile().download) ? BadDownload.class : downloadClass(name)));
    }
    @Test public void downloaderRejectsInvalidInheritanceAndStaticQuery() {
        assertThrows(IllegalStateException.class, () -> WeChatImageDownloader.resolve(profile(),
            name -> name.equals(profile().implementation) ? UnrelatedService.class : downloadClass(name)));
        assertThrows(IllegalStateException.class, () -> WeChatImageDownloader.resolve(profile(),
            name -> name.equals(profile().database) ? StaticDatabase.class : downloadClass(name)));
    }
    public static class Kernel { public static Core v() { throw new AssertionError(); } public static Core x() { throw new AssertionError("probe called service"); } }
    public static class Core { public Database database; }
    // The APK declares the database field as an interface with an abstract query method.
    public interface Database { android.database.Cursor f(String sql,String[] args,int flags); }
    @Test public void abstractDatabaseQueryDispatchesToConcreteReceiver() throws Exception {
        for (long code : new long[]{3183, 3200}) {
            WeChatImageProfile p = WeChatImageProfile.forVersion("8.0.79", code);
            WeChatImageDownloader.Resolved resolved = WeChatImageDownloader.resolve(p,
                    name -> downloadClassFor(p, name));
            assertTrue(java.lang.reflect.Modifier.isAbstract(resolved.databaseQuery.getModifiers()));
            int[] calls = {0};
            Database receiver = (sql, args, flags) -> { calls[0]++; return null; };
            assertNull(resolved.databaseQuery.invoke(receiver, "SELECT 1", new String[0], 0));
            assertEquals(1, calls[0]);
        }
    }
    public static class StaticDatabase {
        public static android.database.Cursor f(String sql,String[] args,int flags) { throw new AssertionError(); }
    }
    public interface ServiceBase {} public interface ImageService extends ServiceBase {}
    public static class Manager { public static ServiceBase c(Class<?> type) { throw new AssertionError(); } }
    public static class ServiceImpl implements ImageService { public DownloadService Wi() { throw new AssertionError(); } public DownloadService ej() { throw new AssertionError(); } public DownloadService Bj() { throw new AssertionError(); } }
    public static class UnrelatedService { public DownloadService Bj() { throw new AssertionError(); } }
    public interface DownloadService {} public interface Callback {}
    public static class DownloadImpl implements DownloadService {
        public int b(long id,Pair pair,int type,Object data,int flag,Callback callback,int priority,boolean original) {
            throw new AssertionError("probe downloaded image");
        }
    }
    public static class BadDownload implements DownloadService {
        public long b(long id,Pair pair,int type,Object data,int flag,Callback callback,int priority,boolean original) { return 0; }
    }
    @Test public void abstractDownloadImplementationIsRejected() {
        assertThrows(IllegalStateException.class, () -> WeChatImageDownloader.resolve(profile(),
                name -> name.equals(profile().download) ? AbstractDownload.class : downloadClass(name)));
    }
    public abstract static class AbstractDownload implements DownloadService {
        public abstract int b(long id, Pair pair, int type, Object data, int flag, Callback callback,
                              int priority, boolean original);
    }
    public static class Pair { public Pair(long id,String talker) { throw new AssertionError(); } }
    public static class Decoder { public static byte[] wxam2PicBuf(byte[] source,int flags,int format) { throw new AssertionError(); } }

    @Test public void downloaderRejectsUnrelatedServiceBaseDespiteMatchingGetter() {
        assertThrows(IllegalStateException.class, () -> WeChatImageDownloader.resolve(profile(), name -> {
            if (name.equals(profile().serviceBase)) return UnrelatedBase.class;
            if (name.equals(profile().manager)) return UnrelatedBaseManager.class;
            return downloadClass(name);
        }));
    }
    @Test public void downloaderRejectsAbstractMessagePairDespiteMatchingDownload() {
        assertThrows(IllegalStateException.class, () -> WeChatImageDownloader.resolve(profile(), name -> {
            if (name.equals("com.tencent.mm.plugin.msg.MsgIdTalker")) return AbstractPair.class;
            if (name.equals(profile().download)) return AbstractPairDownload.class;
            return downloadClass(name);
        }));
    }
    public interface UnrelatedBase {}
    public static class UnrelatedBaseManager {
        public static UnrelatedBase c(Class<?> type) { throw new AssertionError("probe invoked service getter"); }
    }
    public abstract static class AbstractPair {
        public AbstractPair(long id,String talker) { throw new AssertionError("probe invoked constructor"); }
    }
    public static class AbstractPairDownload implements DownloadService {
        public int b(long id,AbstractPair pair,int type,Object data,int flag,Callback callback,int priority,boolean original) {
            throw new AssertionError("probe downloaded image");
        }
    }
}
