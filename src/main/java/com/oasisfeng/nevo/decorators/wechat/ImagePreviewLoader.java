package com.oasisfeng.nevo.decorators.wechat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;
import com.oasisfeng.nevo.xposed.BuildConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
	private final WeChatImageDownloader largePreview = new WeChatImageDownloader();
	private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1, runnable -> {
		Thread thread = new Thread(runnable, "NX-image-preview");
		thread.setDaemon(true);
		return thread;
	});
	ImagePreviewLoader(WeChatImageEvents events) {
		this.events = events;
		log("diagnostic_build", "revision=image-diag-2");
		worker.setKeepAliveTime(30, TimeUnit.SECONDS);
		worker.allowCoreThreadTimeOut(true);
		worker.setRemoveOnCancelPolicy(true);
	}
	void setLargePreviewEnabled(boolean enabled) { largePreviewEnabled = enabled; }
	void installLargePreview(Context context, ClassLoader loader) {
		if (largePreviewEnabled) largePreview.install(context, loader);
	}
	private final Map<String, Request> pending = new ConcurrentHashMap<>();
	// Published previews stay a short while so later messages in the same conversation keep the image.
	private final Map<String, Preview> previews = new ConcurrentHashMap<>();
	// Accessed only by the worker; one candidate must never be assigned to two notifications.
	private final LinkedHashMap<String, Long> claimed = new LinkedHashMap<>();
	private final Map<Long, Request> probes = new ConcurrentHashMap<>();
	private volatile boolean largePreviewEnabled;

	private static final long PREVIEW_KEEP_MS = 30000;
	private static final int PREVIEW_CACHE = 4;
	private static final long PREVIEW_CACHE_BYTES = (long) ImagePreviewPolicy.MAX_PIXELS * 4 * PREVIEW_CACHE;
	private static final long MAX_SOURCE_BYTES = 20L * 1024 * 1024;

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
		prunePreviews(SystemClock.elapsedRealtime());
		String key = id + ":" + tag;
		Request request = pending.get(key);
		Preview preview = previews.get(key);
		if (request == null && preview != null) request = preview.request;
		if (request == null) return;
		Notification previous = active(manager, tag, id);
		ImageEventIndex.Entry event = events.index.select(talker, request.when, request.started, SystemClock.elapsedRealtime());
		boolean safe = ImageNotificationIdentity.continues(request.token, previous == null ? 0 : previous.extras.getLong(TOKEN),
				request.resolvedTalker, talker, request.eventKey, event == null ? null : event.key,
				request.messages, messageIdentity(messages), imageCount(messages));
		if (!safe) {
			pending.remove(key, request);
			if (request.future != null) request.future.cancel(false);
			previews.remove(key);
			log("continuity_rejected", "request=" + request.token + " activeToken="
					+ (previous == null ? 0 : previous.extras.getLong(TOKEN)) + " images=" + imageCount(messages));
			return;
		}
		n.extras.putLong(TOKEN, request.token);
		log("continuity", "request=" + request.token + " pending=" + pending.containsKey(key));
		if (preview != null) main.postDelayed(() -> reattach(context, manager, tag, id, preview), 150);
	}

	private static int imageCount(List<NotificationCompat.MessagingStyle.Message> messages) {
		int count = 0;
		for (NotificationCompat.MessagingStyle.Message message : messages) {
			CharSequence text = message.getText();
			if (text != null && ("图片".contentEquals(text) || "[图片]".contentEquals(text))) count++;
		}
		return count;
	}

	private static List<String> messageIdentity(List<NotificationCompat.MessagingStyle.Message> messages) {
		List<String> result = new ArrayList<>();
		for (NotificationCompat.MessagingStyle.Message message : messages) {
			androidx.core.app.Person sender = message.getPerson();
			String person = sender == null ? "" : String.valueOf(sender.getKey()) + ":" + sender.getName();
			// Length prefix prevents separators in user text from creating ambiguous identities. Never logged.
			String text = String.valueOf(message.getText());
			result.add(message.getTimestamp() + ":" + person.length() + ":" + person + ":" + text.length() + ":" + text);
		}
		return result;
	}

	private static Notification active(NotificationManager manager, String tag, int id) {
		for (StatusBarNotification active : manager.getActiveNotifications())
			if (active.getId() == id && Objects.equals(active.getTag(), tag)) return active.getNotification();
		return null;
	}

	synchronized void request(Context context, NotificationManager manager, String tag, int id, Notification n, TalkerSource talker,
			List<NotificationCompat.MessagingStyle.Message> messages) {
		if (context == null || n.extras.containsKey(TOKEN)) return;
		if (!events.isAvailable()) { log("fallback", "reason=events_unavailable scans=0"); return; }
		String key = id + ":" + tag;
		Request old = pending.get(key);
		Notification previous = active(manager, tag, id);
		if (old != null && previous != null && previous.extras.getLong(TOKEN) == old.token
				&& old.messages.equals(messageIdentity(messages)) && old.when == n.when
				&& Objects.equals(old.talker(), talker.talker()) && TextUtils.equals(old.title, n.extras.getCharSequence(Notification.EXTRA_TITLE))
				&& TextUtils.equals(old.text, n.extras.getCharSequence(Notification.EXTRA_TEXT))) {
			n.extras.putLong(TOKEN, old.token);
			return;
		}
		if (pending.size() >= 8 && !pending.containsKey(key)) { log("fallback", "reason=request_limit"); return; }
		if (worker.getQueue().size() >= 16) { log("fallback", "reason=queue_limit"); return; }
		if (old != null && old.future != null) old.future.cancel(false);
		if (old != null) largePreview.cancel(old.token);
		previews.remove(key);
		Request request = new Request(key, talker, n, messageIdentity(messages));
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
			if (talker == null || talker.isEmpty()) { retryOrFinish(context, manager, tag, id, request, "talker_pending"); return; }
			ImageEventIndex.Entry event = events.index.select(talker,
					request.when > 0 ? request.when : request.received, request.started, SystemClock.elapsedRealtime());
			if (event == null) { retryOrFinish(context, manager, tag, id, request, "no_unique_message_event"); return; }
			request.resolvedTalker = talker;
			request.messageTime = event.created;
			if (request.eventKey != null && !request.eventKey.equals(event.key)) { finish(request, "event_changed"); return; }
			request.eventKey = event.key;
			request.probePaths = event.paths;
			startProbe(request);
			Long owner = claimed.get(event.key);
			if (owner != null && owner != request.token) { finish(request, "message_already_assigned"); return; }
			ImagePreviewPolicy.Size target = targetSize(context);
			Set<String> resolvedPaths = new HashSet<>();
			List<ImageEventIndex.Path> rankedPaths = new ArrayList<>(event.paths);
			rankedPaths.sort((a, b) -> Integer.compare(b.quality, a.quality));
			Scan scan = scanCandidates(request, rankedPaths, target, resolvedPaths, null);
			boolean largeCandidateConsidered = false;
			if (largePreviewEnabled && event.msgSvrId > 0
					&& (scan.best == null || !ImagePreviewPolicy.coversTarget(scan.best.width, scan.best.height, target.width, target.height))) {
				long now = SystemClock.elapsedRealtime();
				if (request.largeRow == null && now >= request.largeNextLookupAt) {
					request.largeNextLookupAt = now + 200;
					WeChatImageDownloader.BaseRow row = largePreview.queryBaseRow(event.msgSvrId, talker);
					if (!request.largeLookupLogged || row != null) {
						request.largeLookupLogged = true;
						log("large_lookup", "request=" + request.token + " row=" + (row != null)
								+ " originalRowFollowed=false");
					}
					request.largeRow = row;
					request.largePaths = request.largeRow != null && request.largeRow.isComplete(request.largeRow)
							? request.largeRow.paths() : null;
				}
				if (request.largePaths != null && !request.largePaths.isEmpty()) {
					largeCandidateConsidered = true;
					List<ImageEventIndex.Path> largePaths = new ArrayList<>(request.largePaths);
					largePaths.sort((a, b) -> Integer.compare(b.quality, a.quality));
					scan = scanCandidates(request, largePaths, target, resolvedPaths, scan.best);
				}
				if (request.largeRow != null && !request.largeSubmitAttempted
						&& (scan.best == null || !ImagePreviewPolicy.coversTarget(scan.best.width, scan.best.height, target.width, target.height))) {
					WeChatImageDownloader.BaseRow row = request.largeRow;
					request.largeSubmitAttempted = true;
					largePreview.request(row, request.token,
							request.started + ImagePreviewPolicy.QUALITY_WAIT_MS, completedRow -> {
								if (completedRow != null) {
									request.largeDownloadComplete = true;
									request.largePaths = completedRow.paths();
								}
								try {
									request.future = worker.schedule(() -> resolve(context, manager, tag, id, request), 0, TimeUnit.MILLISECONDS);
								} catch (RejectedExecutionException ignored) {
									finish(request, "queue_full");
								}
							});
				}
			}
			Candidate best = scan.best;
			int valid = scan.valid;
			if (!request.diagnosed) {
				request.diagnosed = true;
				log("resolved", "request=" + request.token + " paths=" + event.paths.size() + " valid=" + valid
						+ " target=" + target.width + "x" + target.height + " large=" + largeCandidateConsidered);
			}
			if (best == null) { retryOrFinish(context, manager, tag, id, request, "file_not_ready"); return; }
			long elapsed = SystemClock.elapsedRealtime() - request.started;
			boolean waitForQuality = ImagePreviewPolicy.shouldWaitForQuality(best.width, best.height, best.quality,
					target.width, target.height, elapsed, request.largeDownloadComplete);
			if (waitForQuality && retry(context, manager, tag, id, request, "waiting_for_quality")) return;
			if (request.publishing) return;
			Bitmap bitmap = decode(best, target);
			if (bitmap == null) {
				log("decode_failed", "request=" + request.token + " quality=" + best.quality
						+ " fileChanged=" + (best.file.length() != best.size || best.file.lastModified() != best.modified));
				retryOrFinish(context, manager, tag, id, request, "decode_or_file_changed"); return;
			}
			request.publishing = true;
			ImagePreviewPolicy.Size planned = ImagePreviewPolicy.fitInside(best.width, best.height,
					target.width, target.height, ImagePreviewPolicy.MAX_PIXELS);
			int sample = ImagePreviewPolicy.sampleSize(best.width, best.height, planned.width, planned.height);
			claimed.put(event.key, request.token);
			while (claimed.size() > 64) claimed.remove(claimed.keySet().iterator().next());
			log("decoded", "request=" + request.token + " source=message_event scans=0 mime=" + best.mime
					+ " quality=" + best.quality + " sourceSize=" + best.width + "x" + best.height + " sample=" + sample
					+ " output=" + bitmap.getWidth() + "x" + bitmap.getHeight()
					+ " bytes=" + bitmap.getAllocationByteCount() + " elapsedMs=" + (SystemClock.elapsedRealtime() - request.started));
			main.post(() -> publish(context, manager, tag, id, request, bitmap));
		} catch (Exception | OutOfMemoryError failure) { finish(request, failure.getClass().getSimpleName()); }
	}

	private Scan scanCandidates(Request request, List<ImageEventIndex.Path> paths, ImagePreviewPolicy.Size target,
			Set<String> resolvedPaths, Candidate initial) {
		Scan scan = new Scan(initial);
		for (ImageEventIndex.Path path : paths) {
			String resolved;
			try { resolved = events.resolvePath(path.value); }
			catch (Exception failure) {
				candidateLog(request, path, "resolve_error=" + failure.getClass().getSimpleName());
				if (!request.pathFailureLogged) {
					request.pathFailureLogged = true;
					log("candidate_error", "request=" + request.token + " type=" + failure.getClass().getSimpleName());
				}
				continue;
			}
			if (resolved == null) { candidateLog(request, path, "reason=resolved_null"); continue; }
			if (!resolvedPaths.add(resolved)) { candidateLog(request, path, "reason=duplicate_resolved_path"); continue; }
			File file = new File(resolved);
			if (!file.isAbsolute()) { candidateLog(request, path, "reason=not_absolute"); continue; }
			long size = file.length(), modified = file.lastModified();
			String fileState = "exists=" + file.exists() + " regular=" + file.isFile()
					+ " readable=" + file.canRead() + " bytes=" + size;
			if (size <= 0 || size > MAX_SOURCE_BYTES || !file.isFile() || !file.canRead()) {
				candidateLog(request, path, "reason=file_unavailable " + fileState); continue;
			}
			BitmapFactory.Options options = bounds(file);
			byte[] decodedBytes = null;
			if (largePreviewEnabled && (options.outWidth <= 0 || options.outHeight <= 0)) {
				decodedBytes = largePreview.decodeWxgf(file, size, modified);
				if (decodedBytes != null) options = bounds(decodedBytes);
			}
			candidateLog(request, path, fileState + " source=" + options.outWidth + "x" + options.outHeight
					+ " mime=" + options.outMimeType + " wxgf=" + (decodedBytes != null));
			if (options.outWidth <= 0 || options.outHeight <= 0 || options.outMimeType == null
					|| !options.outMimeType.startsWith("image/")) continue;
			scan.valid++;
			if (scan.best == null || ImagePreviewPolicy.isBetter(options.outWidth, options.outHeight, path.quality,
					scan.best.width, scan.best.height, scan.best.quality, target.width, target.height))
				scan.best = new Candidate(file, size, modified, options.outWidth, options.outHeight, options.outMimeType,
						path.quality, decodedBytes);
		}
		return scan;
	}

	/** Debug-only observation outlives preview resolution, but cannot publish or initiate downloads. */
	private void startProbe(Request request) {
		if (!BuildConfig.DEBUG || request.probeStarted) return;
		request.probeStarted = true;
		if (probes.size() >= 8 || worker.getQueue().size() >= 16) return;
		probes.put(request.token, request);
		worker.schedule(() -> probe(request), 2, TimeUnit.SECONDS);
	}

	private void probe(Request request) {
		if (!BuildConfig.DEBUG) return;
		long now = SystemClock.elapsedRealtime();
		if (now - request.started >= 120000) {
			probes.remove(request.token);
			log("probe_finished", "request=" + request.token);
			return;
		}
		try {
			ImageEventIndex.Entry event = events.index.get(request.eventKey, now);
			if (event != null) request.probePaths = event.paths;
			for (ImageEventIndex.Path path : request.probePaths) {
				if (!request.probeStates.containsKey(path.value) && request.probeStates.size() >= 64) continue;
				String resolved = events.resolvePath(path.value);
				String state = "quality=" + path.quality + " resolved=" + (resolved != null);
				if (resolved != null) {
					File file = new File(resolved);
					if (!file.isAbsolute()) continue;
					long bytes = file.length();
					state += " exists=" + file.exists() + " readable=" + file.canRead() + " bytes=" + bytes;
					if (file.isFile() && file.canRead() && bytes > 0 && bytes <= MAX_SOURCE_BYTES) {
						BitmapFactory.Options info = bounds(file);
						state += " source=" + info.outWidth + "x" + info.outHeight;
					}
				}
				String previous = request.probeStates.put(path.value, state);
				if (!state.equals(previous)) {
					int candidate = new ArrayList<>(request.probeStates.keySet()).indexOf(path.value) + 1;
					log("path_probe", "request=" + request.token + " candidate=" + candidate + " " + state
							+ " elapsedMs=" + (now - request.started));
				}
			}
		} catch (Exception failure) {
			log("probe_failed", "request=" + request.token + " type=" + failure.getClass().getSimpleName());
			probes.remove(request.token);
			return;
		}
		if (worker.getQueue().size() >= 16) { probes.remove(request.token); return; }
		worker.schedule(() -> probe(request), Math.min(2000, 120000 - (now - request.started)), TimeUnit.MILLISECONDS);
	}

	/** Request-local numeric IDs correlate candidates without exposing paths. Log only state changes. */
	private static void candidateLog(Request request, ImageEventIndex.Path path, String state) {
		if (!BuildConfig.DEBUG) return;
		Integer id = request.candidateIds.get(path.value);
		if (id == null) {
			if (request.candidateIds.size() >= 64) return;
			id = request.candidateIds.size() + 1;
			request.candidateIds.put(path.value, id);
		}
		String detail = "quality=" + path.quality + " " + state;
		if (detail.equals(request.candidateStates.put(id, detail))) return;
		log("candidate", "request=" + request.token + " candidate=" + id + " " + detail
				+ " elapsedMs=" + (SystemClock.elapsedRealtime() - request.started));
	}

	private void retryOrFinish(Context context, NotificationManager manager, String tag, int id, Request request, String reason) {
		if (!retry(context, manager, tag, id, request, reason)) finish(request, reason);
	}

	private boolean retry(Context context, NotificationManager manager, String tag, int id, Request request, String reason) {
		long elapsed = SystemClock.elapsedRealtime() - request.started;
		long remaining = ImagePreviewPolicy.QUALITY_WAIT_MS - elapsed;
		if (remaining <= 0 || pending.get(request.key) != request) return false;
		request.attempts++;
		if (request.attempts == 1) log("retry", "request=" + request.token + " reason=" + reason);
		long delay = Math.min(remaining, elapsed < 1000 ? 100 : 500);
		try {
			request.future = worker.schedule(() -> resolve(context, manager, tag, id, request), delay, TimeUnit.MILLISECONDS);
			return true;
		} catch (RejectedExecutionException overloaded) {
			finish(request, "queue_full");
			return true;
		}
	}

	private void finish(Request request, String reason) {
		pending.remove(request.key, request);
		largePreview.cancel(request.token);
		log("fallback", "request=" + request.token + " reason=" + reason + " scans=0");
	}

	private static BitmapFactory.Options bounds(File file) {
		BitmapFactory.Options info = new BitmapFactory.Options();
		info.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(file.getAbsolutePath(), info);
		return info;
	}

	private static BitmapFactory.Options bounds(byte[] bytes) {
		BitmapFactory.Options info = new BitmapFactory.Options();
		info.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(bytes, 0, bytes.length, info);
		return info;
	}

	private static Bitmap decode(Candidate candidate, ImagePreviewPolicy.Size target) {
		ImagePreviewPolicy.Size output = ImagePreviewPolicy.fitInside(candidate.width, candidate.height,
				target.width, target.height, ImagePreviewPolicy.MAX_PIXELS);
		int sample = ImagePreviewPolicy.sampleSize(candidate.width, candidate.height, output.width, output.height);
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = sample;
		options.inPreferredConfig = Bitmap.Config.ARGB_8888;
		Bitmap decoded = candidate.decodedBytes == null
				? BitmapFactory.decodeFile(candidate.file.getAbsolutePath(), options)
				: BitmapFactory.decodeByteArray(candidate.decodedBytes, 0, candidate.decodedBytes.length, options);
		if (decoded == null) return null;
		if (candidate.file.length() != candidate.size || candidate.file.lastModified() != candidate.modified) {
			decoded.recycle();
			return null;
		}
		ImagePreviewPolicy.Size exact = ImagePreviewPolicy.fitInside(decoded.getWidth(), decoded.getHeight(),
				target.width, target.height, ImagePreviewPolicy.MAX_PIXELS);
		if (exact.width == decoded.getWidth() && exact.height == decoded.getHeight()) return decoded;
		Bitmap scaled = Bitmap.createScaledBitmap(decoded, exact.width, exact.height, true);
		if (scaled != decoded) decoded.recycle();
		return scaled;
	}

	private static ImagePreviewPolicy.Size targetSize(Context context) {
		Resources resources = context.getResources();
		DisplayMetrics display = resources.getDisplayMetrics();
		int width = systemDimension(resources, "notification_big_picture_max_width");
		int height = systemDimension(resources, "notification_big_picture_max_height");
		if (width <= 0) width = display.widthPixels;
		if (height <= 0) height = Math.max(1, display.heightPixels / 2);
		return ImagePreviewPolicy.fitInside(width, height, width, height, ImagePreviewPolicy.MAX_PIXELS);
	}

	@SuppressLint("DiscouragedApi") // Framework notification dimensions are private resources and vary by device density.
	private static int systemDimension(Resources resources, String name) {
		int id = resources.getIdentifier(name, "dimen", "android");
		if (id == 0) return 0;
		try { return resources.getDimensionPixelSize(id); }
		catch (Resources.NotFoundException unavailable) { return 0; }
	}

	private void publish(Context context, NotificationManager manager, String tag, int id, Request request, Bitmap bitmap) {
		try {
			Notification current = null;
			boolean notificationExists = false;
			long activeToken = 0;
			for (StatusBarNotification active : manager.getActiveNotifications()) {
				if (active.getId() == id && Objects.equals(active.getTag(), tag)) {
					notificationExists = true;
					activeToken = active.getNotification().extras.getLong(TOKEN);
				}
				if (active.getId() == id && Objects.equals(active.getTag(), tag)
						&& ImageCandidateSelector.matchesRequest(request.token, active.getNotification().extras.getLong(TOKEN))) {
					current = active.getNotification();
					break;
				}
			}
			if (current == null || pending.get(request.key) != request) {
				bitmap.recycle();
				log("discarded", "request=" + request.token + " reason=notification_removed_or_replaced"
						+ " notificationExists=" + notificationExists + " activeToken=" + activeToken
						+ " pendingMatches=" + (pending.get(request.key) == request));
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
					+ (result.actions == null ? 0 : result.actions.length) + " picture=" + pictureSize(result));
			previews.put(request.key, new Preview(request, request.resolvedTalker, request.messageTime, request.token, bitmap,
					SystemClock.elapsedRealtime()));
			prunePreviews(SystemClock.elapsedRealtime());
			main.postDelayed(() -> prunePreviews(SystemClock.elapsedRealtime()), PREVIEW_KEEP_MS);
		} catch (Exception failure) {
			log("publish_failed", "request=" + request.token + " type=" + failure.getClass().getSimpleName());
		} finally {
			pending.remove(request.key, request);
			largePreview.cancel(request.token);
		}
	}

	private static String pictureSize(Notification notification) {
		Bitmap picture = notification.extras.getParcelable(Notification.EXTRA_PICTURE);
		return picture == null ? "none" : picture.getWidth() + "x" + picture.getHeight();
	}

	private void reattach(Context context, NotificationManager manager, String tag, int id, Preview preview) {
		Notification current = active(manager, tag, id);
		if (current == null || isPreview(current) || current.extras.getLong(TOKEN) != preview.token
				|| previews.get(id + ":" + tag) != preview
				|| SystemClock.elapsedRealtime() - preview.publishedAt >= PREVIEW_KEEP_MS) return;
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

	private synchronized void prunePreviews(long now) {
		for (Map.Entry<String, Preview> entry : previews.entrySet())
			if (now - entry.getValue().publishedAt >= PREVIEW_KEEP_MS) previews.remove(entry.getKey(), entry.getValue());
		long bytes = 0;
		for (Preview preview : previews.values()) bytes += preview.bytes;
		while (previews.size() > PREVIEW_CACHE || bytes > PREVIEW_CACHE_BYTES) {
			String oldest = null;
			long oldestTime = Long.MAX_VALUE;
			for (Map.Entry<String, Preview> entry : previews.entrySet())
				if (entry.getValue().publishedAt < oldestTime) { oldestTime = entry.getValue().publishedAt; oldest = entry.getKey(); }
			if (oldest == null) return;
			Preview removed = previews.remove(oldest);
			if (removed != null) bytes -= removed.bytes;
		}
	}

	private static final class Candidate {
		final File file;
		final long size, modified;
		final int width, height;
		final int quality;
		final String mime;
		final byte[] decodedBytes;
		Candidate(File file, long size, long modified, int width, int height, String mime, int quality,
				byte[] decodedBytes) {
			this.file = file; this.size = size; this.modified = modified;
			this.width = width; this.height = height; this.mime = mime; this.quality = quality;
			this.decodedBytes = decodedBytes;
		}
	}

	private static final class Scan {
		Candidate best;
		int valid;
		Scan(Candidate best) { this.best = best; }
	}

	private static final class Preview {
		final Request request;
		final String talker;
		final long messageTime, token, publishedAt;
		final int bytes;
		final Bitmap bitmap;
		Preview(Request request, String talker, long messageTime, long token, Bitmap bitmap, long publishedAt) {
			this.request = request;
			this.talker = talker;
			this.messageTime = messageTime;
			this.token = token;
			this.bitmap = bitmap;
			this.publishedAt = publishedAt;
			bytes = bitmap.getAllocationByteCount();
		}
	}

	private static final class Request {
		final List<String> messages;
		volatile String eventKey;
		List<ImageEventIndex.Path> probePaths;
		boolean probeStarted;
		final Map<String, String> probeStates = new LinkedHashMap<>();
		final Map<String, Integer> candidateIds = new LinkedHashMap<>();
		final Map<Integer, String> candidateStates = new LinkedHashMap<>();
		final String key;
		private final TalkerSource talkerSource;
		final long token = SEQUENCE.incrementAndGet(), received = System.currentTimeMillis();
		final long when, started = SystemClock.elapsedRealtime();
		int attempts;
		boolean diagnosed, pathFailureLogged;
		boolean largeLookupLogged, largeSubmitAttempted, largeDownloadComplete, publishing;
		long largeNextLookupAt;
		WeChatImageDownloader.BaseRow largeRow;
		volatile List<ImageEventIndex.Path> largePaths;
		volatile String resolvedTalker;
		volatile long messageTime;
		volatile ScheduledFuture<?> future;
		final CharSequence title, text;
		Request(String key, TalkerSource talkerSource, Notification n, List<String> messages) {
			this.messages = messages;
			this.key = key;
			this.talkerSource = talkerSource;
			when = n.when;
			title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
			text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
		}
		String talker() { return talkerSource == null ? null : talkerSource.talker(); }
	}
}
