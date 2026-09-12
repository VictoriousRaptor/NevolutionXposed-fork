package com.oasisfeng.nevo.decorators.wechat;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

/** Conservative temporal fallback, not an exact WeChat message-to-file mapping. */
final class ImageCandidateSelector {
	static final long WINDOW_MS = 2000;

	static boolean isRecent(long modified, long received) {
		return modified > 0 && modified >= received - WINDOW_MS && modified <= received + WINDOW_MS;
	}

	static File unique(List<File> candidates, Predicate<File> decodable) {
		File selected = null;
		for (File file : candidates) {
			if (!decodable.test(file)) continue;
			if (selected != null && !selected.equals(file)) return null;
			selected = file;
		}
		return selected;
	}

	static boolean matchesRequest(long expected, long actual) {
		return expected != 0 && expected == actual;
	}
}
