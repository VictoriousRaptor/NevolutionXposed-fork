package com.oasisfeng.nevo.decorators.wechat;

import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.util.Log;

import com.oasisfeng.nevo.xposed.BuildConfig;
import com.oasisfeng.nevo.xposed.compat.XposedHelpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Version-locked access to WeChat's normal (non-original) image download and its ImgInfo2 base row.
 * Every descriptor is resolved and validated before use; any mismatch disables this component.
 */
final class WeChatImageDownloader {
	private static final String SQL = "SELECT id, msgSvrId, msgTalker, bigImgPath, hevcPath, midImgPath, offset, totalLen, reserved1 FROM ImgInfo2 WHERE msgSvrId=?";
	private static final int MAX_TASKS = 4;
	private static final long MAX_SOURCE_BYTES = 20L * 1024 * 1024;

	interface ReadyCallback { void ready(BaseRow row); }

	private final Object databaseLock = new Object();
	private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(2, runnable -> {
		Thread thread = new Thread(runnable, "NX-image-download");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<Long, Task> tasks = new HashMap<>();

	private volatile boolean available;
	private Object downloadService;
	private Class<?> callbackClass;
	private Class<?> msgIdTalkerClass;
	private Class<?> downloadServiceClass;
	private Constructor<?> msgIdTalkerConstructor;
	private Method coreStorageGet;
	private Field databaseField;
	private Method databaseQuery;
	private Method serviceGet;
	private Method downloadServiceGet;
	private Method downloadMethod;
	private Method wxgfDecode;

	WeChatImageDownloader() {
		worker.setKeepAliveTime(30, TimeUnit.SECONDS);
		worker.allowCoreThreadTimeOut(true);
		worker.setRemoveOnCancelPolicy(true);
	}

	void install(Context context, ClassLoader loader) {
		String stage = "version";
		try {
			android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo("com.tencent.mm", 0);
			if (!"8.0.72".equals(info.versionName) || info.versionCode != 3085) {
				log("large_unavailable", "reason=unsupported_version");
				return;
			}

			stage = "core_storage";
			Class<?> kernel = Class.forName("em0.k1", false, loader);
			Class<?> coreStorage = Class.forName("em0.c0", false, loader);
			Class<?> databaseWrapper = Class.forName("s85.a0", false, loader);
			coreStorageGet = method(kernel, "u", coreStorage);
			for (Field field : coreStorage.getDeclaredFields()) {
				if (field.getType() != databaseWrapper) continue;
				if (databaseField != null) throw new IllegalStateException("Ambiguous database wrapper");
				databaseField = field;
			}
			if (databaseField == null) throw new IllegalStateException("Missing database wrapper");
			databaseField.setAccessible(true);
			databaseQuery = method(databaseWrapper, "a", Cursor.class, String.class, String[].class, int.class);

			stage = "image_service";
			Class<?> serviceManager = Class.forName("w85.n0", false, loader);
			Class<?> imageService = Class.forName("n70.y", false, loader);
			Class<?> imageServiceImplementation = Class.forName("m70.e", false, loader);
			downloadServiceClass = Class.forName("l11.j", false, loader);
			msgIdTalkerClass = Class.forName("com.tencent.mm.plugin.msg.MsgIdTalker", false, loader);
			callbackClass = Class.forName("n70.w", false, loader);
			serviceGet = method(serviceManager, "c", Class.forName("w85.m", false, loader), Class.class);
			downloadServiceGet = method(imageServiceImplementation, "Bh", Class.forName("n70.x", false, loader));
			msgIdTalkerConstructor = msgIdTalkerClass.getDeclaredConstructor(long.class, String.class);
			msgIdTalkerConstructor.setAccessible(true);
			downloadMethod = XposedHelpers.findMethodExact(downloadServiceClass, "b", long.class, msgIdTalkerClass,
					int.class, Object.class, int.class, callbackClass, int.class, boolean.class);
			if (downloadMethod.getReturnType() != int.class)
				throw new IllegalStateException("Unexpected download result");

			stage = "wxgf";
			Class<?> wxgfClass = Class.forName("com.tencent.mm.plugin.gif.MMWXGFJNI", false, loader);
			wxgfDecode = method(wxgfClass, "wxam2PicBuf", byte[].class, byte[].class, int.class, int.class);

			available = true;
			log("large_ready", "profile=8.0.72/3085 revision=image-large-1 base_row_only=true");
		} catch (Throwable failure) {
			available = false;
			log("large_unavailable", "stage=" + stage + " type=" + failure.getClass().getSimpleName());
		}
	}

	/** Queries only the base row selected by the fixed msgSvrId rule. reserved1 is never followed. */
	BaseRow queryBaseRow(long msgSvrId, String expectedTalker) {
		if (!available || msgSvrId <= 0) return null;
		Cursor cursor = null;
		try {
			synchronized (databaseLock) {
				Object core = coreStorageGet.invoke(null);
				if (core == null) return null;
				Object wrapper = databaseField.get(core);
				if (wrapper == null) return null;
				Object result = databaseQuery.invoke(wrapper, SQL, new String[]{Long.toString(msgSvrId)}, 0);
				if (!(result instanceof Cursor)) return null;
				cursor = (Cursor) result;
				List<BaseRow> rows = new ArrayList<>();
				while (cursor.moveToNext()) {
					BaseRow row = readRow(cursor);
					if (row != null) rows.add(row);
				}
				return selectBaseRow(rows, expectedTalker);
			}
		} catch (Throwable failure) {
			log("large_query_failed", "type=" + failure.getClass().getSimpleName());
			return null;
		} finally {
			if (cursor != null) try { cursor.close(); } catch (Exception ignored) {}
		}
	}

	private BaseRow readRow(Cursor cursor) {
		try {
			return new BaseRow(
					cursor.getLong(cursor.getColumnIndexOrThrow("id")),
					cursor.getLong(cursor.getColumnIndexOrThrow("msgSvrId")),
					string(cursor, "msgTalker"),
					string(cursor, "bigImgPath"),
					string(cursor, "hevcPath"),
					string(cursor, "midImgPath"),
					cursor.getLong(cursor.getColumnIndexOrThrow("offset")),
					cursor.getLong(cursor.getColumnIndexOrThrow("totalLen")),
					cursor.getLong(cursor.getColumnIndexOrThrow("reserved1")));
		} catch (Exception failure) {
			return null;
		}
	}

	private static String string(Cursor cursor, String column) {
		int index = cursor.getColumnIndexOrThrow(column);
		return cursor.isNull(index) ? "" : cursor.getString(index);
	}

	/** Visible for unit tests: unique row, or the unique row pointing at another row. */
	static BaseRow selectBaseRow(List<BaseRow> rows, String expectedTalker) {
		if (rows == null || rows.isEmpty()) return null;
		BaseRow selected = null;
		if (rows.size() == 1) {
			selected = rows.get(0);
		} else {
			for (BaseRow row : rows) {
				if (row == null || row.reserved1 <= 0 || row.reserved1 == row.id || !containsId(rows, row.reserved1)) continue;
				if (selected != null) return null;
				selected = row;
			}
		}
		if (selected == null || selected.id <= 0 || selected.msgSvrId <= 0) return null;
		if (expectedTalker != null && !expectedTalker.isEmpty()
				&& (selected.talker == null || !expectedTalker.equals(selected.talker))) return null;
		return selected;
	}

	private static boolean containsId(List<BaseRow> rows, long id) {
		for (BaseRow row : rows) if (row != null && row.id == id && row.id != 0) return true;
		return false;
	}

	/**
	 * Starts one WeChat task per msgSvrId and polls it outside the image decode worker.
	 * Returning false means the caller must keep its normal timeout behavior.
	 */
	boolean request(BaseRow base, long requestToken, long deadline, ReadyCallback callback) {
		if (!available || base == null || requestToken == 0 || callback == null
				|| deadline <= SystemClock.elapsedRealtime()) return false;
		Task task;
		boolean created = false;
		synchronized (tasks) {
			task = tasks.get(base.msgSvrId);
			if (task == null) {
				if (tasks.size() >= MAX_TASKS) return false;
				task = new Task(base);
				tasks.put(base.msgSvrId, task);
				created = true;
			}
			task.waiters.put(requestToken, new Waiter(deadline, callback));
		}
		if (!created) return true;
		try {
			if (!submit(task)) {
				synchronized (tasks) { tasks.remove(base.msgSvrId, task); }
				return false;
			}
			Task scheduled = task;
			task.future = worker.schedule(() -> poll(scheduled), 50, TimeUnit.MILLISECONDS);
			log("large_submitted", "request=" + requestToken + " return=" + task.submitResult);
			return true;
		} catch (Throwable failure) {
			synchronized (tasks) { tasks.remove(base.msgSvrId, task); }
			log("large_submit_failed", "request=" + requestToken + " type=" + failure.getClass().getSimpleName());
			return false;
		}
	}

	void cancel(long requestToken) {
		if (requestToken == 0) return;
		synchronized (tasks) {
			Iterator<Map.Entry<Long, Task>> iterator = tasks.entrySet().iterator();
			while (iterator.hasNext()) {
				Task task = iterator.next().getValue();
				task.waiters.remove(requestToken);
				if (task.waiters.isEmpty()) {
					if (task.future != null) task.future.cancel(false);
					iterator.remove();
				}
			}
		}
	}

	private boolean submit(Task task) throws Exception {
		Object service = downloadService();
		if (service == null || !downloadServiceClass.isInstance(service))
			throw new IllegalStateException("Image download service unavailable");
		Object msgIdTalker = msgIdTalkerConstructor.newInstance(task.base.id, task.base.talker);
		Object listener = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass},
				new IgnoredCallback());
		Object result = downloadMethod.invoke(service, task.base.id, msgIdTalker, 0, null, 0, listener, -1, false);
		task.submitResult = ((Number) result).intValue();
		return task.submitResult >= 0;
	}

