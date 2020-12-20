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

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

class ClipDownloadConnection extends Thread implements Closeable {
    private static final double REQ_MB_PER_SECOND = 1.5D;
    private final Logger logger = LogManager.getLogger();
    private final OkHttpClient httpClient;
    private final String connectionId;
    private final ClipDownloader downloader;
    private boolean stopped = false;

    public ClipDownloadConnection(String connectionId, long chunkSizeBytes, ClipDownloader downloader) {
        super(connectionId);
        this.connectionId = connectionId;
        this.downloader = downloader;
        // require at least 1.5 MB/sec download rate
        var chunkSizeMb = chunkSizeBytes / (1024D * 1024D);
        var timeoutSecs = chunkSizeMb / REQ_MB_PER_SECOND;
        var callTimeout = Duration.of((long) (timeoutSecs * 1000), ChronoUnit.MILLIS);
        logger.debug("Setting call timeout to {}", callTimeout);

        this.httpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(1, 10, TimeUnit.SECONDS))
                .callTimeout(callTimeout)
                .readTimeout(1, TimeUnit.SECONDS)
                .connectTimeout(2, TimeUnit.SECONDS)
                .build();
    }

    public void run() {
        while (!stopped) {
            var chunk = downloader.nextChunk(connectionId);
            if (chunk.isPresent()) {
                try {
                    long start = System.currentTimeMillis();
                    byte[] bytes = downloadChunk(chunk.get());
                    downloader.onChunkReceived(connectionId, chunk.get(), bytes, System.currentTimeMillis() - start);
                } catch (IOException e) {
                    downloader.onChunkError(connectionId, chunk.get(), e);
                    this.httpClient.connectionPool().evictAll();
                }
            } else {
                stopped = true;
            }
        }
        this.httpClient.connectionPool().evictAll();
        downloader.onConnectionTerminated(connectionId);
    }

    private byte[] downloadChunk(ClipChunk chunk) throws IOException {
        var request = new Request.Builder()
                .url(downloader.getUrl())
                .addHeader("Range", "bytes=" + chunk.getFrom() + "-" + chunk.getTo())
                .build();
        try (var response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body().bytes();
            }
            throw new IOException("No body, status code was " + response.code());
        }
    }


    @Override
    public void close() {

    }
}
