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

package de.corelogics.mediaview.repository;

import de.corelogics.mediaview.config.MainConfiguration;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.FacetsConfig;
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
import java.util.Comparator;
import java.util.function.Supplier;

@Log4j2
public class LuceneDirectory {
    public static final String DOCUMENT_FIELD_TYPE = "__type__";
    public static final String DOCUMENT_FIELD_VERSION = "__schema_version__";
    public static final String DOCUMENT_FIELD_VERSION_SORTED = "__schema_version$$sorted";

    @FunctionalInterface
    public interface SearchFunction<T> {
        T search(IndexSearcher searcher) throws IOException;
    }

    @FunctionalInterface
    public interface UpdateFunction {
        void update(IndexWriter writer) throws IOException;
    }

    public static final FieldType TYPE_NO_TOKENIZE = new FieldType();

    static {
        TYPE_NO_TOKENIZE.setTokenized(false);
        TYPE_NO_TOKENIZE.setIndexOptions(IndexOptions.DOCS);
        TYPE_NO_TOKENIZE.setStored(false);
        TYPE_NO_TOKENIZE.freeze();
    }

    private Directory index;
    private SearcherManager searcherManager;

    Supplier<Long> maxMemorySupplier = Runtime.getRuntime()::maxMemory;

    public LuceneDirectory(MainConfiguration mainConfiguration) {
        openConnection(calcIndexPath(mainConfiguration), calcCacheSize());
        log.info("Successfully opened database at {} with {} Bytes cache",
            calcIndexPath(mainConfiguration),
            calcCacheSize());
        migrationDeleteUnversioned();
    }

    private void migrationDeleteUnversioned() {
        log.debug("For schema migration, deleting all documents not containing any version or doctype");
        try {
            performUpdate(new StandardAnalyzer(), writer ->
                writer.deleteDocuments(new BooleanQuery.Builder()
                    .add(new FieldExistsQuery(DOCUMENT_FIELD_TYPE), BooleanClause.Occur.MUST_NOT)
                    .add(new FieldExistsQuery(DOCUMENT_FIELD_VERSION), BooleanClause.Occur.MUST_NOT)
                    .setMinimumNumberShouldMatch(1)
                    .build()));
        } catch (final IOException e) {
            throw new IllegalStateException("Could not perform DB schema migration.", e);
        }
    }

    private String calcIndexPath(MainConfiguration mainConfiguration) {
        return mainConfiguration.dbLocation().map(File::new).map(File::getAbsolutePath).orElse("<in-mem>");
    }

    long calcCacheSize() {
        return Math.min(Math.max(16_000_000L, maxMemorySupplier.get() - 150_000_000), 100_000_000L);
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
                document.add(new Field("ID$$term", "placeholder", TYPE_NO_TOKENIZE));
                writer.updateDocument(new Term("ID$$term", "placeholder"), document);
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
            throw new IllegalStateException(STR."Could not initialize FS directory on '\{indexPath}'.", e);
        }
    }

    public <T> T performSearch(SearchFunction<T> function) {
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

    public void performUpdate(Analyzer analyzer, UpdateFunction function) throws IOException {
        try (val writer = new IndexWriter(this.index, new IndexWriterConfig(analyzer))) {
            function.update(writer);
        }
        searcherManager.maybeRefreshBlocking();
    }

    public Query createDoctypeQuery(String docType) {
        return new TermQuery(new Term(DOCUMENT_FIELD_TYPE, docType));
    }

    public DocumentBuilder buildDocument(String docType, long schemaVersion) {
        val doc = new Document();
        doc.add(new Field(DOCUMENT_FIELD_TYPE, docType, TYPE_NO_TOKENIZE));
        doc.add(new StoredField(DOCUMENT_FIELD_VERSION, schemaVersion));
        doc.add(new NumericDocValuesField(DOCUMENT_FIELD_VERSION_SORTED, schemaVersion));
        return new DocumentBuilder(doc);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class DocumentBuilder {
        private final Document document;

        public DocumentBuilder addField(RepoTypeFields field, String source) {
            document.add(new TextField(field.value(), field.value(source), Field.Store.YES));

            if (field.isSort()) {
                document.add(new SortedDocValuesField(field.sorted(), new BytesRef(field.sorted(source))));
            }

            if (field.isTerm()) {
                document.add(new Field(field.term(), field.term(source), TYPE_NO_TOKENIZE));
                document.add(new Field(field.termLower(), field.termLower(source), TYPE_NO_TOKENIZE));
                if (!source.isBlank()) {
                    document.add(new SortedSetDocValuesFacetField(field.facet(), field.facet(source)));
                }
            }
            return this;
        }

        public DocumentBuilder addField(RepoTypeFields field, long source) {
            document.add(new StoredField(field.value(), source));

            if (field.isSort()) {
                document.add(new NumericDocValuesField(field.sorted(), source));
            }
            return this;
        }

        public DocumentBuilder addField(RepoTypeFields field, ZonedDateTime source) {
            document.add(new StoredField(field.value(), source.toString()));

            if (field.isSort()) {
                document.add(new NumericDocValuesField(field.sorted(), source.toEpochSecond()));
            }
            return this;
        }

        @SneakyThrows
        private Document applyFacets(Document d) {
            val facetsConfig = new FacetsConfig();
            for (val ixf : d) {
                if (ixf.fieldType() == SortedSetDocValuesFacetField.TYPE) {
                    val facetField = (SortedSetDocValuesFacetField) ixf;
                    facetsConfig.setIndexFieldName(facetField.dim, facetField.dim);
                    facetsConfig.setMultiValued(facetField.dim, true); // TODO: revisit this but for now all fields assumed to have multivalue
                }
            }
            return facetsConfig.build(d);
        }

        public Document build() {
            return applyFacets(document);
        }
    }

    @SneakyThrows
    public Document loadDocument(IndexSearcher searcher, int docId) {
        return searcher.storedFields().document(docId);
    }
}
