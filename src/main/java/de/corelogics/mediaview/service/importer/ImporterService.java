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

import de.corelogics.mediaview.client.mediatheklist.MediathekListClient;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.client.mediathekview.MediathekViewImporter;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ImporterService {
    private final Logger logger = LogManager.getLogger();

    private final MainConfiguration mainConfiguration;
    private final MediathekListClient mediathekListeClient;
    private final MediathekViewImporter importer;
    private final ClipRepository clipRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean stopped = false;

    public ImporterService(MainConfiguration mainConfiguration, MediathekListClient mediathekListeClient, MediathekViewImporter importer, ClipRepository clipRepository) {
        this.mainConfiguration = mainConfiguration;
        this.mediathekListeClient = mediathekListeClient;
        this.importer = importer;
        this.clipRepository = clipRepository;
    }


    public void scheduleImport() {
        logger.info("Starting import scheduler. Update interval: {} hours", mainConfiguration::updateIntervalFullHours);
        scheduleNextFullImport();
    }

    private void scheduleNextFullImport() {
        var now = ZonedDateTime.now();
        var nextFullUpdateAt = clipRepository
                .findLastFullImport()
                .map(t -> t.plus(mainConfiguration.updateIntervalFullHours(), ChronoUnit.HOURS))
                .filter(now::isBefore)
                .orElseGet(() -> now.plus(10, ChronoUnit.SECONDS));
        long inSeconds = ChronoUnit.SECONDS.between(now, nextFullUpdateAt);
        logger.info("Scheduling next full import at {} (in {} seconds from now)", nextFullUpdateAt, inSeconds);
        scheduler.schedule(this::fullImport, inSeconds, TimeUnit.SECONDS);
    }

    public void shutdown() {
        this.stopped = true;
        try {
            this.scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("Could not terminate.", e);
        }
    }

    private void fullImport() {
        logger.info("Starting a full import");
        var startedAt = ZonedDateTime.now();
        try {
            var entryUpdateList = new ArrayList<ClipEntry>(1000);
            var numImported = new AtomicInteger();
            try (var input = mediathekListeClient.openMediathekListeFull()) {
                var list = importer.createList(input);
                var it = list.stream().iterator();
                while (it.hasNext()) {
                    if (stopped) {
                        logger.debug("Stopped: terminating import");
                        return;
                    }
                    var e = it.next();
                    if (numImported.incrementAndGet() % 10000 == 0) {
                        logger.info("Full imported yielded {} clips until now", numImported::get);
                    }
                    entryUpdateList.add(e);
                    if (entryUpdateList.size() > 999) {
                        clipRepository.addClips(entryUpdateList, startedAt);
                        entryUpdateList.clear();
                    }
                }
                clipRepository.addClips(entryUpdateList, startedAt);
                clipRepository.deleteClipsImportedBefore(startedAt);
                clipRepository.compact();
                logger.info("Successfully performed a full import, yielding {} clips", numImported::get);
            }
        } catch (final Exception e) {
            logger.warn("Exception during import.", e);
        }
        try {
            clipRepository.updateLastFullImport(ZonedDateTime.now());
            scheduleNextFullImport();
        } catch (Exception e) {
            logger.warn("Could not schedule next full import: ", e);
        }
    }
}
