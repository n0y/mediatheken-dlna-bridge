/*
 * MIT License
 *
 * Copyright (c) 2020-2025 Mediatheken DLNA Bridge Authors.
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

import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.util.HttpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.Closeable;
import java.io.IOException;

@Log4j2
@RequiredArgsConstructor
class ClipDownloadConnection implements Runnable, Closeable {
    private final ClipDownloader downloader;
    private final MainConfiguration mainConfiguration;
    private final OkHttpClient httpClient;
    private final String connectionId;
    private boolean stopped = false;

    public void run() {
        while (!stopped) {
            val chunk = downloader.nextChunk(connectionId);
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
        downloader.onConnectionTerminated(connectionId);
    }

    private byte[] downloadChunk(ClipChunk chunk) throws IOException {
        val request =
            HttpUtils.enhanceRequest(
                    this.mainConfiguration,
                    new Request.Builder()
                        .url(downloader.getUrl())
                        .addHeader(HttpUtils.HEADER_RANGE, "bytes=" + chunk.from() + "-" + chunk.to()))
                .build();
        try (val response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body().bytes();
            }
            throw new IOException("No body, status code was " + response.code());
        }
    }


    @Override
    public void close() {
        this.stopped = true;
    }
}
