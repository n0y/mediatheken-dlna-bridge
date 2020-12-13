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

import com.netflix.governator.annotations.Configuration;
import de.corelogics.mediaview.client.mediatheklist.MediathekListClient;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.client.mediathekview.MediathekViewImporter;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ImporterService {
    private final Logger logger = LogManager.getLogger();

    @Configuration("UPDATEINTERVAL_FULL_HOURS")
    private int updateIntervalFullHours = 24;

    private final MediathekListClient mediathekListeClient;
    private final MediathekViewImporter importer;
    private final ClipRepository clipRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public ImporterService(MediathekListClient mediathekListeClient, MediathekViewImporter importer, ClipRepository clipRepository) {
        this.mediathekListeClient = mediathekListeClient;
        this.importer = importer;
        this.clipRepository = clipRepository;
    }


    public void scheduleImport() {
        logger.info("Starting import scheduler. Update interval: {} hours", updateIntervalFullHours);
        scheduleNextFullImport();
    }

    private void scheduleNextFullImport() {
        var now = ZonedDateTime.now();
        var nextFullUpdateAt = clipRepository
                .findLastFullImport()
                .map(t -> t.plus(updateIntervalFullHours, ChronoUnit.HOURS))
                .filter(now::isAfter)
                .orElseGet(() -> now.plus(10, ChronoUnit.SECONDS));
        long inSeconds = ChronoUnit.SECONDS.between(now, nextFullUpdateAt);
        logger.info("Scheduling next full import at {} (in {} seconds from now)", nextFullUpdateAt, inSeconds);
        scheduler.schedule(this::fullImport, inSeconds, TimeUnit.SECONDS);
    }

    private void fullImport() {
        logger.info("Starting a full import");
        try {
            var entryUpdateList = new ArrayList<ClipEntry>(1000);
            var numImported = new AtomicInteger();
            try (var input = mediathekListeClient.openMediathekListeFull()) {
                var list = importer.createList(input);
                list.stream().forEach(e -> {
                    if (numImported.incrementAndGet() % 10000 == 0) {
                        logger.info("Full imported yielded {} clips until now", numImported::get);
                    }
                    entryUpdateList.add(e);
                    if (entryUpdateList.size() > 999) {
                        clipRepository.addClips(entryUpdateList);
                        entryUpdateList.clear();
                    }
                });
                clipRepository.addClips(entryUpdateList);
                clipRepository.updateLastFullImport(ZonedDateTime.now());
                logger.info("Successfully performed a full import, yielding {} clips", numImported::get);
                scheduleNextFullImport();
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}
