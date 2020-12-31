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

package de.corelogics.mediaview.client.mediathekview;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ClipIteratorTest {

    @Test
    void whenParsingCorrectTestData_thenExpectedClipEntriesAreReturned() throws IOException {
        var list = new ArrayList<ClipEntry>();
        new ClipIterator(ClipIterator.class.getResourceAsStream("/liste.example.json"))
                .forEachRemaining(list::add);

        assertThat(list).containsExactly(
                new ClipEntry(
                        "channel-name",
                        "show-title",
                        ZonedDateTime.of(2000, 1, 1, 1, 1, 0, 0, ZoneId.of("Europe/Berlin")),
                        "clip-title-1",
                        "00:10:00",
                        100L * 1024 * 1024,
                        "https://somewhere.test/1/content-base",
                        "https://somewhere.test/1/content-hd"),
                new ClipEntry(
                        "channel-name",
                        "show-title",
                        ZonedDateTime.of(2000, 1, 2, 2, 2, 0, 0, ZoneId.of("Europe/Berlin")),
                        "clip-title-2",
                        "00:20:00",
                        200L * 1024 * 1024,
                        "https://somewhere.test/2/content-base",
                        "https://somewhere.test/2/content-hd"));
    }
}
