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

package de.corelogics.mediaview.client.mediathekview;

import de.corelogics.mediaview.util.HashingUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.ZonedDateTime;

@Getter
@EqualsAndHashCode
@ToString
public class ClipEntry {
    @EqualsAndHashCode.Exclude
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
        return HashingUtils.idHash(this.channelName, getBestUrl());
    }

    public String getBestUrl() {
        return null != urlHd && !urlHd.isBlank() ? urlHd : url;
    }
}
