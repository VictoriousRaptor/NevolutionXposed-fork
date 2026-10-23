package com.oasisfeng.nevo.decorators.wechat;

import android.database.Cursor;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WeChatImageDownloaderTest {
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

	@Test public void completionRequiresTheSameBaseRowAndAllBytes() {
		WeChatImageDownloader.BaseRow base = row(10, 1000, "chat", "/base", "", 0);
		WeChatImageDownloader.BaseRow partial = new WeChatImageDownloader.BaseRow(
				10, 42, 1000, "chat", "/base", "", "", 9, 10, 0);
		WeChatImageDownloader.BaseRow complete = new WeChatImageDownloader.BaseRow(
				10, 42, 1000, "chat", "/base", "", "", 10, 10, 0);
		assertFalse(partial.isComplete(base));
		assertFalse(complete.isComplete(row(11, 1000, "chat", "/wrong", "", 0)));
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
		FakeDownloadService service = new FakeDownloadService();
		set(downloader, "downloadService", service);
		set(downloader, "downloadServiceClass", FakeDownloadService.class);
		set(downloader, "callbackClass", FakeCallback.class);
		set(downloader, "msgIdTalkerConstructor", FakeMsgIdTalker.class.getConstructor(long.class, String.class));
		set(downloader, "downloadMethod", FakeDownloadService.class.getMethod("b", long.class, FakeMsgIdTalker.class,
				int.class, Object.class, int.class, FakeCallback.class, int.class, boolean.class));
		Method submit = WeChatImageDownloader.class.getDeclaredMethod("submit", WeChatImageDownloader.BaseRow.class);
		submit.setAccessible(true);
		assertEquals(7, submit.invoke(downloader, base));
		assertEquals(10L, service.imageId);
		assertEquals(42L, service.message.id);
		assertEquals("chat", service.message.talker);
	}

	@Test public void missingMessageLocalIdSkipsDownloadButKeepsTheBaseRow() throws Exception {
		WeChatImageDownloader downloader = new WeChatImageDownloader();
		set(downloader, "available", true);
		for (long localId : new long[]{0, -1}) {
			WeChatImageDownloader.BaseRow base = new WeChatImageDownloader.BaseRow(
					10, localId, 1000, "chat", "/base", "", "", 10, 10, 0);
			assertSame(base, WeChatImageDownloader.selectBaseRow(Collections.singletonList(base), "chat"));
			assertTrue(base.isComplete(base));
			assertFalse(downloader.request(base, 1, Long.MAX_VALUE, ignored -> {}));
		}
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
