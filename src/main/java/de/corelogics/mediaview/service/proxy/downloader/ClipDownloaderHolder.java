/*
 * MIT License
 *
 * Copyright (c) 2020-2021 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.service.proxy.downloader;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
class ClipDownloaderHolder {
    private final ClipDownloader clipDownloader;
    private final AtomicInteger numberOfOpenStreams = new AtomicInteger();

    @Getter
    private long lastReadTs = System.currentTimeMillis();

    public int getNumberOfOpenStreams() {
        return numberOfOpenStreams.get();
    }

    public OpenedStream openInputStreamStartingFrom(long position, Duration readTimeout) throws EOFException {
        numberOfOpenStreams.incrementAndGet();
        var metadata = clipDownloader.getMetaData();
        return new OpenedStream(
                metadata.getContentType(),
                metadata.getSize(),

                new FilterInputStream(clipDownloader.openInputStreamStartingFrom(position, readTimeout)) {
                    @Override
                    public int read() throws IOException {
                        try {
                            return super.read();
                        } finally {
                            lastReadTs = System.currentTimeMillis();
                        }
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        try {
                            return super.read(b, off, len);
                        } finally {
                            lastReadTs = System.currentTimeMillis();
                        }
                    }

                    @Override
                    public void close() throws IOException {
                        numberOfOpenStreams.updateAndGet(i -> Math.max(i - 1, 0));
                        super.close();
                    }
                });
    }

    public void close() {
        clipDownloader.close();
    }
}
