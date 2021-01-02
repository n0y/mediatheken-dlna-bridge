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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class MediathekViewImporterTest {
    private MediathekViewImporter sut = new MediathekViewImporter();

    @Test
    void whenCreatingList_thenIterateElementsAndReturnObject() throws IOException {
        var list = sut.createList(MediathekViewImporterTest.class.getResourceAsStream("/liste.example.json"));
        assertSoftly(a -> {
            a.assertThat(list.getMetaData().getValidUntil()).isPresent().get().isEqualTo("02.01.2000, 12:00");
            a.assertThat(list.getMetaData().getCreatedAt()).isPresent().get().isEqualTo("01.01.2020, 13:00");
            a.assertThat(list.getMetaData().getVersion()).isPresent().get().isEqualTo("1");
            a.assertThat(list.getMetaData().getCreator()).isPresent().get().isEqualTo("creatorName");
            a.assertThat(list.getMetaData().getHash()).isPresent().get().isEqualTo("hashCode");
            a.assertThat(list.stream()).hasSize(3);
        });
    }
}
