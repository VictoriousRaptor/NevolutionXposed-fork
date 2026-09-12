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
import androidx.core.app.NotificationCompat;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.xposed.BuildConfig;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
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
	// Published previews stay a short while so later messages in the same conversation keep the image.
	private final Map<String, Preview> previews = new ConcurrentHashMap<>();
	// Accessed only by the worker; one candidate must never be assigned to two notifications.
	private final LinkedHashMap<String, Long> claimed = new LinkedHashMap<>();

	private static final long PREVIEW_KEEP_MS = 30000;
	private static final int PREVIEW_CACHE = 4;

	static boolean isPreview(Notification n) { return n.extras.getBoolean(READY); }

	/** Resolved lazily: WeChat answers the reply-intent probe asynchronously, shortly after the notification is rebuilt. */
	interface TalkerSource { String talker(); }

	private static void log(String stage, String detail) {
		if (BuildConfig.DEBUG) Log.i("WeChatDecorator", "NX_IMAGE stage=" + stage + " " + detail);
	}

	/**
	 * WeChat updates the whole conversation notification for every later message. Re-attach the preview only when the
	 * update still lists that exact image message, so the picture is never carried over to an unrelated notification.
	 */
	synchronized void keepPreview(Context context, NotificationManager manager, String tag, int id, Notification n, String talker,
			List<NotificationCompat.MessagingStyle.Message> messages) {
		if (context == null || talker == null || talker.isEmpty() || isPreview(n)) return;
		Preview preview = previews.get(id + ":" + tag);
		if (preview == null || !talker.equals(preview.talker)) return;
		if (SystemClock.elapsedRealtime() - preview.publishedAt >= PREVIEW_KEEP_MS) return;
		if (!mentionsImage(messages)) return;
		main.postDelayed(() -> reattach(context, manager, tag, id, preview), 150);
	}

	private static boolean mentionsImage(List<NotificationCompat.MessagingStyle.Message> messages) {
		for (NotificationCompat.MessagingStyle.Message message : messages) {
			CharSequence text = message.getText();
			if (text != null && text.toString().indexOf("图片") >= 0) return true;
		}
		return false;
	}

	private static Notification active(NotificationManager manager, String tag, int id) {
		for (StatusBarNotification active : manager.getActiveNotifications())
			if (active.getId() == id && Objects.equals(active.getTag(), tag)) return active.getNotification();
		return null;
	}

	synchronized void request(Context context, NotificationManager manager, String tag, int id, Notification n, TalkerSource talker) {
		if (context == null || n.extras.containsKey(TOKEN)) return;
		if (!events.isAvailable()) { log("fallback", "reason=events_unavailable scans=0"); return; }
		String key = id + ":" + tag;
		Request old = pending.get(key);
		if (old != null && old.when == n.when && TextUtils.equals(old.title, n.extras.getCharSequence(Notification.EXTRA_TITLE))
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
		log("queued", "request=" + request.token + " id=" + id + " when=" + request.when);
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
			String talker = request.talker();
			if (talker == null || talker.isEmpty()) { retry(context, manager, tag, id, request, "talker_pending"); return; }
			ImageEventIndex.Entry event = events.index.select(talker,
					request.when > 0 ? request.when : request.received, request.started, SystemClock.elapsedRealtime());
			if (event == null) { retry(context, manager, tag, id, request, "no_unique_message_event"); return; }
			request.resolvedTalker = talker;
			request.messageTime = event.created;
			Long owner = claimed.get(event.key);
			if (owner != null && owner != request.token) { finish(request, "message_already_assigned"); return; }
			for (String path : event.paths) {
				String resolved = events.resolvePath(path);
				if (!request.diagnosed) {
					request.diagnosed = true;
					log("resolved", "request=" + request.token + " exists=" + (resolved != null && new File(resolved).exists()) + " value=" + resolved);
				}
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
		if (request.attempts == 1) log("retry", "request=" + request.token + " reason=" + reason);
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
			// recoverBuilder() already parceled the actions, so the notify hook may skip its own rebuild.
			NevoDecoratorService.LocalDecorator.markActionsSerialized(result);
			manager.notify(tag, id, result);
			log("published", "request=" + request.token + " style=BigPicture actions="
					+ (result.actions == null ? 0 : result.actions.length));
			previews.put(request.key, new Preview(request.resolvedTalker, request.messageTime, request.token, bitmap,
					SystemClock.elapsedRealtime()));
			prunePreviews();
		} catch (Exception failure) {
			log("publish_failed", "request=" + request.token + " type=" + failure.getClass().getSimpleName());
		} finally {
			pending.remove(request.key, request);
		}
	}

	private void reattach(Context context, NotificationManager manager, String tag, int id, Preview preview) {
		Notification current = active(manager, tag, id);
		if (current == null || isPreview(current)) return;
		try {
			Notification.Builder builder = Notification.Builder.recoverBuilder(context, current)
					.setStyle(new Notification.BigPictureStyle().bigPicture(preview.bitmap))
					.setOnlyAlertOnce(true);
			Notification result = builder.build();
			result.extras.putBoolean(READY, true);
			result.extras.putLong(TOKEN, preview.token);
			NevoDecoratorService.LocalDecorator.markActionsSerialized(result);
			manager.notify(tag, id, result);
			log("reattached", "request=" + preview.token + " id=" + id + " style=BigPicture");
		} catch (Exception failure) {
			log("reattach_failed", "request=" + preview.token + " type=" + failure.getClass().getSimpleName());
		}
	}

	private void prunePreviews() {
		while (previews.size() > PREVIEW_CACHE) {
			String oldest = null;
			long oldestTime = Long.MAX_VALUE;
			for (Map.Entry<String, Preview> entry : previews.entrySet())
				if (entry.getValue().publishedAt < oldestTime) { oldestTime = entry.getValue().publishedAt; oldest = entry.getKey(); }
			if (oldest == null) return;
			previews.remove(oldest);
		}
	}

	private static final class Preview {
		final String talker;
		final long messageTime, token, publishedAt;
		final Bitmap bitmap;
		Preview(String talker, long messageTime, long token, Bitmap bitmap, long publishedAt) {
			this.talker = talker;
			this.messageTime = messageTime;
			this.token = token;
			this.bitmap = bitmap;
			this.publishedAt = publishedAt;
		}
	}

	private static final class Request {
		final String key;
		private final TalkerSource talkerSource;
		final long token = SEQUENCE.incrementAndGet(), received = System.currentTimeMillis();
		final long when, started = SystemClock.elapsedRealtime();
		int attempts;
		boolean diagnosed;
		volatile String resolvedTalker;
		volatile long messageTime;
		volatile ScheduledFuture<?> future;
		final CharSequence title, text;
		Request(String key, TalkerSource talkerSource, Notification n) {
			this.key = key;
			this.talkerSource = talkerSource;
			when = n.when;
			title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
			text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
		}
		String talker() { return talkerSource == null ? null : talkerSource.talker(); }
	}
}
