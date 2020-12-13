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

import de.corelogics.mediaview.repository.clip.ClipRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class MediathekViewImporterTest {
    ClipRepository repo = new ClipRepository();

    @Test
    @RepeatedTest(200)
    void iterate_test() throws IOException {

//        var list = new ArrayList<ClipEntry>(1000);
//        new MediathekViewImporter().iterateEntries().forEach(e -> {
//            list.add(e);
//            if (list.size() > 999) {
//                System.out.println(e);
//                repo.addClips(list);
//                list.clear();
//            }
//        });
//        repo.addClips(list);
//        repo.find();

//        assertThat(new MediathekViewImporter().iterateEntries()).hasSize(431773);
    }
}
