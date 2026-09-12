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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Decodes private WeChat files in WeChat and transports a bounded bitmap, not a private file URI. */
final class ImagePreviewLoader {
	private static final String TOKEN = "nevo.wechat.imageRequest";
	private static final String READY = "nevo.wechat.imageReady";
	private static final AtomicLong SEQUENCE = new AtomicLong(SystemClock.elapsedRealtimeNanos());
	private final Handler main = new Handler(Looper.getMainLooper());
	private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(8), runnable -> {
		Thread thread = new Thread(runnable, "NX-image-preview");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<String, Request> pending = new ConcurrentHashMap<>();
	// Accessed only by the worker; one candidate must never be assigned to two notifications.
	private final LinkedHashMap<String, Long> claimed = new LinkedHashMap<>();

	static boolean isPreview(Notification n) { return n.extras.getBoolean(READY); }

	private static void log(String stage, String detail) {
		Log.i("WeChatDecorator", "NX_IMAGE stage=" + stage + " " + detail);
	}

	void request(Context context, NotificationManager manager, String tag, int id, Notification n) {
		if (context == null || n.extras.containsKey(TOKEN)) return;
		String key = id + ":" + tag;
		Request old = pending.get(key);
		if (old != null && old.when == n.when && TextUtils.equals(old.title, n.extras.getCharSequence(Notification.EXTRA_TITLE))
				&& TextUtils.equals(old.text, n.extras.getCharSequence(Notification.EXTRA_TEXT))) {
			n.extras.putLong(TOKEN, old.token);
			return;
		}
		Request request = new Request(key, n);
		pending.put(key, request);
		n.extras.putLong(TOKEN, request.token);
		log("queued", "request=" + request.token + " id=" + id);
		try {
			worker.execute(() -> resolve(context, manager, tag, id, request));
		} catch (RejectedExecutionException overloaded) {
			pending.remove(key, request);
			log("fallback", "request=" + request.token + " reason=queue_full");
		}
	}

	private void resolve(Context context, NotificationManager manager, String tag, int id, Request request) {
		long started = SystemClock.elapsedRealtime();
		try {
			if (pending.get(request.key) != request) return;
			File selected = find(context, request);
			if (selected == null) return;
			String path = selected.getAbsolutePath();
			Long owner = claimed.get(path);
			if (owner != null && owner != request.token) {
				log("fallback", "request=" + request.token + " reason=already_assigned");
				return;
			}
			BitmapFactory.Options options = bounds(selected);
			int sample = 1;
			while (options.outWidth / sample > 384 || options.outHeight / sample > 384) sample *= 2;
			long size = selected.length(), modified = selected.lastModified();
			options.inJustDecodeBounds = false;
			options.inSampleSize = sample;
			options.inPreferredConfig = Bitmap.Config.RGB_565;
			Bitmap bitmap = BitmapFactory.decodeFile(path, options);
			if (bitmap == null || selected.length() != size || selected.lastModified() != modified) {
				if (bitmap != null) bitmap.recycle();
				log("fallback", "request=" + request.token + " reason=decode_or_file_changed");
				return;
			}
			claimed.put(path, request.token);
			while (claimed.size() > 64) claimed.remove(claimed.keySet().iterator().next());
			log("decoded", "request=" + request.token + " mime=" + options.outMimeType
					+ " width=" + bitmap.getWidth() + " height=" + bitmap.getHeight()
					+ " elapsedMs=" + (SystemClock.elapsedRealtime() - started));
			main.post(() -> publish(context, manager, tag, id, request, bitmap));
		} catch (Exception | OutOfMemoryError failure) {
			log("fallback", "request=" + request.token + " type=" + failure.getClass().getSimpleName());
		} finally {
			pending.remove(request.key, request);
		}
	}

	private File find(Context context, Request request) {
		File root = new File(context.getApplicationInfo().dataDir, "MicroMsg");
		File[] accounts = root.listFiles();
		if (accounts == null) { log("fallback", "reason=root_unreadable"); return null; }
		File imageDir = null;
		for (File account : accounts) {
			File images = new File(account, "image2");
			if (!account.isDirectory() || !images.isDirectory()) continue;
			if (imageDir != null) { log("fallback", "reason=multiple_image_accounts"); return null; }
			imageDir = images;
		}
		if (imageDir == null) { log("fallback", "reason=no_image_directory"); return null; }
		long deadline = SystemClock.elapsedRealtime() + 20000;
		List<File> candidates = new ArrayList<>();
		ArrayDeque<File> directories = new ArrayDeque<>();
		directories.add(imageDir);
		int files = 0, dirs = 0;
		while (!directories.isEmpty()) {
			if (pending.get(request.key) != request || SystemClock.elapsedRealtime() > deadline || ++dirs > 80000) {
				log("fallback", "request=" + request.token + " reason=scan_cancelled_or_limit");
				return null;
			}
			File[] children = directories.removeFirst().listFiles();
			if (children == null) { log("fallback", "reason=incomplete_scan"); return null; }
			for (File file : children) {
				if (file.isDirectory()) {
					if (file.getName().length() == 2) directories.addLast(file);
				} else if (file.isFile()) {
					if (++files > 150000) { log("fallback", "reason=file_limit"); return null; }
					if (ImageCandidateSelector.isRecent(file.lastModified(), request.received)) candidates.add(file);
				}
			}
		}
		File selected = ImageCandidateSelector.unique(candidates, file -> {
			if (file.length() <= 0 || file.length() > 20 * 1024 * 1024) return false;
			BitmapFactory.Options info = bounds(file);
			return info.outWidth > 0 && info.outHeight > 0 && info.outMimeType != null && info.outMimeType.startsWith("image/");
		});
		log("scan_result", "request=" + request.token + " files=" + files + " candidates=" + candidates.size()
				+ " selected=" + (selected != null) + " criterion=unique_decodable_within_2s");
		return selected;
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
			if (current == null) {
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
		}
	}

	private static final class Request {
		final String key;
		final long token = SEQUENCE.incrementAndGet(), received = System.currentTimeMillis();
		final long when;
		final CharSequence title, text;
		Request(String key, Notification n) {
			this.key = key;
			when = n.when;
			title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
			text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
		}
	}
}
