package de.corelogics.mediaview.service.downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.BitSet;
import java.util.concurrent.TimeUnit;

public class ClipDownloader implements Closeable {
    private static final long CHUNK_SIZE_BYTES = 1_000_000;
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new BitsetSerializerModule());

    private final Logger logger = LogManager.getLogger();
    private final File cacheDir = new File("cache");
    private final String url;
    private final String id;
    private final OkHttpClient httpClient;
    private final RandomAccessFile metaDataFile;
    private final RandomAccessFile contentFile;
    private ClipMetadata metadata;

    private static class ClipMetadata {
        private String contentType;
        private long size;
        private BitSet bitSet;

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        @Override
        public String toString() {
            return "ClipMetadata{" +
                    "contentType='" + contentType + '\'' +
                    ", size=" + size +
                    '}';
        }

        public void setBitSet(BitSet bitSet) {
            this.bitSet = bitSet;
        }

        public BitSet getBitSet() {
            return bitSet;
        }
    }

    public ClipDownloader(String url, String id) {
        try {
            this.url = url;
            this.id = id;
            this.metaDataFile = new RandomAccessFile(new File(this.cacheDir, this.id + ".metadata"), "rw");
            this.contentFile = new RandomAccessFile(new File(this.cacheDir, this.id), "rw");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw new IllegalStateException("");
            }
            this.httpClient = new OkHttpClient.Builder()
                    .connectionPool(new ConnectionPool(1, 10, TimeUnit.SECONDS))
                    .callTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(1, TimeUnit.SECONDS)
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .build();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void run() {
        logger.debug("Starting download for {}", this.url);
        this.metadata = loadOrFetchMetaData();
        logger.debug("Initialized metadata to {}", this.metadata);
        initializeBitsetIfNotExists();
        updateMetadataFile();
        updateContentFile();
    }

    private void initializeBitsetIfNotExists() {
        if (null == this.metadata.getBitSet())  {
            var noChunks = (int) (this.metadata.getSize() / CHUNK_SIZE_BYTES);
            this.metadata.setBitSet(new BitSet(noChunks));
        }
    }

    private void updateContentFile() {
        try {
            this.contentFile.setLength(this.metadata.getSize());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateMetadataFile() {
        try {
            this.metaDataFile.seek(0);
            objectMapper.writeValue(this.metaDataFile, this.metadata);
            this.metaDataFile.setLength(this.metaDataFile.getFilePointer());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ClipMetadata loadOrFetchMetaData() {
        try {
            if (this.metaDataFile.length() > 0) {
                return loadMetadataFromFile();
            }
            return fetchMetadataFromUrl();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ClipMetadata fetchMetadataFromUrl() {
        logger.debug("Loading metadata via HEAD request from {}", this.url);
        try {
            var request = new Request.Builder()
                    .url(this.url)
                    .head()
                    .build();
            try (var response = this.httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.debug("Unsuccessfull status code '{}' for HEAD {}", response.code(), url);
                    throw new RuntimeException();
                }
                logger.debug("successfull HEAD request with headers: {} for HEAD {}", response.headers(), url);
                var meta = new ClipMetadata();
                meta.setContentType(response.header("Content-Type"));
                meta.setSize(Long.parseLong(response.header("Content-Length")));
                return meta;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ClipMetadata loadMetadataFromFile() {
        try {
            this.metaDataFile.seek(0);
            return objectMapper.readValue(this.metaDataFile, ClipMetadata.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        this.metaDataFile.close();
        this.contentFile.close();
    }

    public static void main(String[] args) {
        var dl = new ClipDownloader("https://pdvideosdaserste-a.akamaihd.net/int/2020/12/03/d20c5ce2-a1ed-4c2e-a6db-abf05c48ce5a/1920-1_791699.mp4", "12.mp4");
        dl.run();
    }
}