	private Object downloadService() throws Exception {
		Object cached = downloadService;
		if (cached != null) return cached;
		synchronized (this) {
			if (downloadService != null) return downloadService;
			Object implementation = serviceGet.invoke(null, Class.forName("n70.y", false, callbackClass.getClassLoader()));
			if (implementation == null) return null;
			downloadService = downloadServiceGet.invoke(implementation);
			return downloadService;
		}
	}

	private void poll(Task task) {
		long now = SystemClock.elapsedRealtime();
		List<ReadyCallback> ready = null;
		synchronized (tasks) {
			if (tasks.get(task.base.msgSvrId) != task) return;
			Iterator<Map.Entry<Long, Waiter>> iterator = task.waiters.entrySet().iterator();
			while (iterator.hasNext()) if (iterator.next().getValue().deadline <= now) iterator.remove();
			if (task.waiters.isEmpty()) {
				tasks.remove(task.base.msgSvrId, task);
				return;
			}
			if (task.ready) {
				ready = new ArrayList<>();
				for (Waiter waiter : task.waiters.values()) ready.add(waiter.callback);
				tasks.remove(task.base.msgSvrId, task);
			}
		}
		if (ready != null) {
			for (ReadyCallback callback : ready) {
				try { callback.ready(task.completedRow); }
				catch (Throwable failure) { log("large_callback_failed", "type=" + failure.getClass().getSimpleName()); }
			}
			log("large_ready", "elapsedMs=" + (now - task.started));
			return;
		}

		BaseRow current = queryBaseRow(task.base.msgSvrId, task.base.talker);
		boolean complete = current != null && current.id == task.base.id && current.isComplete(task.base);
		synchronized (tasks) {
			if (tasks.get(task.base.msgSvrId) != task) return;
			if (complete) {
				task.completedRow = current;
				task.ready = true;
			}
			else if (worker.getQueue().size() < 16) {
				try {
					task.future = worker.schedule(() -> poll(task), 100, TimeUnit.MILLISECONDS);
					return;
				} catch (RejectedExecutionException overloaded) {
					tasks.remove(task.base.msgSvrId, task);
					return;
				}
			} else {
				tasks.remove(task.base.msgSvrId, task);
				return;
			}
		}
		poll(task);
	}

