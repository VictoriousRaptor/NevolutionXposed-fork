package com.oasisfeng.nevo.decorators.wechat;

import com.oasisfeng.nevo.decorators.wechat.ImagePreviewLoader.Candidate;
import com.oasisfeng.nevo.decorators.wechat.ImagePreviewLoader.CandidateCache;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ImagePreviewCandidateCacheTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test public void unchangedFileReusesConversionAcrossRetries() throws Exception {
        CandidateCache cache = new CandidateCache();
        CandidateCache.Owner owner = new CandidateCache.Owner();
        File source = source();
        AtomicInteger conversions = new AtomicInteger();
        Candidate first = null;
        for (int retry = 0; retry < 5; retry++) {
            Candidate current = cache.get(owner, source, source.length(), source.lastModified());
            if (current == null) {
                conversions.incrementAndGet();
                current = candidate(source, new byte[]{1, 2});
            }
            cache.retain(owner, current);
            if (first == null) first = current;
            assertSame(first, current);
        }
        assertEquals(1, conversions.get());
    }

    @Test public void changedPathSizeOrModifiedTimeCannotReuseBytes() throws Exception {
        CandidateCache cache = new CandidateCache();
        CandidateCache.Owner owner = new CandidateCache.Owner();
        File source = source();
        Candidate converted = candidate(source, new byte[]{1});
        cache.retain(owner, converted);
        assertNull(cache.get(owner, source(), converted.size, converted.modified));
        assertSame(converted, cache.get(owner, source, converted.size, converted.modified));
        assertNull(cache.get(owner, source, converted.size + 1, converted.modified));
        cache.retain(owner, converted);
        assertNull(cache.get(owner, source, converted.size, converted.modified + 1));
    }

    @Test public void onlyOneRequestRetainsTheBestConvertedCandidate() throws Exception {
        CandidateCache cache = new CandidateCache();
        CandidateCache.Owner first = new CandidateCache.Owner(), second = new CandidateCache.Owner();
        Candidate a = candidate(source(), new byte[]{1}), b = candidate(source(), new byte[]{2});
        cache.retain(first, a);
        cache.retain(second, b);
        cache.close(first);
        assertNull(cache.get(first, a.file, a.size, a.modified));
        assertSame(b, cache.get(second, b.file, b.size, b.modified));
        cache.retain(second, null); // The path disappeared from the current candidate list.
        assertNull(cache.get(second, b.file, b.size, b.modified));
    }

    @Test public void changedFilesAndOversizedConversionsAreNotRetained() throws Exception {
        CandidateCache cache = new CandidateCache();
        CandidateCache.Owner owner = new CandidateCache.Owner();
        Candidate changed = candidate(source(), new byte[]{1});
        Files.write(changed.file.toPath(), new byte[]{1, 2, 3});
        cache.retain(owner, changed);
        assertNull(cache.get(owner, changed.file, changed.size, changed.modified));
        Candidate oversized = candidate(source(), new byte[20 * 1024 * 1024 + 1]);
        cache.retain(owner, oversized);
        assertNull(cache.get(owner, oversized.file, oversized.size, oversized.modified));
    }

    @Test public void cancellationRejectsConversionThatReturnsLater() throws Exception {
        CandidateCache cache = new CandidateCache();
        CandidateCache.Owner owner = new CandidateCache.Owner();
        Candidate converted = candidate(source(), new byte[]{1});
        CountDownLatch decoding = new CountDownLatch(1), complete = new CountDownLatch(1);
        FutureTask<Void> task = new FutureTask<>(() -> {
            decoding.countDown();
            assertTrue(complete.await(5, TimeUnit.SECONDS));
            cache.retain(owner, converted);
            return null;
        });
        new Thread(task, "synthetic-conversion").start();
        try {
            assertTrue(decoding.await(5, TimeUnit.SECONDS));
            cache.close(owner);
        } finally {
            complete.countDown();
        }
        task.get(5, TimeUnit.SECONDS);
        assertNull(cache.get(owner, converted.file, converted.size, converted.modified));
    }

    private File source() throws Exception {
        File file = files.newFile();
        Files.write(file.toPath(), new byte[]{1, 2});
        return file;
    }

    private static Candidate candidate(File file, byte[] bytes) {
        return new Candidate(file, file.length(), file.lastModified(), 40, 80, "image/png",
                ImagePreviewPolicy.QUALITY_HD, bytes);
    }
}
