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

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.StringJoiner;

public class ByteRange {
    private final boolean partial;
    private final long firstPosition;
    private final Optional<Long> lastPosition;

    public ByteRange(@Nullable String optionalRangeHeader) {
        partial = null != optionalRangeHeader;
        if (partial) {
            var split = optionalRangeHeader.split("[-,=]");
            this.firstPosition = Long.parseLong(split[1]);
            if (split.length > 2) {
                this.lastPosition = Optional.of(Long.parseLong(split[2]));
            } else {
                this.lastPosition = Optional.empty();
            }
        } else {
            this.firstPosition = 0L;
            this.lastPosition = Optional.empty();
        }
    }

    public boolean isPartial() {
        return false;
    }

    public long rangeSize(long completeSize) {
        return 1 + lastPosition.orElse(completeSize) - firstPosition;
    }

    public long getFirstPosition() {
        return this.firstPosition;
    }

    public Optional<Long> getLastPosition() {
        return this.lastPosition;
    }

    @Override
    public String toString() {
        return "[" + firstPosition + "-" + lastPosition.map(Object::toString).orElse("") + "]";
    }
}
