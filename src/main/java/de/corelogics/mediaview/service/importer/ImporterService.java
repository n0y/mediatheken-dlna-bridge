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

package de.corelogics.mediaview.service.importer;

import de.corelogics.mediaview.client.mediatheklist.MediathekListeClient;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.client.mediathekview.MediathekViewImporter;
import de.corelogics.mediaview.repository.clip.ClipRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class ImporterService {
    private final MediathekListeClient mediathekListeClient;
    private final MediathekViewImporter importer;
    private final ClipRepository clipRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public ImporterService(MediathekListeClient mediathekListeClient, MediathekViewImporter importer, ClipRepository clipRepository) {
        this.mediathekListeClient = mediathekListeClient;
        this.importer = importer;
        this.clipRepository = clipRepository;
    }


    public void scheduleImport() {
        scheduler.scheduleAtFixedRate(this::importNow, 1, 1, TimeUnit.DAYS);
    }

    private void importNow() {
        try {
            var list = new ArrayList<ClipEntry>(1000);
            try (var input = mediathekListeClient.openMediathekListeFull()) {
                this.importer.iterateEntries(input).forEach(e -> {
                    list.add(e);
                    if (list.size() > 999) {
                        System.out.println(e);
                        clipRepository.addClips(list);
                        list.clear();
                    }
                });
                clipRepository.addClips(list);
            }
        } catch(final Exception e) {
            e.printStackTrace();
        }
    }
}
