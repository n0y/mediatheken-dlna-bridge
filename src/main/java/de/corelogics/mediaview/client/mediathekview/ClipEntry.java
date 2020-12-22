/*
 * MIT License
 *
 * Copyright (c) 2020 corelogics.de
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

package de.corelogics.mediaview.client.mediathekview;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.StringJoiner;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ClipEntry {
    private static final Base64.Encoder BASE64ENC = Base64.getEncoder().withoutPadding();
    private static final HashFunction HASHING = Hashing.sipHash24();

    private final String id;
    private final String title;
    private final String containedIn;
    private final ZonedDateTime broadcastedAt;
    private final String channelName;
    private final long size;
    private final String url;
    private final String urlHd;
    private final String duration;

    public ClipEntry(String channelName, String containedIn, ZonedDateTime broadcastedAt, String title, String duration, long size, String url, String urlHd) {
        this.containedIn = containedIn;
        this.title = title;
        this.broadcastedAt = broadcastedAt;
        this.channelName = channelName;
        this.size = size;
        this.url = url;
        this.urlHd = urlHd;
        this.duration = duration;
        this.id = createId();
    }

    public String createId() {
        return BASE64ENC.encodeToString(
                HASHING.newHasher()
                        .putString(this.channelName, UTF_8)
                        .putString(getBestUrl(), UTF_8)
                        .hash().asBytes());
    }

    public String getTitle() {
        return title;
    }

    public String getContainedIn() {
        return containedIn;
    }

    public ZonedDateTime getBroadcastedAt() {
        return broadcastedAt;
    }

    public String getChannelName() {
        return channelName;
    }

    public long getSize() {
        return size;
    }

    public String getUrl() {
        return url;
    }

    public String getUrlHd() {
        return urlHd;
    }

    public String getBestUrl() {
        return null != urlHd && !urlHd.isBlank() ? urlHd : url;
    }

    public String getDuration() {
        return duration;
    }

    public String getId() {
        return this.id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClipEntry)) return false;
        ClipEntry clipEntry = (ClipEntry) o;
        return size == clipEntry.size &&
                Objects.equals(title, clipEntry.title) &&
                Objects.equals(containedIn, clipEntry.containedIn) &&
                Objects.equals(broadcastedAt.toEpochSecond(), clipEntry.broadcastedAt.toEpochSecond()) &&
                Objects.equals(channelName, clipEntry.channelName) &&
                Objects.equals(url, clipEntry.url) &&
                Objects.equals(urlHd, clipEntry.urlHd) &&
                Objects.equals(duration, clipEntry.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, containedIn, broadcastedAt.toEpochSecond(), channelName, size, url, urlHd, duration);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ClipEntry.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("title='" + title + "'")
                .add("containedIn='" + containedIn + "'")
                .add("broadcastedAt=" + broadcastedAt)
                .add("channelName='" + channelName + "'")
                .add("size=" + size)
                .add("url='" + url + "'")
                .add("urlHd='" + urlHd + "'")
                .add("duration='" + duration + "'")
                .toString();
    }

}
