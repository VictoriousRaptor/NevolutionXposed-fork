package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

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
				10, 1000, "chat", "/base", "", "", 9, 10, 0);
		WeChatImageDownloader.BaseRow complete = new WeChatImageDownloader.BaseRow(
				10, 1000, "chat", "/base", "", "", 10, 10, 0);
		assertFalse(partial.isComplete(base));
		assertFalse(complete.isComplete(row(11, 1000, "chat", "/wrong", "", 0)));
	}

	private static WeChatImageDownloader.BaseRow row(long id, long serverId, String talker,
			String bigPath, String hevcPath, long reserved1) {
		return new WeChatImageDownloader.BaseRow(id, serverId, talker, bigPath, hevcPath, "", 0, 0, reserved1);
	}

	private static java.util.List<String> paths(WeChatImageDownloader.BaseRow row) {
		java.util.List<String> result = new java.util.ArrayList<>();
		for (ImageEventIndex.Path path : row.paths()) result.add(path.value);
		return result;
	}
}
