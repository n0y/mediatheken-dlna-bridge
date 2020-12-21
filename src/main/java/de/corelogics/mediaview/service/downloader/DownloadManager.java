/*
 * MIT License
 *
 * Copyright (c) 2020 Mediatheken DLNA Bridge Authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.corelogics.mediaview.service.downloader;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;
import java.io.EOFException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class DownloadManager {
    private static final int MAX_PARALLEL_DL = 4;

    private final Logger logger = LogManager.getLogger();
    private final Map<String, ClipDownloaderHolder> clipIdToDl = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final CacheDirectory cacheDirectory;

    @Inject
    public DownloadManager(CacheDirectory cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    @PostConstruct
    void scheduleExpireThread() {
        scheduler.scheduleAtFixedRate(this::closeIdlingDownloaders, 10, 10, TimeUnit.SECONDS);
    }

    private synchronized void closeIdlingDownloaders() {
        var tooOld = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(30);
        logger.debug("Closing downloaders idling for 30s");

        clipIdToDl.entrySet().stream()
                .filter(e -> e.getValue().getNumberOfOpenStreams() == 0)
                .filter(e -> e.getValue().getLastReadTs() < tooOld)
                .peek(e -> logger.debug("Expiring downloader for {}", e.getKey()))
                .peek(e -> e.getValue().close())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList())
                .forEach(clipIdToDl::remove);
    }

    private synchronized void tryToRemoveOneIdlingDownloader() {
        logger.debug("Try to expire oldest idling downloader");
        clipIdToDl.entrySet().stream()
                .filter(e -> e.getValue().getNumberOfOpenStreams() == 0)
                .sorted(Comparator.comparingLong(entry -> entry.getValue().getLastReadTs()))
                .findFirst()
                .ifPresent(oldestEntry -> {
                    logger.debug("Downloader for {} is idling since {}. Closing it.", oldestEntry.getKey(), oldestEntry.getValue().getLastReadTs());
                    try {
                        oldestEntry.getValue().close();
                    } finally {
                        clipIdToDl.remove(oldestEntry.getKey());
                    }
                });

    }

    public synchronized OpenedStream openStreamFor(ClipEntry clip, ByteRange byteRange) throws UpstreamNotFoundException, UpstreamReadFailedException, TooManyConcurrentConnectionsException, EOFException, CacheSizeExhaustedException {
        logger.debug("Requested input stream for {} of {}", clip::getId, byteRange::toString);

        var clipDownloaderHolder = clipIdToDl.get(clip.getId());
        if (null == clipDownloaderHolder) {
            if (clipIdToDl.size() >= MAX_PARALLEL_DL) {
                tryToRemoveOneIdlingDownloader();
            }
            if (clipIdToDl.size() < MAX_PARALLEL_DL) {
                clipDownloaderHolder = new ClipDownloaderHolder(createClipDownloader(clip));
                clipIdToDl.put(clip.getId(), clipDownloaderHolder);
            }
        }
        if (null == clipDownloaderHolder) {
            throw new TooManyConcurrentConnectionsException("More then " + MAX_PARALLEL_DL + " connections active. Can't proxy more.");
        } else {
            var openedStream = clipDownloaderHolder.openInputStreamStartingFrom(byteRange.getFirstPosition(), Duration.ofSeconds(20));
            if (byteRange.getLastPosition().isPresent()) {
                var length = byteRange.getLastPosition().get() - byteRange.getFirstPosition();
                openedStream.setStream(ByteStreams.limit(openedStream.getStream(), length));
            }
            return openedStream;
        }
    }

    private ClipDownloader createClipDownloader(ClipEntry clip) throws UpstreamNotFoundException, UpstreamReadFailedException, CacheSizeExhaustedException {
        CacheSizeExhaustedException exh = null;
        for (var maybeBytesAreFree = true; maybeBytesAreFree; maybeBytesAreFree = this.cacheDirectory.tryCleanupCacheDir(this.clipIdToDl.keySet())) {
            try {
                return new ClipDownloader(this.cacheDirectory, clip.getId(), clip.getBestUrl());
            } catch (final CacheSizeExhaustedException e) {
                logger.debug("Cache size exhausted. Trying to clean up");
                exh = e;
            }
        }
        throw null == exh ? new CacheSizeExhaustedException("Cache size exhausted") : exh;
    }
}
