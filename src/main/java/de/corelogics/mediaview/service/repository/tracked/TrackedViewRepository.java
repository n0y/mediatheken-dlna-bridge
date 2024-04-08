/*
 * MIT License
 *
 * Copyright (c) 2020-2024 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.service.repository.tracked;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.service.base.lucene.LuceneDirectory;
import de.corelogics.mediaview.service.base.lucene.RepoTypeFields;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;


@Log4j2
@RequiredArgsConstructor
public class TrackedViewRepository {
    @RequiredArgsConstructor
    @Getter
    enum TrackedViewField implements RepoTypeFields {
        ID(true, false),
        CLIP_ID(true, false),
        CHANNELNAME(true, true),
        CONTAINEDIN(true, true),
        TITLE(false, true),
        LAST_VIEWED_AT(true, true);

        private final boolean term;
        private final boolean sort;
    }

    private static final String DOCTYPE_TRACKEDVIEW = "tracked-view";
    private static final long SCHEMA_VERSION = 1;

    private final ScheduledExecutorService scheduledExecutorService = newSingleThreadScheduledExecutor(
        Thread.ofVirtual().name("trackedview-", 0L).factory());

    private final LuceneDirectory luceneDirectory;

    public void scheduleCleanup() {
        log.debug("Scheduling cleanup of Tracked Views every day, starting at {} (10 minutes from now)", ZonedDateTime.now().plusMinutes(10));
        scheduledExecutorService.scheduleAtFixedRate(
            this::cleanupOldTrackedViews,
            TimeUnit.MINUTES.toMinutes(10),
            TimeUnit.DAYS.toMinutes(1),
            TimeUnit.MINUTES);

    }

    private void cleanupOldTrackedViews() {
        try {
            val oldestDateToKeep = ZonedDateTime.now().minusDays(30).truncatedTo(ChronoUnit.DAYS);
            log.info("Cleaning tracked views older than {} (30 days)", oldestDateToKeep);
            luceneDirectory.performUpdate(new StandardAnalyzer(), writer ->
                writer.deleteDocuments(new BooleanQuery.Builder()
                    .add(luceneDirectory.createDoctypeQuery(DOCTYPE_TRACKEDVIEW), BooleanClause.Occur.MUST)
                    .add(
                        NumericDocValuesField.newSlowRangeQuery(
                            TrackedViewField.LAST_VIEWED_AT.sorted(),
                            Long.MIN_VALUE,
                            oldestDateToKeep.toEpochSecond()),
                        BooleanClause.Occur.MUST)
                    .build()));
        } catch (IOException e) {
            log.warn("Clould not clean up old Tracked Views", e);
        }
    }

    @SneakyThrows(IOException.class)
    public void addTrackedView(ClipEntry forClip, ZonedDateTime atTime) {
        log.debug("Adding TrackedView for {} at {}", forClip, atTime);
        val trackedViewId = STR."\{forClip.getId()}@\{atTime.truncatedTo(ChronoUnit.DAYS)}";
        val document = luceneDirectory.buildDocument(DOCTYPE_TRACKEDVIEW, SCHEMA_VERSION)
            .addField(TrackedViewField.ID, trackedViewId)
            .addField(TrackedViewField.CHANNELNAME, forClip.getChannelName())
            .addField(TrackedViewField.CLIP_ID, forClip.getId())
            .addField(TrackedViewField.CONTAINEDIN, forClip.getContainedIn())
            .addField(TrackedViewField.TITLE, forClip.getTitle())
            .addField(TrackedViewField.LAST_VIEWED_AT, atTime)
            .build();
        luceneDirectory.performUpdate(new StandardAnalyzer(), writer ->
            writer.updateDocument(
                new Term(TrackedViewField.ID.term(), TrackedViewField.ID.term(trackedViewId)),
                document));
    }

    public List<TrackedContainedIn> getRecentlySeenContainedIns(ZonedDateTime earliest, ZonedDateTime latest) {
        log.debug("Getting all Tracked Views between {} and {}", earliest, latest);
        return luceneDirectory.performSearch(searcher -> {
            val result = searcher.search(
                new BooleanQuery.Builder()
                    .add(luceneDirectory.createDoctypeQuery(DOCTYPE_TRACKEDVIEW), BooleanClause.Occur.MUST)
                    .add(NumericDocValuesField.newSlowRangeQuery(TrackedViewField.LAST_VIEWED_AT.sorted(), earliest.toEpochSecond(), latest.toEpochSecond()), BooleanClause.Occur.MUST)
                    .build(),
                1000);
            return Arrays.stream(result.scoreDocs)
                .map(doc -> luceneDirectory.loadDocument(searcher, doc.doc))
                .map(this::trackedViewFromDocument)
                .map(tv -> new TrackedContainedIn(tv.channelName(), tv.containedIn(), tv.lastViewedAt(), tv.lastViewedAt(), 1))
                .collect(Collectors.toMap(
                    tci -> STR."\{tci.channelName()}::\{tci.containedIn()}",
                    Function.identity(),
                    (one, two) -> new TrackedContainedIn(
                        one.channelName(),
                        one.containedIn(),
                        one.earliestViewed().isBefore(two.earliestViewed()) ? one.earliestViewed() : two.earliestViewed(),
                        one.earliestViewed().isBefore(two.earliestViewed()) ? two.earliestViewed() : one.earliestViewed(),
                        one.numberViewed() + two.numberViewed())))
                .values()
                .stream()
                .sorted(Comparator
                    .comparing(TrackedContainedIn::numberViewed)
                    .thenComparing(TrackedContainedIn::latestViewed, Comparator.reverseOrder()))
                .toList();
        });
    }

    private TrackedViewEntry trackedViewFromDocument(Document document) {
        return new TrackedViewEntry(
            document.get(TrackedViewField.CLIP_ID.value()),
            document.get(TrackedViewField.CHANNELNAME.value()),
            document.get(TrackedViewField.CONTAINEDIN.value()),
            document.get(TrackedViewField.TITLE.value()),
            ZonedDateTime.parse(document.get(TrackedViewField.LAST_VIEWED_AT.value())));
    }
}
