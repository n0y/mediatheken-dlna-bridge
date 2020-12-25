package de.corelogics.mediaview.service.proxy.downloader;

import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.util.HttpUtils;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class ClipDownloader implements Closeable {
    private static final double REQUIRED_MB_PER_SECONDS = 1.3D;

    private static final long CHUNK_SIZE_BYTES = 5_000_000;
    private static final long STOP_READING_AFTER_SECS = 30;

    private final AtomicInteger currentConnectionId = new AtomicInteger();
    private final Map<String, ClipDownloadConnection> connections = new HashMap<>();

    private final Logger logger = LogManager.getLogger(ClipDownloader.class);
    private final MainConfiguration mainConfiguration;
    private final CacheDirectory cacheDir;
    private final String url;
    private final String clipId;
    private final int numParallelConnections;
    private final OkHttpClient httpClient;
    private ClipMetadata metadata;
    private BitSet chunksAvailableForDownload;
    private int lastReadInChunk = 0;
    private boolean stopped = false;

    public ClipDownloader(
            MainConfiguration mainConfiguration,
            CacheDirectory cacheDir,
            String clipId,
            String url) throws UpstreamNotFoundException, UpstreamReadFailedException, CacheSizeExhaustedException {
        this.mainConfiguration = mainConfiguration;
        this.cacheDir = cacheDir;
        this.url = url;
        this.clipId = clipId;
        this.numParallelConnections = mainConfiguration.cacheParallelDownloadsPerVideo();
        this.httpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(1, 10, TimeUnit.SECONDS))
                .callTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .build();
        logger.debug("Starting download for {}", this.url);
        this.metadata = loadOrFetchMetaData();
        logger.debug("Initialized metadata to {}", this.metadata);
        initializeBitsets();
        updateMetadataFile();
        growContentFile();
        ensureDownloadersPresent();
    }

    public synchronized void onChunkReceived(String connectionId, ClipChunk clipChunk, byte[] bytes, long timeMs) {
        logger.debug("Chunk {} received: {} bytes in {} ms (by {})", clipChunk.getChunkNumber(), bytes.length, timeMs, connectionId);
        if (!stopped) {
            try {
                cacheDir.writeContent(this.clipId, clipChunk.getChunkNumber() * CHUNK_SIZE_BYTES, bytes);
            } catch (final IOException e) {
                logger.warn("Could not write to content file.", e);
            }
            this.metadata.getBitSet().set(clipChunk.getChunkNumber());
            this.updateMetadataFile();
        }
    }

    public synchronized void onChunkError(String connectionId, ClipChunk clipChunk, Exception e) {
        logger.debug("Chunk {} not received due to {} (by {})", clipChunk.getChunkNumber(), e.getMessage(), connectionId);
        this.chunksAvailableForDownload.set(clipChunk.getChunkNumber());
    }

    public synchronized Optional<ClipChunk> nextChunk(String connectionId) {
        if (!stopped) {
            var chunkNum = this.chunksAvailableForDownload.nextSetBit(lastReadInChunk);
            if (this.chunksAvailableForDownload.get(this.metadata.getNumberOfChunks() - 1)) {
                // first DL latest chunk, for meta data queried by various players
                chunkNum = this.metadata.getNumberOfChunks() - 1;
            }
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
        }
        return Optional.empty();
    }

    public synchronized void onConnectionTerminated(String connectionId) {
        logger.debug("connection {} terminated", connectionId);
        this.connections.remove(connectionId);
    }

    public String getUrl() {
        return this.url;
    }

    private synchronized void ensureDownloadersPresent() {
        while (this.connections.size() < numParallelConnections && this.chunksAvailableForDownload.nextSetBit(0) < this.metadata.getNumberOfChunks()) {
            var connectionId = "dl-thrd-" + currentConnectionId.incrementAndGet();
            logger.debug("Starting new connection {}", connectionId);
            this.connections.put(connectionId, new ClipDownloadConnection(
                    this, mainConfiguration,
                    connectionId,
                    CHUNK_SIZE_BYTES,
                    REQUIRED_MB_PER_SECONDS / numParallelConnections));
            this.connections.get(connectionId).start();
        }
    }

    private void initializeBitsets() {
        if (null == this.metadata.getBitSet()) {
            var bitsetSize = 1 + (int) (this.metadata.getSize() / CHUNK_SIZE_BYTES);
            logger.debug("creating new bitset of size {}", bitsetSize);
            this.metadata.setNumberOfChunks(bitsetSize);
            this.metadata.setBitSet(new BitSet(this.metadata.getNumberOfChunks()));
        }
        this.chunksAvailableForDownload = new BitSet(this.metadata.getNumberOfChunks());
        this.chunksAvailableForDownload.set(0, this.metadata.getNumberOfChunks());
        this.chunksAvailableForDownload.xor(this.metadata.getBitSet());
    }

    private void growContentFile() throws CacheSizeExhaustedException {
        logger.debug("setting content file to length {}", this.metadata::getSize);
        try {
            this.cacheDir.growContentFile(this.clipId, this.metadata.getSize());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateMetadataFile() {
        logger.debug("updating metadata file");
        if (!stopped) {
            try {
                this.cacheDir.writeMetadata(clipId, this.metadata);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private ClipMetadata loadOrFetchMetaData() throws UpstreamNotFoundException, UpstreamReadFailedException {
        try {
            var fromFile = this.cacheDir.loadMetadata(clipId);
            if (fromFile.isPresent()) {
                return fromFile.get();
            }
        } catch (final IOException e) {
            logger.warn("Could not read metadata file, but it's present. Re-fetching.");
        }
        return this.fetchMetadataFromUrl();
    }

    private ClipMetadata fetchMetadataFromUrl() throws UpstreamNotFoundException, UpstreamReadFailedException {
        logger.debug("Loading metadata via HEAD request from {}", this.url);
        try {
            var request = HttpUtils.enhanceRequest(
                    this.mainConfiguration,
                    new Request.Builder()
                            .url(this.url)
                            .head())
                    .build();
            try (var response = this.httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.debug("successfull HEAD request with headers: {} for HEAD {}", response.headers(), url);
                    var meta = new ClipMetadata();
                    meta.setContentType(response.header("Content-Type"));
                    meta.setSize(Long.parseLong(response.header("Content-Length")));
                    return meta;
                }
                if (response.code() == 404) {
                    throw new UpstreamNotFoundException(this.url, response.code());
                }
                throw new UpstreamReadFailedException(String.format("Metadata Read failed with response code %d on url %s", response.code(), this.url));
            }
        } catch (final IOException e) {
            throw new UpstreamReadFailedException(String.format("Could not load meta data from url %s", this.url));
        } finally {
            httpClient.connectionPool().evictAll();
        }
    }

    protected void updateLastReadChunk(int chunkNo) {
        this.lastReadInChunk = chunkNo;
        ensureDownloadersPresent();
    }

    public InputStream openInputStreamStartingFrom(long position, Duration readTimeout) throws EOFException {
        if (position < 0 || position > metadata.getSize()) {
            throw new EOFException(String.format("Position %d outside of allowed range: [0-%d]", position, metadata.getSize()));
        }
        return new InputStream() {
            long currentPosition = position;

            @Override
            public int read() throws IOException {
                var timeoutAt = System.currentTimeMillis() + readTimeout.toMillis();
                while (System.currentTimeMillis() < timeoutAt) {
                    var chunkNo = (int) (currentPosition / CHUNK_SIZE_BYTES);
                    updateLastReadChunk(chunkNo);
                    if (metadata.getBitSet().get(chunkNo)) {
                        return cacheDir.readContentByte(clipId, currentPosition++);
                    } else {
                        try {
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
                    var chunkNo = (int) (currentPosition / CHUNK_SIZE_BYTES);
                    if (chunkNo >= metadata.getNumberOfChunks()) {
                        logger.debug("Read position {}} is beyond size of {}}", currentPosition, metadata.getSize());
                        return -1;
                    }
                    updateLastReadChunk(chunkNo);
                    if (metadata.getBitSet().get(chunkNo)) {
                        var availableBytesInChunk = (chunkNo + 1) * CHUNK_SIZE_BYTES - currentPosition;
                        var toRead = (int) Math.min(len, availableBytesInChunk);
                        var readBytesFromFile = cacheDir.readContentBytes(clipId, currentPosition, b, off, toRead);
                        currentPosition += readBytesFromFile;
                        return readBytesFromFile;
                    } else {
                        logger.debug("Waiting for chunk #{}", chunkNo);
                        try {
                            Thread.sleep(500);
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
    public synchronized void close() {
        this.stopped = true;
        this.connections.values().forEach(ClipDownloadConnection::close);
        this.connections.clear();

        updateMetadataFile();
    }

    public ClipMetadata getMetaData() {
        return this.metadata;
    }
}
