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
import com.google.inject.Singleton;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.service.retriever.ByteRange;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.util.IOUtils;

import javax.annotation.PostConstruct;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class DownloadManager {
    private static final int MAX_PARALLEL_DL = 4;

    private final OkHttpClient httpClient;
    private final Logger logger = LogManager.getLogger();
    private final Map<String, ClipDownloaderHolder> clipIdToDl = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DownloadManager() {
        this.httpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(1, 10, TimeUnit.SECONDS))
                .callTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    @PostConstruct
    void scheduleExpireThread() {
        scheduler.scheduleAtFixedRate(this::expire, 10, 10, TimeUnit.SECONDS);
    }

    private synchronized void expire() {
        var tooOld = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(30);
        clipIdToDl.entrySet().stream()
                .filter(e -> e.getValue().getNumberOfOpenStreams() == 0)
                .filter(e -> e.getValue().getLastReadTs() < tooOld)
                .peek(e -> logger.debug("Expiring downloader for {}", e.getKey()))
                .peek(e -> e.getValue().close())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList())
                .forEach(clipIdToDl::remove);
    }

    public synchronized Optional<InputStream> openStreamFor(ClipEntry clip, ByteRange byteRange) throws IOException {
        logger.debug("Requested input stream for {} of {}", clip::getId, byteRange::toString);

        var clipDownloaderHolder = clipIdToDl.get(clip.getId());
        if (null == clipDownloaderHolder && clipIdToDl.size() < MAX_PARALLEL_DL) {
            clipDownloaderHolder = new ClipDownloaderHolder(new ClipDownloader(clip.getId(), clip.getBestUrl()));
            clipIdToDl.put(clip.getId(), clipDownloaderHolder);
        }
        if (null == clipDownloaderHolder) {
            // too many parallel dls. Open the stream directly, no prefetching
            return fetchDirectly(clip, byteRange);
        } else {
            var inputStream = clipDownloaderHolder.openInputStreamStartingFrom(byteRange.getFirstPosition(), Duration.ofSeconds(20));
            if (byteRange.getLastPosition().isPresent()) {
                var length = byteRange.getLastPosition().get() - byteRange.getFirstPosition();
                inputStream = ByteStreams.limit(inputStream, length);
            }
            return Optional.of(inputStream);
        }
    }

    private Optional<InputStream> fetchDirectly(ClipEntry clip, ByteRange byteRange) throws IOException {
        var request = new Request.Builder()
                .url(clip.getBestUrl())
                .addHeader("Range", "bytes=" + byteRange.getFirstPosition() + "-" + byteRange.getLastPosition().map(Object::toString).orElse(""))
                .build();
        try (var response = this.httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return Optional.of(new FilterInputStream(response.body().byteStream()) {
                    @Override
                    public void close() throws IOException {
                        IOUtils.closeSilently(response);
                        super.close();
                    }
                });
            }
            if (response.code() == 404) {
                return Optional.empty();
            }
            throw new IOException(String.format("Could not download %s. Response code: %d", clip.getBestUrl(), response.code()));
        }
    }
}
