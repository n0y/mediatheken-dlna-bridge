/*
 * MIT License
 *
 * Copyright (c) 2020-2025 Mediatheken DLNA Bridge Authors.
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
import de.corelogics.mediaview.service.base.lifecycle.ShutdownRegistry;
import de.corelogics.mediaview.service.base.threading.BaseThreading;
import de.corelogics.mediaview.service.repository.clip.ClipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.logging.log4j.CloseableThreadContext;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;


@Log4j2
@RequiredArgsConstructor
public final class ImporterService {
    private final MainConfiguration mainConfiguration;
    private final BaseThreading baseThreading;
    private final ShutdownRegistry shutdownRegistry;
    private final MediathekListClient mediathekListeClient;
    private final MediathekViewImporter importer;
    private final ClipRepository clipRepository;

    Supplier<ZonedDateTime> currentTimeProvider = ZonedDateTime::now;

    private final AtomicBoolean stopped = new AtomicBoolean(false);


    public void scheduleImport() {
        log.info("Starting import scheduler. Update interval: {} hours", mainConfiguration::updateIntervalFullHours);
        scheduleNextFullImport();
        this.shutdownRegistry.registerShutdown(this::shutdown);
    }

    private void scheduleNextFullImport() {
        val now = this.currentTimeProvider.get();
        val nextFullUpdateAt = clipRepository
            .findLastFullImport()
            .map(t -> t.plusHours(mainConfiguration.updateIntervalFullHours()))
            .filter(now::isBefore)
            .orElseGet(() -> now.plusSeconds(10));
        final long inSeconds = ChronoUnit.SECONDS.between(now, nextFullUpdateAt);
        log.info("Scheduling next full import at {} (in {} seconds from now)", nextFullUpdateAt, inSeconds);
        baseThreading.schedule(this::fullImport, Duration.ofSeconds(inSeconds));
    }

    void shutdown() {
        log.debug("Shutting down");
        this.stopped.set(true);
    }

    void fullImport() {
        val startedAt = this.currentTimeProvider.get();
        try (val ignored = CloseableThreadContext.put("IMPORT_STARTED", startedAt.toLocalDateTime().toString())) {
            log.info("Starting a full import");
            try {
                var entryUpdateList = new ArrayList<ClipEntry>(1000);
                val numImported = new AtomicInteger();
                try (val input = mediathekListeClient.openMediathekListeFull()) {
                    val list = importer.createList(input);
                    val it = list.getStream().iterator();

                    while (it.hasNext()) {
                        if (stopped.get()) {
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
                            entryUpdateList = new ArrayList<>(1000);
                        }
                    }
                    if (!entryUpdateList.isEmpty()) {
                        clipRepository.addClips(entryUpdateList, startedAt);
                    }
                    clipRepository.deleteClipsImportedBefore(startedAt);
                    log.info("Successfully performed a full import, yielding {} clips", numImported::get);
                }
            } catch (final IOException | RuntimeException e) {
                log.warn("Exception during import.", e);
            }
            try {
                clipRepository.updateLastFullImport(this.currentTimeProvider.get());
                scheduleNextFullImport();
            } catch (Exception e) {
                log.warn("Could not schedule next full import: ", e);
            }
        }
    }
}