	/** Decodes WXGF/HEVC-backed images only after the normal image decoder has rejected the file. */
	byte[] decodeWxgf(File file, long expectedSize, long expectedModified) {
		if (!available || file == null || expectedSize <= 0 || expectedSize > MAX_SOURCE_BYTES) return null;
		try {
			if (file.length() != expectedSize || file.lastModified() != expectedModified) return null;
			byte[] source = readAll(file, (int) expectedSize);
			if (source == null || file.length() != expectedSize || file.lastModified() != expectedModified) return null;
			Object result = wxgfDecode.invoke(null, source, 0, 5);
			if (!(result instanceof byte[])) return null;
			byte[] decoded = (byte[]) result;
			if (decoded.length == 0 || decoded.length > MAX_SOURCE_BYTES) return null;
			log("large_wxgf", "sourceBytes=" + source.length + " decodedBytes=" + decoded.length);
			return decoded;
		} catch (Throwable failure) {
			log("large_wxgf_failed", "type=" + failure.getClass().getSimpleName());
			return null;
		}
	}

	private static byte[] readAll(File file, int size) throws Exception {
		try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream(size)) {
			byte[] buffer = new byte[8192];
			int count;
			while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
			return output.toByteArray();
		}
	}

	private static Method method(Class<?> owner, String name, Class<?> returns, Class<?>... arguments) {
		Method method = XposedHelpers.findMethodExact(owner, name, arguments);
		if (method.getReturnType() != returns) throw new IllegalStateException("Unexpected return type: " + name);
		return method;
	}

	private static void log(String stage, String detail) {
		if (BuildConfig.DEBUG) Log.i("WeChatDecorator", "NX_IMAGE stage=" + stage + " " + detail);
	}

	static final class BaseRow {
		final long id, msgSvrId, offset, totalLen, reserved1;
		final String talker, bigImgPath, hevcPath, midImgPath;

		BaseRow(long id, long msgSvrId, String talker, String bigImgPath, String hevcPath, String midImgPath,
				long offset, long totalLen, long reserved1) {
			this.id = id;
			this.msgSvrId = msgSvrId;
			this.talker = talker == null ? "" : talker;
			this.bigImgPath = bigImgPath == null ? "" : bigImgPath;
			this.hevcPath = hevcPath == null ? "" : hevcPath;
			this.midImgPath = midImgPath == null ? "" : midImgPath;
			this.offset = offset;
			this.totalLen = totalLen;
			this.reserved1 = reserved1;
		}

		boolean isComplete(BaseRow original) {
			return original != null && id == original.id && totalLen > 0 && offset >= totalLen
					&& (!bigImgPath.isEmpty() || !hevcPath.isEmpty() || !midImgPath.isEmpty());
		}

		List<ImageEventIndex.Path> paths() {
			List<ImageEventIndex.Path> result = new ArrayList<>(3);
			add(result, bigImgPath, ImagePreviewPolicy.QUALITY_HD);
			add(result, hevcPath, ImagePreviewPolicy.QUALITY_HD);
			add(result, midImgPath, ImagePreviewPolicy.QUALITY_THUMBNAIL);
			return result;
		}

		private static void add(List<ImageEventIndex.Path> paths, String value, int quality) {
			if (value != null && !value.isEmpty()) paths.add(new ImageEventIndex.Path(value, quality));
		}
	}

	private static final class Waiter {
		final long deadline;
		final ReadyCallback callback;
		Waiter(long deadline, ReadyCallback callback) { this.deadline = deadline; this.callback = callback; }
	}

	private static final class Task {
		final BaseRow base;
		final long started = SystemClock.elapsedRealtime();
		final Map<Long, Waiter> waiters = new HashMap<>();
		volatile ScheduledFuture<?> future;
		volatile boolean ready;
		volatile BaseRow completedRow;
		int submitResult;
		Task(BaseRow base) { this.base = base; }
	}

	private static final class IgnoredCallback implements InvocationHandler {
		@Override public Object invoke(Object proxy, Method method, Object[] args) {
			Class<?> type = method.getReturnType();
			if (type == boolean.class) return false;
			if (type == byte.class) return (byte) 0;
			if (type == short.class) return (short) 0;
			if (type == int.class) return 0;
			if (type == long.class) return 0L;
			if (type == float.class) return 0f;
			if (type == double.class) return 0d;
			if (type == char.class) return '\0';
			if (method.getName().equals("toString")) return "NX-image-download-callback";
			if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
			if (method.getName().equals("equals")) return proxy == (args == null ? null : args[0]);
			return null;
		}
	}
}
