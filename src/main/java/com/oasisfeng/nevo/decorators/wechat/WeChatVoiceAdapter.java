package com.oasisfeng.nevo.decorators.wechat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook;
import com.oasisfeng.nevo.xposed.compat.XposedBridge;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

/** Exact-build native voice adapter. Observing identity never starts transcription. */
final class WeChatVoiceAdapter {
    private static final int VOICE_STATUS_RECEIVED = 99;

    record Profile(String account, String voice, String payload, String cacheGet,
                   String serverGet, String senderGet, String fileGet, String registry, String media,
                   String mediaContract, String resource, Layout layout) {}

    record Layout(String message, String talker, String cacheStore, String record, String cacheFactory,
                  String voiceStore, String voiceInfo, String voiceFactory, String path) {}

    private static final Layout WX79 = new Layout("e9", "N0", "kb", "jb", "zj", "m1", "c1", "Bj", "bk");
    private static final Layout WX80 = new Layout("f9", "P0", "lb", "kb", "Ij", "n1", "d1", "Kj", "kk");

    static Profile profile(String name, long code) {
        if ("8.0.80".equals(name) && code == 3200)
            return new Profile("c61.y1", "w81.w0", "in", "r1", "I0", "D0", "C0",
                    "kj5", "pd0.b", "jq3.u0", "pw5", WX80);
        if (!"8.0.79".equals(name)) return null;
        if (code == 3183) return new Profile("l51.z1", "f81.v0", "en", "m1", "I0", "C0", "A0",
                "kj5", "ed0.b", "kp3.u0", "nw5", WX79);
        if (code == 3200) return new Profile("l41.y1", "f71.v0", "fm", "P1", "H0", "z0", "u0",
                "ni5", "ec0.b", "no3.u0", "pv5", WX79);
        return null;
    }

    private final LinkedHashMap<VoiceTranscriptionQueue.Identity, Capture> captures = new LinkedHashMap<>();
    private volatile Resolved api;
    private final ThreadLocal<Boolean> observing = ThreadLocal.withInitial(() -> false);
    private io.github.libxposed.api.XposedInterface.HookHandle handle;

