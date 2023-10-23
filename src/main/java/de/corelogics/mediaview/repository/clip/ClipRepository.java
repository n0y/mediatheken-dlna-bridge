/*
 * MIT License
 *
 * Copyright (c) 2020-2023 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.repository.clip;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.config.MainConfiguration;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

@Log4j2
public class ClipRepository {
    private enum ClipField {
        ID(true, false),
        CHANNELNAME(true, true),
        CONTAINEDIN(true, true),
        DURATION(true, true),
        TITLE(false, true),
        URL(false, false),
        URL_HD(false, false),
        SIZE(true, false),
        BROADCASTEDAT(true, true),
        IMPORTEDAT(true, true);

        private final boolean term;
        private final boolean sort;

        ClipField(boolean term, boolean sort) {
            this.term = term;
            this.sort = sort;
        }

        public String value() {
            return this.name().toLowerCase(Locale.US);
        }

        public String value(String val) {
            return val;
        }

        public String sorted() {
            return this.value() + "$$sorted";
        }

        public String sorted(String val) {
            return val.toLowerCase(Locale.GERMANY);
        }

        public String term() {
            return this.value() + "$$term";
        }

        public String term(String val) {
            return val;
        }

        public String termLower() {
            return this.value() + "$$lowerterm";
        }

        public String termLower(String val) {
            return val.toLowerCase(Locale.GERMANY);
        }

        public String facet() {
            return this.value() + "$$facet";
        }

        public String facet(String val) {
            return val;
        }

        public boolean isTerm() {
            return term;
        }

        public boolean isSort() {
            return sort;
        }
    }

    @FunctionalInterface
    interface SearchFunction<T> {
        T search(IndexSearcher searcher) throws IOException;
    }

    private static final String DOCID_LAST_UPDATED = "last-update-stat";
    private static final FieldType TYPE_NO_TOKENIZE = new FieldType();

    static {
        TYPE_NO_TOKENIZE.setTokenized(false);
        TYPE_NO_TOKENIZE.setIndexOptions(IndexOptions.DOCS);
        TYPE_NO_TOKENIZE.setStored(false);
        TYPE_NO_TOKENIZE.freeze();
    }

    private final MainConfiguration mainConfiguration;
    private Directory index;
    private SearcherManager searcherManager;
    Supplier<Long> maxMemorySupplier = Runtime.getRuntime()::maxMemory;

    public ClipRepository(MainConfiguration mainConfiguration) {
        this.mainConfiguration = mainConfiguration;
        openConnection(calcIndexPath(), calcCacheSize());
        initialize();
        log.info("Successfully opened database at {} with {} Bytes cache",
            this::calcIndexPath,
            this::calcCacheSize);
    }

    long calcCacheSize() {
        return Math.min(Math.max(16_000_000L, maxMemorySupplier.get() - 150_000_000), 100_000_000L);
    }

    String calcIndexPath() {
        return mainConfiguration.dbLocation().map(File::new).map(File::getAbsolutePath).orElse("<in-mem>");
    }

    void initialize() {
        val indexPath = calcIndexPath();
        if (!"<in-mem>".equals(indexPath)) {
            log.debug("Looking for old clipdb entries in '{}'", indexPath);
            ofNullable(new File(indexPath).getParentFile().listFiles((dir, name) -> name.startsWith("clipdb.")))
                .stream()
                .flatMap(Stream::of)
                .peek(file -> log.info("Removing old clip database file '{}'", file))
                .filter(f -> !f.delete())
                .forEach(file -> log.warn("Could not remove old clip database file '{}'", file.getAbsolutePath()));
        }

    }

    void openConnection(String indexPath, long cacheSize) {
        try {
            if ("<in-mem>".equals(indexPath)) {
                this.index = new ByteBuffersDirectory();
            } else {
                this.index = new NIOFSDirectory(new File(indexPath).toPath());
            }

            try {
                // need at least one entry for a reader to work on...
                val writer = new IndexWriter(this.index, new IndexWriterConfig(new StandardAnalyzer()));
                val document = new Document();
                document.add(new Field(ClipField.ID.term(), ClipField.ID.term("placeholder"), TYPE_NO_TOKENIZE));
                writer.updateDocument(new Term(ClipField.ID.term(), ClipField.ID.term("placeholder")), document);
                writer.close();
            } catch (IOException | IllegalArgumentException e) {
                // index got corrupted (or it's an old version). Delete index and re-index later.
                this.index.close();
                Files.walk(new File(indexPath).toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
                openConnection(indexPath, cacheSize);
                return;
            }

            IndexSearcher.setDefaultQueryCache(new LRUQueryCache(1000, cacheSize));
            this.searcherManager = new SearcherManager(this.index, null);
        } catch (final IOException e) {
            throw new IllegalStateException("Could not initialize FS directory on '" + indexPath + "'.", e);
        }
    }

    private <T> T withSearcher(SearchFunction<T> function) {
        try {
            val searcher = searcherManager.acquire();
            try {
                return function.search(searcher);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (final IOException e) {
            throw new RuntimeException("Could not perform search.", e);
        }
    }

    public Optional<ZonedDateTime> findLastFullImport() {
        log.debug("finding last full import");
        return withSearcher(searcher -> {
            val result = searcher.search(new TermQuery(new Term(ClipField.ID.term(), ClipField.ID.term(DOCID_LAST_UPDATED))), 1);
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
            val analyzer = new StandardAnalyzer();
            val indexWriterConfig = new IndexWriterConfig(analyzer);
            try (val writer = new IndexWriter(this.index, indexWriterConfig)) {
                val d = new Document();
                addToDocument(d, ClipField.ID, DOCID_LAST_UPDATED);
                addDateToDocument(d, ClipField.IMPORTEDAT, dateTime);
                writer.updateDocument(
                    new Term(ClipField.ID.term(), ClipField.ID.term(DOCID_LAST_UPDATED)),
                    applyFacets(d));
            }
            searcherManager.maybeRefreshBlocking();
        } catch (final IOException e) {
            throw new RuntimeException("Could not create index writer", e);
        }
    }

    public List<String> findAllChannels() {
        log.debug("Finding all channels");
        return withSearcher(searcher -> {
            val query = new MatchAllDocsQuery();
            val state = new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), ClipField.CHANNELNAME.facet(), null);
            val fc = new FacetsCollector();
            FacetsCollector.search(searcher, query, 10000, fc);
            val facets = new SortedSetDocValuesFacetCounts(state, fc);
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
        return withSearcher(searcher -> {
            val query = new TermQuery(new Term(ClipField.CHANNELNAME.termLower(), ClipField.CHANNELNAME.termLower(channelName)));
            val state = new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), ClipField.CONTAINEDIN.facet(), null);
            val fc = new FacetsCollector();
            FacetsCollector.search(searcher, query, 10000, fc);
            val facets = new SortedSetDocValuesFacetCounts(state, fc);
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
        return withSearcher(searcher -> {
            val query = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(ClipField.CHANNELNAME.termLower(), ClipField.CHANNELNAME.termLower(channelName))), BooleanClause.Occur.MUST)
                .add(new PrefixQuery(new Term(ClipField.CONTAINEDIN.termLower(), ClipField.CONTAINEDIN.termLower(startingWith))), BooleanClause.Occur.MUST)
                .build();
            val state = new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), ClipField.CONTAINEDIN.facet(), null);
            val fc = new FacetsCollector();
            FacetsCollector.search(searcher, query, 10000, fc);
            val facets = new SortedSetDocValuesFacetCounts(state, fc);
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
        return withSearcher(searcher -> {
            val result = searcher.search(
                new BooleanQuery.Builder()
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
        return withSearcher(searcher -> {
            val result = searcher.search(new TermQuery(new Term(ClipField.ID.term(), ClipField.ID.term(id))), 1);
            if (result.scoreDocs.length > 0) {
                return Optional.of(clipEntryFromDocument(searcher.storedFields().document(result.scoreDocs[0].doc)));
            }
            return Optional.empty();
        });
    }

    public List<ClipEntry> findAllClipsForChannelBetween(String channelName, ZonedDateTime startDate, ZonedDateTime endDate) {
        log.debug("Finding clips of channel '{}' between '{}' and '{}'", channelName, startDate, endDate);
        return withSearcher(searcher -> {
            val result = searcher.search(
                new BooleanQuery.Builder()
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
            val analyzer = new StandardAnalyzer();
            val indexWriterConfig = new IndexWriterConfig(analyzer);
            try (val writer = new IndexWriter(this.index, indexWriterConfig)) {
                writer.deleteDocuments(
                    NumericDocValuesField.newSlowRangeQuery(
                        ClipField.IMPORTEDAT.sorted(), Long.MIN_VALUE, startedAt.toEpochSecond() - 1));
            }
            searcherManager.maybeRefreshBlocking();
        } catch (final IOException e) {
            throw new RuntimeException("Could not create index writer", e);
        }
    }

    public synchronized void addClips(Iterable<ClipEntry> clipEntries, ZonedDateTime importedAt) {
        log.debug("Adding ClipEntries");
        try {
            val analyzer = new StandardAnalyzer();
            val indexWriterConfig = new IndexWriterConfig(analyzer);
            try (val writer = new IndexWriter(this.index, indexWriterConfig)) {
                for (val e : clipEntries) {
                    log.debug("Updating document with id '{}': '{}'", e.getId(), e.getTitle());
                    val d = new Document();
                    addToDocument(d, ClipField.ID, e.getId());
                    addToDocument(d, ClipField.CHANNELNAME, e.getChannelName());
                    addToDocument(d, ClipField.CONTAINEDIN, e.getContainedIn());
                    addToDocument(d, ClipField.DURATION, e.getDuration());
                    addToDocument(d, ClipField.TITLE, e.getTitle());
                    addToDocument(d, ClipField.URL, e.getUrl());
                    addToDocument(d, ClipField.URL_HD, e.getUrlHd());
                    addLongToDocument(d, ClipField.SIZE, e.getSize());
                    addDateToDocument(d, ClipField.BROADCASTEDAT, e.getBroadcastedAt());
                    addDateToDocument(d, ClipField.IMPORTEDAT, importedAt);
                    writer.updateDocument(
                        new Term(ClipField.ID.term(), ClipField.ID.term(e.getId())),
                        applyFacets(d));
                }
            }
            searcherManager.maybeRefreshBlocking();
        } catch (final IOException e) {
            throw new RuntimeException("Could not create index writer", e);
        }
    }

    private Document applyFacets(Document d) throws IOException {
        val facetsConfig = new FacetsConfig();
        for (val ixf : d) {
            if (ixf.fieldType() == SortedSetDocValuesFacetField.TYPE) {
                SortedSetDocValuesFacetField facetField = (SortedSetDocValuesFacetField) ixf;
                facetsConfig.setIndexFieldName(facetField.dim, facetField.dim);
                facetsConfig.setMultiValued(facetField.dim, true); // TODO: revisit this but for now all fields assumed to have multivalue
            }
        }
        d = facetsConfig.build(d);
        return d;
    }

    private void addToDocument(Document doc, ClipField field, String source) {
        doc.add(new TextField(field.value(), field.value(source), Field.Store.YES));

        if (field.isSort()) {
            doc.add(new SortedDocValuesField(field.sorted(), new BytesRef(field.sorted(source))));
        }

        if (field.isTerm()) {
            doc.add(new Field(field.term(), field.term(source), TYPE_NO_TOKENIZE));
            doc.add(new Field(field.termLower(), field.termLower(source), TYPE_NO_TOKENIZE));
            if (!source.isBlank()) {
                doc.add(new SortedSetDocValuesFacetField(field.facet(), field.facet(source)));
            }
        }
    }

    private void addLongToDocument(Document doc, ClipField field, long source) {
        doc.add(new StoredField(field.value(), source));

        if (field.isSort()) {
            doc.add(new NumericDocValuesField(field.sorted(), source));
        }
    }

    private void addDateToDocument(Document doc, ClipField field, ZonedDateTime source) {
        doc.add(new StoredField(field.value(), source.toString()));

        if (field.isSort()) {
            doc.add(new NumericDocValuesField(field.sorted(), source.toEpochSecond()));
        }
    }
}
