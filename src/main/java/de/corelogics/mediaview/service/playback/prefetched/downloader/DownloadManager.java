/*
 * MIT License
 *
 * Copyright (c) 2020-2024 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.service.playback.prefetched.downloader;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.service.base.lifecycle.ShutdownRegistry;
import de.corelogics.mediaview.service.base.threading.BaseThreading;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import okhttp3.OkHttpClient;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.logging.log4j.CloseableThreadContext;

import java.io.EOFException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class DownloadManager {
    private final Map<String, ClipDownloaderHolder> clipIdToDl = new HashMap<>();
    private final MainConfiguration mainConfiguration;
    private final CacheDirectory cacheDirectory;
    private final OkHttpClient httpClient;

    private final AtomicBoolean running = new AtomicBoolean(true);

    public DownloadManager(MainConfiguration mainConfiguration, BaseThreading baseThreading, ShutdownRegistry shutdownRegistry, OkHttpClient httpClient, CacheDirectory cacheDirectory) {
        this.mainConfiguration = mainConfiguration;
        this.cacheDirectory = cacheDirectory;
        this.httpClient = httpClient;
        baseThreading.schedulePeriodic(this::closeIdlingDownloaders, Duration.ofSeconds(10), Duration.ofSeconds(10));
        shutdownRegistry.registerShutdown(this::onShutdown);
    }

    private synchronized void onShutdown() {
        log.debug("Shutting down");
        running.set(false);
        clipIdToDl.entrySet().forEach(e -> e.getValue().close());
        clipIdToDl.clear();
    }

    private synchronized void closeIdlingDownloaders() {
        if (this.running.get()) {
            val startedAt = ZonedDateTime.now();
            try (val ignored = CloseableThreadContext.put("CLOSE_IDLE_STARTED_AT", startedAt.toLocalDateTime().toString())) {
                log.debug("Closing downloaders idling for 30s");
                val tooOld = startedAt.minusSeconds(30).toInstant().toEpochMilli();

                clipIdToDl.entrySet().stream()
                    .filter(e -> e.getValue().getNumberOfOpenStreams() == 0)
                    .filter(e -> e.getValue().getLastReadTs() < tooOld)
                    .peek(e -> log.debug("Expiring downloader for {}", e.getKey()))
                    .peek(e -> e.getValue().close())
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(clipIdToDl::remove);
            }
        }
    }

    private synchronized void tryToRemoveOneIdlingDownloader() {
        log.debug("Try to expire oldest idling downloader");
        clipIdToDl.entrySet().stream()
            .filter(e -> e.getValue().getNumberOfOpenStreams() == 0)
            .min(Comparator.comparingLong(entry -> entry.getValue().getLastReadTs()))
            .ifPresent(oldestEntry -> {
                log.debug("Downloader for {} is idling since {}. Closing it.", oldestEntry.getKey(), oldestEntry.getValue().getLastReadTs());
                try {
                    oldestEntry.getValue().close();
                } finally {
                    clipIdToDl.remove(oldestEntry.getKey());
                }
            });

    }

    public synchronized OpenedStream openStreamFor(ClipEntry clip, ByteRange byteRange) throws UpstreamNotFoundException, UpstreamReadFailedException, TooManyConcurrentConnectionsException, EOFException, CacheSizeExhaustedException {
        log.debug("Requested input stream for {} of {}", clip::getId, byteRange::toString);

        var clipDownloaderHolder = clipIdToDl.get(clip.getId());
        if (null == clipDownloaderHolder) {
            if (clipIdToDl.size() >= mainConfiguration.cacheMaxParallelDownloads()) {
                tryToRemoveOneIdlingDownloader();
            }
            if (clipIdToDl.size() < mainConfiguration.cacheMaxParallelDownloads()) {
                clipDownloaderHolder = new ClipDownloaderHolder(createClipDownloader(clip));
                clipIdToDl.put(clip.getId(), clipDownloaderHolder);
            }
        }
        if (null == clipDownloaderHolder) {
            throw new TooManyConcurrentConnectionsException(STR."More then \{mainConfiguration.cacheMaxParallelDownloads()} connections active. Can't proxy more.");
        } else {
            val openedStream = clipDownloaderHolder.openInputStreamStartingFrom(byteRange.getFirstPosition(), Duration.ofSeconds(20));
            if (byteRange.getLastPosition().isPresent()) {
                val length = byteRange.getLastPosition().get() - byteRange.getFirstPosition();
                openedStream.setStream(new BoundedInputStream(openedStream.getStream(), length));
            }
            return openedStream;
        }
    }

    private ClipDownloader createClipDownloader(ClipEntry clip) throws UpstreamNotFoundException, UpstreamReadFailedException, CacheSizeExhaustedException {
        CacheSizeExhaustedException exh = null;
        for (var maybeBytesAreFree = true; maybeBytesAreFree; maybeBytesAreFree = this.cacheDirectory.tryCleanupCacheDir(this.clipIdToDl.keySet())) {
            try {
                return new ClipDownloader(
                    this.mainConfiguration,
                    this.cacheDirectory,
                    this.httpClient,
                    clip.getId(),
                    clip.getBestUrl());
            } catch (final CacheSizeExhaustedException e) {
                log.debug("Cache size exhausted. Trying to clean up");
                exh = e;
            }
        }
        throw exh;
    }
}
