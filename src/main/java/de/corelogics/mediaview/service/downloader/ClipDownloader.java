package de.corelogics.mediaview.service.downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.time.Duration;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class ClipDownloader implements Closeable {
    private static final long CHUNK_SIZE_BYTES = 3_000_000;
    private static final long STOP_READING_AFTER_SECS = 30;
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new BitsetSerializerModule());

    private final AtomicInteger currentConnectionId = new AtomicInteger();
    private final Map<String, ClipDownloadConnection> connections = new HashMap<>();

    private final Logger logger = LogManager.getLogger();
    private final File cacheDir = new File("cache");
    private final String url;
    private final String id;
    private final OkHttpClient httpClient;
    private final RandomAccessFile metaDataFile;
    private final RandomAccessFile contentFile;
    private ClipMetadata metadata;
    private BitSet chunksAvailableForDownload;
    private long lastReadPosition = 0;
    private long lastReadTime = 0;

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
                    .readTimeout(10, TimeUnit.SECONDS)
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .build();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }


    public synchronized void onChunkReceived(String connectionId, ClipChunk clipChunk, byte[] bytes, long timeMs) {
        logger.debug("Chunk {} received: {} bytes (by {})", clipChunk.getChunkNumber(), bytes.length, connectionId);
        try {
            synchronized (contentFile) {
                contentFile.seek(clipChunk.getChunkNumber() * CHUNK_SIZE_BYTES);
                contentFile.write(bytes);
            }
        } catch (final IOException e) {
            logger.warn("Could not write to content file.", e);
        }
        this.metadata.getBitSet().set(clipChunk.getChunkNumber());
        this.updateMetadataFile();
    }

    public synchronized void onChunkError(String connectionId, ClipChunk clipChunk, Exception e) {
        logger.debug("Chunk {} not received due to {} (by {})", clipChunk.getChunkNumber(), e.getMessage(), connectionId);
        this.chunksAvailableForDownload.set(clipChunk.getChunkNumber());
    }

    public synchronized Optional<ClipChunk> nextChunk(String connectionId) {
        if (System.currentTimeMillis() > lastReadTime + STOP_READING_AFTER_SECS * 1000) {
            return Optional.empty();
        }
        var chunkNum = this.chunksAvailableForDownload.nextSetBit((int) (lastReadPosition / CHUNK_SIZE_BYTES));
        if (chunkNum < 0 || chunkNum >= this.metadata.getNumberOfChunks()) {
            chunkNum = this.chunksAvailableForDownload.nextSetBit(0);
        }
        if (chunkNum >= 0 && chunkNum < this.metadata.getNumberOfChunks()) {
            this.chunksAvailableForDownload.clear(chunkNum);
            var chunk = new ClipChunk(chunkNum, chunkNum * CHUNK_SIZE_BYTES, (1 + chunkNum) * CHUNK_SIZE_BYTES - 1);
            logger.debug("handing out {} to {}", chunk, connectionId);
            return Optional.of(chunk);
        }
        logger.debug("no more chunks reported to {}", connectionId);
        return Optional.empty();
    }

    public synchronized void onConnectionTerminated(String connectionId) {
        logger.debug("connection {} terminated", connectionId);
        this.connections.remove(connectionId);
    }

    public String getUrl() {
        return this.url;
    }

    private void run() {
        logger.debug("Starting download for {}", this.url);
        this.metadata = loadOrFetchMetaData();
        logger.debug("Initialized metadata to {}", this.metadata);
        initializeBitsets();
        updateMetadataFile();
        updateContentFile();
        this.startNewDownloader();
    }

    private synchronized void startNewDownloader() {
        var connectionId = "dl-thrd-" + currentConnectionId.incrementAndGet();
        logger.debug("Starting new connection {}", connectionId);
        this.connections.put(connectionId, new ClipDownloadConnection(connectionId, CHUNK_SIZE_BYTES, this));
        this.connections.get(connectionId).start();
    }

    private void initializeBitsets() {
        if (null == this.metadata.getBitSet()) {
            var bitsetSize = (int) (this.metadata.getSize() / CHUNK_SIZE_BYTES);
            logger.debug("creating new bitset of size {}", bitsetSize);
            this.metadata.setNumberOfChunks(bitsetSize);
            this.metadata.setBitSet(new BitSet(this.metadata.getNumberOfChunks()));
        }
        this.chunksAvailableForDownload = new BitSet(this.metadata.getNumberOfChunks());
        this.chunksAvailableForDownload.set(0, this.metadata.getNumberOfChunks());
        this.chunksAvailableForDownload.xor(this.metadata.getBitSet());
    }

    private void updateContentFile() {
        logger.debug("setting content file to length {}", this.metadata::getSize);
        try {
            synchronized (contentFile) {
                this.contentFile.setLength(this.metadata.getSize());
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateMetadataFile() {
        logger.debug("updating metadata file");
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
        } finally {
            httpClient.connectionPool().evictAll();
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

    protected void updateRead(long pos) {
        this.lastReadPosition = pos;
        this.lastReadTime = System.currentTimeMillis();
        if (connections.isEmpty()) {
            startNewDownloader();
        }
    }

    public InputStream openInputStreamStartingFrom(long position, Duration readTimeout) throws IOException {
        if (position < 0 || position > metadata.getSize()) {
            throw new IOException(String.format("Position %d outside of allowed range: [0-%d]", position, metadata.getSize()));
        }
        lastReadPosition = position;
        return new InputStream() {
            long currentPosition = position;

            @Override
            public int read() throws IOException {
                var timeoutAt = System.currentTimeMillis() + readTimeout.toMillis();
                while (System.currentTimeMillis() < timeoutAt) {
                    updateRead(currentPosition);
                    var chunkNo = (int) (currentPosition / CHUNK_SIZE_BYTES);
                    if (metadata.getBitSet().get(chunkNo)) {
                        synchronized (contentFile) {
                            //logger.debug("Chunk {} is present. Handing out data", chunkNo);
                            contentFile.seek(currentPosition++);
                            return contentFile.read();
                        }
                    } else {
                        try {
                            logger.debug("Chunk {} is not yet present. Sleeping.", chunkNo);
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            throw new IOException("Interrupted while waiting for data");
                        }
                    }
                }
                throw new IOException("Timeout waiting for data");
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                // we only read to the chunk limit...
                var timeoutAt = System.currentTimeMillis() + readTimeout.toMillis();
                while (System.currentTimeMillis() < timeoutAt) {
                    updateRead(currentPosition);
                    var chunkNo = (int) (currentPosition / CHUNK_SIZE_BYTES);
                    if (metadata.getBitSet().get(chunkNo)) {
                        synchronized (contentFile) {
                            var availableBytesInChunk = (chunkNo + 1) * CHUNK_SIZE_BYTES - currentPosition;
                            var availableBytesInFile = contentFile.length() - currentPosition;
                            var toRead = Math.min(len, Math.min(availableBytesInFile, availableBytesInChunk));
                            //logger.debug("Chunk {} is present. Handing out data", chunkNo);
                            contentFile.seek(currentPosition);
                            var readBytesFromFile = contentFile.read(b, off, (int) toRead);
                            currentPosition += readBytesFromFile;
                            return readBytesFromFile;
                        }
                    } else {
                        try {
                            logger.debug("Chunk {} is not yet present. Sleeping.", chunkNo);
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            throw new IOException("Interrupted while waiting for data");
                        }
                    }
                }
                return 0;
            }
        };
    }

    @Override
    public void close() {
        try {
            this.metaDataFile.close();
            this.contentFile.close();
        } catch (final IOException e) {
            throw new RuntimeException();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        var dl = new ClipDownloader("https://pdvideosdaserste-a.akamaihd.net/int/2020/12/03/d20c5ce2-a1ed-4c2e-a6db-abf05c48ce5a/1920-1_791699.mp4", "12.mp4");
        dl.run();
        Thread.sleep(1);
        try (var in = new BufferedInputStream(dl.openInputStreamStartingFrom(4_000_000, Duration.ofSeconds(60)))) {
            var data = new byte[1_200_000];
            var start = System.currentTimeMillis();
            var currentPosition = 0L;
            for (var numRead = in.read(data, 0, data.length); numRead >= 0; numRead = in.read(data, 0, data.length)) {
                currentPosition += numRead;
                var duration = 1 + System.currentTimeMillis() - start;
                var kb = currentPosition / (1024);
                System.out.println(currentPosition + ": Read " + kb + "KB in " + (duration / 1000) + "s: " + (1000 * kb / duration) + " kb/sec");
            }
        } catch (IOException e) {
            dl.close();
        }
    }
}
