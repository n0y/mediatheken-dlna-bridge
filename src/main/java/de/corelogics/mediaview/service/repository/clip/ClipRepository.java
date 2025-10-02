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

package de.corelogics.mediaview.service.repository.clip;

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
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

@Log4j2
@RequiredArgsConstructor
public class ClipRepository {
    private static final String DOCTYPE_CLIP = "clip";
    private static final String DOCTYPE_IMPORTINFO = "importinfo";
    private static final long SCHEMA_VERSION = 2;

    @RequiredArgsConstructor
    @Getter
    private enum ClipField implements RepoTypeFields {
        ID(true, false),
        CHANNELNAME(true, true),
        CONTAINEDIN(true, true),
        DURATION(true, true),
        TITLE(false, true),
        URL(false, false),
        URL_HD(false, false),
        SIZE(true, false),
        BROADCASTEDAT(true, true),
        IMPORTEDAT(true, true),

        TYPE(true, false);

        private final boolean term;
        private final boolean sort;
    }

    private final LuceneDirectory luceneDirectory;

    public Optional<ZonedDateTime> findLastFullImport() {
        log.debug("finding last full import");
        return luceneDirectory.performSearch(searcher -> {
            val result = searcher.search(
                new BooleanQuery.Builder()
                    .add(luceneDirectory.createDoctypeQuery(DOCTYPE_IMPORTINFO), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(ClipField.ID.term(), ClipField.ID.term(DOCTYPE_IMPORTINFO))), BooleanClause.Occur.MUST)
                    .build(),
                1);
            if (result.scoreDocs.length > 0) {
                val doc = searcher.getIndexReader().storedFields().document(result.scoreDocs[0].doc);
                return Optional.of(ZonedDateTime.parse(doc.get(ClipField.IMPORTEDAT.value())));
            }
            return Optional.empty();
        });
    }

    public synchronized void updateLastFullImport(ZonedDateTime dateTime) {
        log.debug("Updating last full import time to {}", dateTime);
        try {
            val document = luceneDirectory.buildDocument(DOCTYPE_IMPORTINFO, SCHEMA_VERSION)
                .addField(ClipField.ID, DOCTYPE_IMPORTINFO)
                .addField(ClipField.IMPORTEDAT, dateTime)
                .build();
            luceneDirectory.performUpdate(new StandardAnalyzer(), writer ->
                writer.updateDocument(
                    new Term(ClipField.ID.term(), ClipField.ID.term(DOCTYPE_IMPORTINFO)),
                    document));
        } catch (final IOException e) {
            throw new RuntimeException("Could not create index writer", e);
        }
    }

    public List<String> findAllChannels() {
        log.debug("Finding all channels");
        return luceneDirectory.performSearch(searcher -> {
            val query = luceneDirectory.createDoctypeQuery(DOCTYPE_CLIP);
            val state = new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), ClipField.CHANNELNAME.facet(), new FacetsConfig());
            val facetResults = FacetsCollectorManager.search(searcher, query, 10000, new FacetsCollectorManager());
            val facets = new SortedSetDocValuesFacetCounts(state, facetResults.facetsCollector());
            return Stream.of(facets.getTopChildren(10000, ClipField.CHANNELNAME.facet()).labelValues)
                .map(l -> l.label)
                .collect(Collectors.toList());
        });
    }

    /**
     * @return name to number of clips
     */
    public Map<String, Integer> findAllContainedIns(String channelName) {
        log.debug("Finding all containedIns for channel '{}'", channelName);
        return luceneDirectory.performSearch(searcher -> {
            val query = new BooleanQuery.Builder()
                .add(luceneDirectory.createDoctypeQuery(DOCTYPE_CLIP), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(ClipField.CHANNELNAME.termLower(), ClipField.CHANNELNAME.termLower(channelName))), BooleanClause.Occur.MUST)
                .build();
            val state = new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), ClipField.CONTAINEDIN.facet(), new FacetsConfig());
            val facetResults = FacetsCollectorManager.search(searcher, query, 10000, new FacetsCollectorManager());
            val facets = new SortedSetDocValuesFacetCounts(state, facetResults.facetsCollector());
            return Stream.of(facets.getTopChildren(10000, ClipField.CONTAINEDIN.facet()).labelValues)
                .map(l -> Map.entry(l.label, l.value.intValue()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        });
    }

    /**
     * @return name to number of clips
     */
    public Map<String, Integer> findAllContainedIns(String channelName, String startingWith) {
        log.debug("Finding all containedIns for channel '{}' starting with '{}'", channelName, startingWith);
        return luceneDirectory.performSearch(searcher -> {
            val query = new BooleanQuery.Builder()
                .add(luceneDirectory.createDoctypeQuery(DOCTYPE_CLIP), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(ClipField.CHANNELNAME.termLower(), ClipField.CHANNELNAME.termLower(channelName))), BooleanClause.Occur.MUST)
                .add(new PrefixQuery(new Term(ClipField.CONTAINEDIN.termLower(), ClipField.CONTAINEDIN.termLower(startingWith))), BooleanClause.Occur.MUST)
                .build();
            val state = new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), ClipField.CONTAINEDIN.facet(), new FacetsConfig());
            val facetResults = FacetsCollectorManager.search(searcher, query, 10000, new FacetsCollectorManager());
            val facets = new SortedSetDocValuesFacetCounts(state, facetResults.facetsCollector());
            return Stream.of(facets.getTopChildren(10000, ClipField.CONTAINEDIN.facet()).labelValues)
                .map(l -> Map.entry(l.label, l.value.intValue()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        });
    }

    private ClipEntry clipEntryFromDocument(Document result) {
        return new ClipEntry(
            result.get(ClipField.CHANNELNAME.value()),
            result.get(ClipField.CONTAINEDIN.value()),
            ZonedDateTime.parse(result.get(ClipField.BROADCASTEDAT.value())),
            result.get(ClipField.TITLE.value()),
            result.get(ClipField.DURATION.value()),
            result.getField(ClipField.SIZE.value()).numericValue().longValue(),
            result.get(ClipField.URL.value()),
            result.get(ClipField.URL_HD.value()));
    }

    public List<ClipEntry> findAllClips(String channelId, String containedIn) {
        log.debug("Finding all clips for channel '{}' and containedIn '{}'", channelId, containedIn);
        return luceneDirectory.performSearch(searcher -> {
            val result = searcher.search(
                new BooleanQuery.Builder()
                    .add(luceneDirectory.createDoctypeQuery(DOCTYPE_CLIP), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(ClipField.CONTAINEDIN.termLower(), ClipField.CONTAINEDIN.termLower(containedIn))), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(ClipField.CHANNELNAME.termLower(), ClipField.CHANNELNAME.termLower(channelId))), BooleanClause.Occur.MUST)
                    .build(),
                1000,
                new Sort(
                    new SortField(ClipField.BROADCASTEDAT.sorted(), SortField.Type.LONG, true),
                    new SortField(ClipField.TITLE.sorted(), SortField.Type.STRING)));
            val clipEntries = new ArrayList<ClipEntry>(result.scoreDocs.length);
            for (val doc : result.scoreDocs) {
                clipEntries.add(clipEntryFromDocument(searcher.storedFields().document(doc.doc)));
            }
            return clipEntries;
        });
    }

    public Optional<ClipEntry> findClipById(String id) {
        log.debug("Finding clip for id '{}'", id);
        return luceneDirectory.performSearch(searcher -> {
            val result = searcher.search(
                new BooleanQuery.Builder()
                    .add(luceneDirectory.createDoctypeQuery(DOCTYPE_CLIP), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(ClipField.ID.term(), ClipField.ID.term(id))), BooleanClause.Occur.MUST)
                    .build(),
                1);
            if (result.scoreDocs.length > 0) {
                return Optional.of(clipEntryFromDocument(searcher.storedFields().document(result.scoreDocs[0].doc)));
            }
            return Optional.empty();
        });
    }

    public List<ClipEntry> findAllClipsForChannelBetween(String channelName, ZonedDateTime startDate, ZonedDateTime endDate) {
        log.debug("Finding clips of channel '{}' between '{}' and '{}'", channelName, startDate, endDate);
        return luceneDirectory.performSearch(searcher -> {
            val result = searcher.search(
                new BooleanQuery.Builder()
                    .add(luceneDirectory.createDoctypeQuery(DOCTYPE_CLIP), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(ClipField.CHANNELNAME.termLower(), ClipField.CHANNELNAME.termLower(channelName))), BooleanClause.Occur.MUST)
                    .add(NumericDocValuesField.newSlowRangeQuery(ClipField.BROADCASTEDAT.sorted(), startDate.toEpochSecond(), endDate.toEpochSecond()), BooleanClause.Occur.MUST)
                    .build(),
                1000,
                new Sort(
                    new SortField(ClipField.BROADCASTEDAT.sorted(), SortField.Type.LONG),
                    new SortField(ClipField.TITLE.sorted(), SortField.Type.STRING)));
            val clipEntries = new ArrayList<ClipEntry>(result.scoreDocs.length);
            for (val doc : result.scoreDocs) {
                clipEntries.add(clipEntryFromDocument(searcher.storedFields().document(doc.doc)));
            }
            return clipEntries;
        });
    }

    public synchronized void deleteClipsImportedBefore(ZonedDateTime startedAt) {
        log.debug("Deleting all clips not imported at {}", startedAt);
        try {
            luceneDirectory.performUpdate(new StandardAnalyzer(), writer ->
                writer.deleteDocuments(
                    new BooleanQuery.Builder()
                        .add(luceneDirectory.createDoctypeQuery(DOCTYPE_CLIP), BooleanClause.Occur.MUST)
                        .add(NumericDocValuesField.newSlowRangeQuery(
                            ClipField.IMPORTEDAT.sorted(), Long.MIN_VALUE, startedAt.toEpochSecond() - 1), BooleanClause.Occur.MUST)
                        .build()));
        } catch (final IOException e) {
            throw new RuntimeException("Could not create index writer", e);
        }
    }

    @SneakyThrows(IOException.class)
    public synchronized void addClips(Iterable<ClipEntry> clipEntries, ZonedDateTime importedAt) {
        log.debug("Adding ClipEntries");
        luceneDirectory.performUpdate(new StandardAnalyzer(), writer -> {
            for (val e : clipEntries) {
                log.debug("Updating document with id '{}': '{}'", e.getId(), e.getTitle());
                val documentId = e.getId();
                val document = luceneDirectory.buildDocument(DOCTYPE_CLIP, SCHEMA_VERSION)
                    .addField(ClipField.ID, documentId)
                    .addField(ClipField.CHANNELNAME, e.getChannelName())
                    .addField(ClipField.CONTAINEDIN, e.getContainedIn())
                    .addField(ClipField.DURATION, e.getDuration())
                    .addField(ClipField.TITLE, e.getTitle())
                    .addField(ClipField.URL, e.getUrl())
                    .addField(ClipField.URL_HD, e.getUrlHd())
                    .addField(ClipField.SIZE, e.getSize())
                    .addField(ClipField.BROADCASTEDAT, e.getBroadcastedAt())
                    .addField(ClipField.IMPORTEDAT, importedAt)
                    .build();
                writer.updateDocument(
                    new Term(ClipField.ID.term(), ClipField.ID.term(documentId)),
                    document);
            }
        });
    }
}
