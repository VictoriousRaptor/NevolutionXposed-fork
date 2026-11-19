package com.oasisfeng.nevo.decorators.wechat;

import android.database.Cursor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WeChatImageDownloaderTest {
	@Test public void completedRowMustRetainBothMessageIdsAndConversation() {
		WeChatImageDownloader.BaseRow original = new WeChatImageDownloader.BaseRow(10, 42, 1000,
				"synthetic-chat", "/preview", "", "", 0, 10, 0);
		WeChatImageDownloader.Task task = new WeChatImageDownloader.Task(original, 42);
		assertFalse(task.matches(row(10, 1001, "synthetic-chat", "/preview", "", 0)));
		assertFalse(task.matches(row(11, 1000, "synthetic-chat", "/preview", "", 0)));
		assertFalse(task.matches(row(10, 1000, "other-synthetic-chat", "/preview", "", 0)));
		assertFalse(task.matches(new WeChatImageDownloader.BaseRow(10, 43, 1000, "synthetic-chat", "/preview", "", "", 10, 10, 0)));
		assertTrue(task.matches(row(10, 1000, "synthetic-chat", "/preview", "", 0)));
	}
	@Test public void sourceReadReturnsExactlyTheExpectedBytes() throws Exception {
		assertArrayEquals(new byte[]{1, 2, 3}, readSource(new byte[]{1, 2, 3}, 3));
	}

	@Test public void sourceReadRejectsTruncatedFiles() throws Exception {
		assertNull(readSource(new byte[]{1, 2}, 3));
	}

	@Test public void sourceReadRejectsBytesBeyondTheExpectedSize() throws Exception {
		assertNull(readSource(new byte[]{1, 2, 3}, 2));
	}

	private static byte[] readSource(byte[] bytes, int expectedSize) throws Exception {
		Path file = Files.createTempFile("nevo-image-source-", ".bin");
		try {
			Files.write(file, bytes);
			Method read = WeChatImageDownloader.class.getDeclaredMethod("readAll", java.io.File.class, int.class);
			read.setAccessible(true);
			return (byte[]) read.invoke(null, file.toFile(), expectedSize);
		} finally { Files.deleteIfExists(file); }
	}

	@Test public void uniqueRowIsTheBaseRow() {
		WeChatImageDownloader.BaseRow base = row(10, 1000, "chat", "", "", 0);
		assertSame(base, WeChatImageDownloader.selectBaseRow(Collections.singletonList(base), "chat"));
	}

	@Test public void onlyTheRowPointingAtAnotherRowIsSelected() {
		WeChatImageDownloader.BaseRow original = row(20, 1000, "chat", "/original", "", 0);
		WeChatImageDownloader.BaseRow base = row(10, 1000, "chat", "/base", "", 20);
		assertSame(base, WeChatImageDownloader.selectBaseRow(Arrays.asList(original, base), "chat"));
		assertEquals(Collections.singletonList("/base"), paths(base));
	}

	@Test public void multipleRowsWithoutOneBaseAreAmbiguous() {
		assertNull(WeChatImageDownloader.selectBaseRow(Arrays.asList(
				row(10, 1000, "chat", "/a", "", 0),
				row(20, 1000, "chat", "/b", "", 0)), "chat"));
	}

	@Test public void talkerMismatchFailsClosed() {
		assertNull(WeChatImageDownloader.selectBaseRow(Collections.singletonList(
				row(10, 1000, "other", "/a", "", 0)), "chat"));
	}

	@Test public void completionRequiresAllBytes() {
		WeChatImageDownloader.BaseRow partial = new WeChatImageDownloader.BaseRow(
				10, 42, 1000, "chat", "/base", "", "", 9, 10, 0);
		WeChatImageDownloader.BaseRow complete = new WeChatImageDownloader.BaseRow(
				10, 42, 1000, "chat", "/base", "", "", 10, 10, 0);
		assertFalse(partial.isComplete());
		assertTrue(complete.isComplete());
	}

	@Test public void databaseMessageIdIsUsedOnlyForTheMessagePair() throws Exception {
		String[] columns = {"id", "msglocalid", "msgSvrId", "msgTalker", "bigImgPath", "hevcPath",
				"midImgPath", "offset", "totalLen", "reserved1"};
		Object[] values = {10L, 42L, 1000L, "chat", "", "", "", 0L, 0L, 20L};
		Cursor cursor = (Cursor) Proxy.newProxyInstance(Cursor.class.getClassLoader(), new Class<?>[]{Cursor.class},
				(proxy, method, args) -> {
					switch (method.getName()) {
						case "getColumnIndexOrThrow":
							int index = Arrays.asList(columns).indexOf(args[0]);
							if (index < 0) throw new IllegalArgumentException("Unknown column");
							return index;
						case "isNull": return values[(Integer) args[0]] == null;
						case "getLong": case "getString": return values[(Integer) args[0]];
						default: throw new UnsupportedOperationException(method.getName());
					}
				});
		WeChatImageDownloader downloader = new WeChatImageDownloader();
		Method readRow = WeChatImageDownloader.class.getDeclaredMethod("readRow", Cursor.class);
		readRow.setAccessible(true);
		WeChatImageDownloader.BaseRow base = (WeChatImageDownloader.BaseRow) readRow.invoke(downloader, cursor);
		FakeDownloadService service = connectFakeService(downloader);
		assertTrue(downloader.request(base, event(42, 1000, "chat"), 1, Long.MAX_VALUE, ignored -> {}));
		downloader.cancel(1);
		assertEquals(10L, service.imageId);
		assertEquals(42L, service.message.id);
		assertEquals("chat", service.message.talker);
	}

	@Test public void matchingEventSuppliesMissingRowLocalIdForSubmitAndPoll() throws Exception {
		WeChatImageDownloader downloader = new WeChatImageDownloader();
		FakeDownloadService service = connectFakeService(downloader);
		ImageEventIndex.Entry event = event(42, 1000, "chat");
		for (long missingId : new long[]{0, -1}) {
			WeChatImageDownloader.BaseRow base = new WeChatImageDownloader.BaseRow(
					10, missingId, 1000, "chat", "/base", "", "", 10, 10, 0);
			assertTrue(downloader.request(base, event, 1, Long.MAX_VALUE, ignored -> {}));
			try {
				assertEquals(10L, service.imageId);
				assertEquals(42L, service.message.id);
				assertFalse(downloader.request(base, event(43, 1000, "chat"), 2, Long.MAX_VALUE, ignored -> {}));
				WeChatImageDownloader.Task task = new WeChatImageDownloader.Task(base, event.msgLocalId);
				assertTrue(task.matches(base));
				assertTrue(task.matches(row(10, 1000, "chat", "/base", "", 0)));
				assertFalse(task.matches(new WeChatImageDownloader.BaseRow(
						10, 43, 1000, "chat", "/base", "", "", 10, 10, 0)));
			} finally { downloader.cancel(1); }
		}
	}

	@Test public void conflictingEventCannotAuthorizeDownload() {
		WeChatImageDownloader.BaseRow base = row(10, 1000, "chat", "", "", 0);
		assertFalse(base.matchesEvent(event(42, 1001, "chat")));
		assertFalse(base.matchesEvent(event(42, 1000, "other")));
		assertFalse(base.matchesEvent(event(43, 1000, "chat")));
	}

	private static ImageEventIndex.Entry event(long localId, long serverId, String talker) {
		ImageEventIndex index = new ImageEventIndex();
		index.putIdentity(talker, localId, serverId, 100, 1);
		return index.select(talker, serverId, 100, 1, 1);
	}

	private static FakeDownloadService connectFakeService(WeChatImageDownloader downloader) throws Exception {
		FakeDownloadService service = new FakeDownloadService();
		set(downloader, "available", true);
		set(downloader, "downloadService", service);
		set(downloader, "downloadServiceClass", FakeDownloadService.class);
		set(downloader, "callbackClass", FakeCallback.class);
		set(downloader, "msgIdTalkerConstructor", FakeMsgIdTalker.class.getConstructor(long.class, String.class));
		set(downloader, "downloadMethod", FakeDownloadService.class.getMethod("b", long.class, FakeMsgIdTalker.class,
				int.class, Object.class, int.class, FakeCallback.class, int.class, boolean.class));
		return service;
	}

	private static void set(WeChatImageDownloader downloader, String name, Object value) throws Exception {
		Field field = WeChatImageDownloader.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(downloader, value);
	}

	public interface FakeCallback {}

	public static final class FakeMsgIdTalker {
		final long id;
		final String talker;
		public FakeMsgIdTalker(long id, String talker) { this.id = id; this.talker = talker; }
	}

	public static final class FakeDownloadService {
		long imageId;
		FakeMsgIdTalker message;
		public int b(long imageId, FakeMsgIdTalker message, int mode, Object context, int priority,
				FakeCallback callback, int scene, boolean original) {
			this.imageId = imageId;
			this.message = message;
			assertEquals(0, mode);
			assertNull(context);
			assertEquals(0, priority);
			assertEquals(-1, scene);
			assertFalse(original);
			return 7;
		}
	}

	private static WeChatImageDownloader.BaseRow row(long id, long serverId, String talker,
			String bigPath, String hevcPath, long reserved1) {
		return new WeChatImageDownloader.BaseRow(id, 42, serverId, talker, bigPath, hevcPath, "", 0, 0, reserved1);
	}

	private static java.util.List<String> paths(WeChatImageDownloader.BaseRow row) {
		java.util.List<String> result = new java.util.ArrayList<>();
		for (ImageEventIndex.Path path : row.paths()) result.add(path.value);
		return result;
	}
}
