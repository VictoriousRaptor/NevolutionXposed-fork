package com.oasisfeng.nevo.decorators.wechat;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Decodes private WeChat files in WeChat and transports a bounded bitmap, not a private file URI. */
final class ImagePreviewLoader {
	private static final String TOKEN = "nevo.wechat.imageRequest";
	private static final String READY = "nevo.wechat.imageReady";
	private static final AtomicLong SEQUENCE = new AtomicLong(SystemClock.elapsedRealtimeNanos());
	private final Handler main = new Handler(Looper.getMainLooper());
	private final WeChatImageEvents events;
	private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1, runnable -> {
		Thread thread = new Thread(runnable, "NX-image-preview");
		thread.setDaemon(true);
		return thread;
	});
	ImagePreviewLoader(WeChatImageEvents events) {
		this.events = events;
		worker.setKeepAliveTime(30, TimeUnit.SECONDS);
		worker.allowCoreThreadTimeOut(true);
		worker.setRemoveOnCancelPolicy(true);
	}
	private final Map<String, Request> pending = new ConcurrentHashMap<>();
	// Accessed only by the worker; one candidate must never be assigned to two notifications.
	private final LinkedHashMap<String, Long> claimed = new LinkedHashMap<>();

	static boolean isPreview(Notification n) { return n.extras.getBoolean(READY); }

	private static void log(String stage, String detail) {
		Log.i("WeChatDecorator", "NX_IMAGE stage=" + stage + " " + detail);
	}

	synchronized void request(Context context, NotificationManager manager, String tag, int id, Notification n) {
		if (context == null || n.extras.containsKey(TOKEN)) return;
		if (!events.isAvailable()) { log("fallback", "reason=events_unavailable scans=0"); return; }
		String talker = WeChatImageEvents.notificationTalker(n);
		if (talker == null) { log("fallback", "reason=notification_identity_missing scans=0"); return; }
		String key = id + ":" + tag;
		Request old = pending.get(key);
		if (old != null && old.talker.equals(talker) && old.when == n.when && TextUtils.equals(old.title, n.extras.getCharSequence(Notification.EXTRA_TITLE))
				&& TextUtils.equals(old.text, n.extras.getCharSequence(Notification.EXTRA_TEXT))) {
			n.extras.putLong(TOKEN, old.token);
			return;
		}
		if (pending.size() >= 8 && !pending.containsKey(key)) { log("fallback", "reason=request_limit"); return; }
		if (worker.getQueue().size() >= 16) { log("fallback", "reason=queue_limit"); return; }
		if (old != null && old.future != null) old.future.cancel(false);
		Request request = new Request(key, talker, n);
		pending.put(key, request);
		n.extras.putLong(TOKEN, request.token);
		log("queued", "request=" + request.token + " id=" + id);
		try {
			request.future = worker.schedule(() -> resolve(context, manager, tag, id, request), 0, TimeUnit.MILLISECONDS);
		} catch (RejectedExecutionException overloaded) {
			pending.remove(key, request);
			log("fallback", "request=" + request.token + " reason=queue_full");
		}
	}

	private void resolve(Context context, NotificationManager manager, String tag, int id, Request request) {
		if (pending.get(request.key) != request) return;
		try {
			ImageEventIndex.Entry event = events.index.select(request.talker,
					request.when > 0 ? request.when : request.received, SystemClock.elapsedRealtime());
			if (event == null) { retry(context, manager, tag, id, request, "no_unique_message_event"); return; }
			Long owner = claimed.get(event.key);
			if (owner != null && owner != request.token) { finish(request, "message_already_assigned"); return; }
			for (String path : event.paths) {
				String resolved = events.resolvePath(path);
				if (resolved == null || !new File(resolved).isAbsolute()) continue;
				File file = new File(resolved);
				long size = file.length(), modified = file.lastModified();
				if (size <= 0 || size > 20 * 1024 * 1024 || !file.isFile() || !file.canRead()) continue;
				BitmapFactory.Options options = bounds(file);
				if (options.outWidth <= 0 || options.outHeight <= 0 || options.outMimeType == null
						|| !options.outMimeType.startsWith("image/")) continue;
				int sample = 1;
				while (options.outWidth / sample > 384 || options.outHeight / sample > 384) sample *= 2;
				options.inJustDecodeBounds = false;
				options.inSampleSize = sample;
				options.inPreferredConfig = Bitmap.Config.RGB_565;
				Bitmap bitmap = BitmapFactory.decodeFile(resolved, options);
				if (bitmap == null) continue;
				if (file.length() != size || file.lastModified() != modified) { bitmap.recycle(); continue; }
				claimed.put(event.key, request.token);
				while (claimed.size() > 64) claimed.remove(claimed.keySet().iterator().next());
				log("decoded", "request=" + request.token + " source=message_event scans=0 mime=" + options.outMimeType
						+ " width=" + bitmap.getWidth() + " height=" + bitmap.getHeight()
						+ " elapsedMs=" + (SystemClock.elapsedRealtime() - request.started));
				main.post(() -> publish(context, manager, tag, id, request, bitmap));
				return;
			}
			retry(context, manager, tag, id, request, "file_not_ready");
		} catch (Exception | OutOfMemoryError failure) { finish(request, failure.getClass().getSimpleName()); }
	}

	private void retry(Context context, NotificationManager manager, String tag, int id, Request request, String reason) {
		long elapsed = SystemClock.elapsedRealtime() - request.started;
		if (elapsed >= 5000 || ++request.attempts >= 16) { finish(request, reason); return; }
		if (pending.get(request.key) != request) return;
		request.future = worker.schedule(() -> resolve(context, manager, tag, id, request), elapsed < 1000 ? 100 : 500, TimeUnit.MILLISECONDS);
	}

	private void finish(Request request, String reason) {
		pending.remove(request.key, request);
		log("fallback", "request=" + request.token + " reason=" + reason + " scans=0");
	}

	private static BitmapFactory.Options bounds(File file) {
		BitmapFactory.Options info = new BitmapFactory.Options();
		info.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(file.getAbsolutePath(), info);
		return info;
	}

	private void publish(Context context, NotificationManager manager, String tag, int id, Request request, Bitmap bitmap) {
		try {
			Notification current = null;
			for (StatusBarNotification active : manager.getActiveNotifications()) {
				if (active.getId() == id && Objects.equals(active.getTag(), tag)
						&& ImageCandidateSelector.matchesRequest(request.token, active.getNotification().extras.getLong(TOKEN))) {
					current = active.getNotification();
					break;
				}
			}
			if (current == null || pending.get(request.key) != request) {
				bitmap.recycle();
				log("discarded", "request=" + request.token + " reason=notification_removed_or_replaced");
				return;
			}
			Notification.Builder builder = Notification.Builder.recoverBuilder(context, current);
			builder.setStyle(new Notification.BigPictureStyle().bigPicture(bitmap))
					.setOnlyAlertOnce(true);
			Notification result = builder.build();
			result.extras.putBoolean(READY, true);
			result.extras.putLong(TOKEN, request.token);
			manager.notify(tag, id, result);
			log("published", "request=" + request.token + " style=BigPicture actions="
					+ (result.actions == null ? 0 : result.actions.length));
		} catch (Exception failure) {
			log("publish_failed", "request=" + request.token + " type=" + failure.getClass().getSimpleName());
		} finally {
			pending.remove(request.key, request);
		}
	}

	private static final class Request {
		final String key, talker;
		final long token = SEQUENCE.incrementAndGet(), received = System.currentTimeMillis();
		final long when, started = SystemClock.elapsedRealtime();
		int attempts;
		volatile ScheduledFuture<?> future;
		final CharSequence title, text;
		Request(String key, String talker, Notification n) {
			this.key = key;
			this.talker = talker;
			when = n.when;
			title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
			text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
		}
	}
}