    synchronized void install(Context context, ClassLoader loader) {
        if (api != null) return;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(WeChatDecorator.WECHAT_PACKAGE, 0);
            Profile profile = profile(info.versionName, Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode);
            if (profile == null) return;
            Resolved resolved = new Resolved(profile, loader);
            handle = XposedBridge.hookMethod(resolved.serverGetter, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || !(param.getResult() instanceof Long)) return;
                    capture(resolved, param.thisObject, (Long) param.getResult());
                }
            });
            if (handle == null) throw new IllegalStateException("No hook handle");
            api = resolved;
            Log.i("WeChat.Voice", "adapter_ready code=" + (Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode));
        } catch (Throwable failure) {
            XposedBridge.rethrowFrameworkError(failure);
            if (handle != null) handle.unhook();
            handle = null;
            Log.w("WeChat.Voice", "adapter_unavailable type=" + failure.getClass().getSimpleName());
        }
    }

    private synchronized void capture(Resolved r, Object message, long serverId) {
        if (api != r || serverId <= 0 || !r.message.isInstance(message) || observing.get()) return;
        observing.set(true);
        try {
            if (((Integer) r.type.invoke(message) & 0xffff) != 34 || (Integer) r.sender.invoke(message) != 0) return;
            long local = (Long) r.local.invoke(message);
            String account = r.account(), talker = (String) r.talker.invoke(message), file = (String) r.file.invoke(message);
            if (local <= 0 || empty(account) || empty(talker) || empty(file)) return;
            long now = SystemClock.elapsedRealtime();
            captures.values().removeIf(c -> now < c.created || now - c.created >= VoiceTranscriptionQueue.TTL);
            VoiceTranscriptionQueue.Identity id = new VoiceTranscriptionQueue.Identity(account, talker, local, serverId);
            captures.putIfAbsent(id, new Capture(r, id, message, file, now));
            while (captures.size() > 64) captures.remove(captures.keySet().iterator().next());
        } catch (Exception failure) { /* Missing identity leaves the native notification untouched. */ }
        finally { observing.remove(); }
    }

    synchronized Capture find(String talker, long serverId) {
        if (api == null || talker == null || serverId <= 0) return null;
        Capture found = null;
        for (Capture capture : captures.values()) {
            if (!talker.equals(capture.id.talker()) || serverId != capture.id.serverId() || !capture.valid()) continue;
            if (found != null) return null; // An ambiguous identity is never guessed.
            found = capture;
        }
        return found;
    }

    synchronized void close() {
        captures.clear();
        if (handle != null) handle.unhook();
        handle = null; api = null;
    }

    static boolean empty(String text) { return text == null || text.isEmpty(); }

    static boolean audioReceived(int totalBytes, int receivedBytes, int status) {
        // Audio delivered in the sync payload can reach the received status without updating FileNowSize.
        return status == VOICE_STATUS_RECEIVED || (totalBytes > 0 && receivedBytes >= totalBytes);
    }

    final class Capture implements VoiceTranscriptionQueue.Backend {
        final Resolved r;
        final VoiceTranscriptionQueue.Identity id;
        final Object message;
        final String file;
        final long created;
        Capture(Resolved r, VoiceTranscriptionQueue.Identity id, Object message, String file, long created) {
            this.r = r; this.id = id; this.message = message; this.file = file; this.created = created;
        }
        boolean valid() {
            boolean previous = observing.get();
            observing.set(true);
            try {
                long age = SystemClock.elapsedRealtime() - created;
                return api == r && age >= 0 && age < VoiceTranscriptionQueue.TTL && id.account().equals(r.account())
                        && id.talker().equals(r.talker.invoke(message)) && id.localId() == (Long) r.local.invoke(message)
                        && id.serverId() == r.server.getLong(message) && file.equals(r.file.invoke(message))
                        && ((Integer) r.type.invoke(message) & 0xffff) == 34 && (Integer) r.sender.invoke(message) == 0;
            } catch (Exception unavailable) { return false; }
            finally { if (previous) observing.set(true); else observing.remove(); }
        }
        @Override public String cached() throws Exception {
            if (!valid()) return null;
            Object saved = r.cacheGet.invoke(r.cache.invoke(null), file);
            // The cache is keyed by filename; also require the exact local message ID.
            return saved != null && r.cachedId.getLong(saved) == id.localId()
                    ? (String) r.cachedText.get(saved) : null;
        }
        @Override public boolean ready() throws Exception {
            if (!valid()) return false;
            Object info = r.voiceInfoGet.invoke(r.voiceInfo.invoke(null), file);
            if (info == null || r.infoLocal.getLong(info) != id.localId() || r.infoServer.getLong(info) != id.serverId()
                    || !file.equals(r.infoFile.get(info)) || !id.talker().equals(r.infoTalker.get(info))) return false;
            if (!audioReceived(r.infoTotal.getInt(info), r.infoSize.getInt(info), r.infoStatus.getInt(info))) return false;
            Object descriptor = r.descriptor.invoke(null, message, r.audio.invoke(null, "AUDIO"));
            Object service = r.service.invoke(null, r.contract);
            if (!r.path.getDeclaringClass().isInstance(service)) return false;
            String path = (String) r.path.invoke(service, descriptor, file, false);
            return !empty(path) && (Boolean) r.exists.invoke(null, path) && (Long) r.size.invoke(null, path) >= 8;
        }
        @Override public void transcribe(Consumer<String> completion) throws Exception {
            if (!valid()) throw new IllegalStateException("Stale voice");
            Object event = r.event.newInstance();
            Object input = r.input.get(event);
            r.inputFile.set(input, file);
            r.inputId.set(input, Long.toString(id.localId()));
            r.inputTalker.set(input, id.talker());
            r.operation.setInt(input, 0);
            r.scene.setInt(input, 1);
            r.conversationType.setInt(input, id.talker().endsWith("@chatroom") ? 2 : 1);
            r.callback.set(input, (Runnable) () -> {
                try {
                    Object output = r.output.get(event);
                    // Native events can report intermediate results; only accept a completed result.
                    if (r.finished.getBoolean(output)) completion.accept((String) r.outputText.get(output));
                } catch (Exception failure) { completion.accept(null); }
            });
            if (!(Boolean) r.publish.invoke(event)) completion.accept(null);
        }
    }

    static final class Resolved {
        final Class<?> message, contract;
        final Constructor<?> event;
        final Method serverGetter, local, talker, type, sender, file, account, cache, cacheGet,
                descriptor, service, path, exists, size, publish, voiceInfo, voiceInfoGet;
        final Field server, cachedId, cachedText, input, output, inputFile, inputId,
                inputTalker, operation, scene, conversationType, callback, outputText, finished,
                infoLocal, infoServer, infoFile, infoTalker, infoTotal, infoSize, infoStatus;
        final Method audio;
        Resolved(Profile p, ClassLoader loader) throws ReflectiveOperationException {
            String storage = "com.tencent.mm.storage.";
            message = Class.forName(storage + p.layout.message, false, loader);
            serverGetter = method(message, p.serverGet, long.class, false);
            local = method(message, "getMsgId", long.class, false);
            talker = method(message, p.layout.talker, String.class, false);
            type = method(message, "getType", int.class, false);
            sender = method(message, p.senderGet, int.class, false);
            server = field(message, "field_msgSvrId", long.class);
            file = method(message, p.fileGet, String.class, false);
            account = method(Class.forName(p.account, false, loader), "u", String.class, true);
            Class<?> store = Class.forName(storage + p.layout.cacheStore, false, loader);
            Class<?> record = Class.forName(storage + p.layout.record, false, loader);
            cache = method(Class.forName(p.voice, false, loader), p.layout.cacheFactory, store, true);
            cacheGet = method(store, p.cacheGet, record, false, String.class);
            cachedId = field(record, "field_msgId", long.class);
            cachedText = field(record, "field_content", String.class);
            String voicePackage = p.voice.substring(0, p.voice.lastIndexOf('.'));
            Class<?> voiceStore = Class.forName(voicePackage + "." + p.layout.voiceStore, false, loader);
            Class<?> info = Class.forName(voicePackage + "." + p.layout.voiceInfo, false, loader);
            voiceInfo = method(Class.forName(p.voice, false, loader), p.layout.voiceFactory, voiceStore, true);
            voiceInfoGet = method(voiceStore, p.cacheGet, info, false, String.class);
            infoFile = field(info, "b", String.class); infoTalker = field(info, "x", String.class);
            infoLocal = field(info, "m", long.class); infoServer = field(info, "e", long.class);
            infoTotal = field(info, "h", int.class); infoSize = field(info, "g", int.class);
            infoStatus = field(info, "i", int.class);
            Class<?> resource = Class.forName(p.resource + ".e0", false, loader);
            if (!resource.isEnum()) throw new NoSuchMethodException("Resource enum");
            // Enum constant names survive obfuscation, but their public field names need not.
            audio = method(resource, "valueOf", resource, true, String.class);
            Class<?> descriptorType = Class.forName(p.resource + ".x", false, loader);
            descriptor = method(descriptorType, "a", descriptorType, true, message, resource);
            contract = Class.forName(p.mediaContract, false, loader);
            Class<?> media = Class.forName(p.media, false, loader);
            if (!contract.isAssignableFrom(media)) throw new NoSuchMethodException("Media contract");
            service = method(Class.forName(p.registry + ".n0", false, loader), "c",
                    Class.forName(p.registry + ".m", false, loader), true, Class.class);
            path = method(media, p.layout.path, String.class, false, descriptorType, String.class, boolean.class);
            Class<?> vfs = Class.forName("com.tencent.mm.vfs.z6", false, loader);
            exists = method(vfs, "j", boolean.class, true, String.class);
            size = method(vfs, "k", long.class, true, String.class);
            Class<?> eventType = Class.forName("com.tencent.mm.autogen.events.ExtTranslateVoiceEvent", false, loader);
            Class<?> base = Class.forName("com.tencent.mm.sdk.event.IEvent", false, loader);
            if (eventType.getSuperclass() != base) throw new NoSuchMethodException("Event base");
            event = eventType.getConstructor();
            publish = method(base, "e", boolean.class, false);
            Class<?> in = Class.forName(p.payload + ".z8", false, loader);
            Class<?> out = Class.forName(p.payload + ".a9", false, loader);
            input = field(eventType, "g", in); output = field(eventType, "h", out);
            inputFile = field(in, "a", String.class); inputId = field(in, "b", String.class);
            operation = field(in, "c", int.class); scene = field(in, "d", int.class);
            callback = field(in, "e", Runnable.class); conversationType = field(in, "f", int.class);
            inputTalker = field(in, "g", String.class);
            outputText = field(out, "a", String.class); finished = field(out, "b", boolean.class);
        }
        String account() throws ReflectiveOperationException { return (String) account.invoke(null); }
    }

    static Method method(Class<?> owner, String name, Class<?> returns, boolean isStatic, Class<?>... arguments)
            throws NoSuchMethodException {
        Method method = owner.getMethod(name, arguments);
        if (method.getReturnType() != returns || Modifier.isStatic(method.getModifiers()) != isStatic
                || Modifier.isAbstract(method.getModifiers())) throw new NoSuchMethodException(name);
        method.setAccessible(true);
        return method;
    }
    static Field field(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field field = null;
        for (Class<?> current = owner; current != null && field == null; current = current.getSuperclass()) {
            try { field = current.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        }
        if (field == null) throw new NoSuchFieldException(name);
        if (field.getType() != type || Modifier.isStatic(field.getModifiers())) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        return field;
    }
}
