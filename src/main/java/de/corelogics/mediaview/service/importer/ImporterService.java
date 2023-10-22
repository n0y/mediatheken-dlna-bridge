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

package de.corelogics.mediaview.service.importer;

import de.corelogics.mediaview.client.mediatheklist.MediathekListClient;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.client.mediathekview.MediathekViewImporter;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
@RequiredArgsConstructor
public class ImporterService {
    private final MainConfiguration mainConfiguration;
    private final MediathekListClient mediathekListeClient;
    private final MediathekViewImporter importer;
    private final ClipRepository clipRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean stopped = false;


    public void scheduleImport() {
        log.info("Starting import scheduler. Update interval: {} hours", mainConfiguration::updateIntervalFullHours);
        scheduleNextFullImport();
    }

    private void scheduleNextFullImport() {
        val now = ZonedDateTime.now();
        val nextFullUpdateAt = clipRepository
            .findLastFullImport()
            .map(t -> t.plusHours(mainConfiguration.updateIntervalFullHours()))
            .filter(now::isBefore)
            .orElseGet(() -> now.plusSeconds(10));
        final long inSeconds = ChronoUnit.SECONDS.between(now, nextFullUpdateAt);
        log.info("Scheduling next full import at {} (in {} seconds from now)", nextFullUpdateAt, inSeconds);
        scheduler.schedule(this::fullImport, inSeconds, TimeUnit.SECONDS);
    }

    public void shutdown() {
        this.stopped = true;
        try {
            this.scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("Could not terminate.", e);
        }
    }

    private void fullImport() {
        log.info("Starting a full import");
        val startedAt = ZonedDateTime.now();
        try {
            val entryUpdateList = new ArrayList<ClipEntry>(1000);
            val numImported = new AtomicInteger();
            try (val input = mediathekListeClient.openMediathekListeFull()) {
                val list = importer.createList(input);
                val it = list.getStream().iterator();
                while (it.hasNext()) {
                    if (stopped) {
                        log.debug("Stopped: terminating import");
                        return;
                    }
                    val e = it.next();
                    if (numImported.incrementAndGet() % 10000 == 0) {
                        log.info("Full import yielded {} clips until now", numImported::get);
                    }
                    entryUpdateList.add(e);
                    if (entryUpdateList.size() > 999) {
                        clipRepository.addClips(entryUpdateList, startedAt);
                        entryUpdateList.clear();
                    }
                }
                clipRepository.addClips(entryUpdateList, startedAt);
                clipRepository.deleteClipsImportedBefore(startedAt);
                log.info("Successfully performed a full import, yielding {} clips", numImported::get);
            }
        } catch (final Exception e) {
            log.warn("Exception during import.", e);
        }
        try {
            clipRepository.updateLastFullImport(ZonedDateTime.now());
            scheduleNextFullImport();
        } catch (Exception e) {
            log.warn("Could not schedule next full import: ", e);
        }
    }
}
