/*
 * MIT License
 *
 * Copyright (c) 2020-2021 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.service.proxy.downloader;

import com.fasterxml.jackson.core.JsonFactory;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Ticker;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.util.IdUtils;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.Thread.ofVirtual;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

@Log4j2
public class CacheDirectory {
    private final JsonFactory factory = new JsonFactory();
    private final File cacheDirFile;
    private final long cacheSizeBytes;
    private final ScheduledExecutorService scheduledExecutorService = newSingleThreadScheduledExecutor(ofVirtual().factory());
    private final LoadingCache<String, RandomAccessFile> openFiles;

    CacheDirectory(int cacheSizeGb, File cacheDir, Ticker cacheTicker) {
        this.openFiles = Caffeine.newBuilder()
            .maximumSize(40)
            .expireAfterAccess(120, TimeUnit.SECONDS)
            .evictionListener(this::closeFile)
            .ticker(cacheTicker)
            .build(this::openFile);

        if (cacheSizeGb < 10) {
            throw new IllegalStateException("Configuration: CACHE_SIZE_GB is " + cacheSizeGb + ", but at least 10 GB are required");
        }
        this.cacheSizeBytes = 1024L * 1024L * 1024L * cacheSizeGb;
        this.cacheDirFile = cacheDir;
        log.debug("Initializing cache download manager, with cache in directory [{}]", this.cacheDirFile::getAbsolutePath);
        if (!cacheDirFile.exists() && !cacheDirFile.mkdirs()) {
            throw new IllegalStateException("Could not create nonexistent cache directory at " + this.cacheDirFile.getAbsolutePath());
        }
        scheduledExecutorService.scheduleAtFixedRate(this.openFiles::cleanUp, 10, 10, TimeUnit.SECONDS);
        log.info("Successfully started cache download manager, with cache in directory [{}]", this.cacheDirFile::getAbsolutePath);
    }

    public CacheDirectory(MainConfiguration mainConfiguration) {
        this(mainConfiguration.cacheSizeGb(), mainConfiguration.cacheDir(), Ticker.systemTicker());
    }

    private RandomAccessFile openFile(String filename) throws FileNotFoundException {
        return new RandomAccessFile(new File(cacheDirFile, filename), "rw");
    }

    private void closeFile(String name, RandomAccessFile file, RemovalCause cause) {
        IOUtils.closeQuietly(file, e -> log.debug("Could not (quietly) close file.", e));
    }

    private String contentFilename(String clipId) {
        return IdUtils.encodeId(clipId) + ".mp4";
    }

    private String metaFilename(String clipId) {
        return IdUtils.encodeId(clipId) + ".json";
    }


    private long directorySize() {
        return ofNullable(this.cacheDirFile.listFiles()).stream()
            .flatMap(Arrays::stream)
            .filter(File::isFile)
            .mapToLong(File::length)
            .sum();
    }

    public void writeContent(String clipId, long position, byte[] bytes) throws IOException {
        val contentFile = this.openFiles.get(contentFilename(clipId));
        synchronized (contentFile) {
            contentFile.seek(position);
            contentFile.write(bytes);
        }
    }

    private RandomAccessFile openIfPresent(String filename) throws FileNotFoundException {
        val existingContentAccess = this.openFiles.getIfPresent(filename);
        if (null != existingContentAccess) {
            return existingContentAccess;
        }
        // opening the nonexistent file would create it
        val contentFile = new File(this.cacheDirFile, filename);
        if (contentFile.exists()) {
            return this.openFiles.get(filename);
        }
        throw new FileNotFoundException(filename);
    }

    public int readContentByte(String clipId, long position) throws IOException {
        val contentFilename = contentFilename(clipId);
        val contentAccess = this.openIfPresent(contentFilename);
        synchronized (contentAccess) {
            if (position >= contentAccess.length()) {
                throw new EOFException(contentFilename + ": " + position + " >" + contentAccess.length());
            }
            contentAccess.seek(position);
            return contentAccess.read();
        }
    }

    public int readContentBytes(String clipId, long position, byte[] buffer, int off, int len) throws IOException {
        val contentFilename = contentFilename(clipId);
        val contentAccess = this.openIfPresent(contentFilename);
        synchronized (contentAccess) {
            if (position >= contentAccess.length()) {
                return -1;
            }
            contentAccess.seek(position);
            val bytesLeftInFile = contentAccess.length() - position;
            return contentAccess.read(buffer, off, (int) Math.min(bytesLeftInFile, len));
        }
    }

    public synchronized void growContentFile(String clipId, long newSize) throws IOException, CacheSizeExhaustedException {
        val contentFilename = contentFilename(clipId);
        val contentAccess = this.openFiles.get(contentFilename);
        synchronized (contentAccess) {
            val dirSize = directorySize();
            val contentFileSizePrior = contentAccess.length();
            val dirSizeAfter = dirSize - contentFileSizePrior + newSize;
            if (dirSizeAfter > this.cacheSizeBytes) {
                throw new CacheSizeExhaustedException(
                    String.format(
                        "growing [%s] to [%d] would exceed cache size limit of %d with new size of %d",
                        contentFilename,
                        newSize,
                        this.cacheSizeBytes,
                        dirSizeAfter));
            }
            contentAccess.setLength(newSize);
        }
    }


    void writeMetadata(String clipId, @NotNull ClipMetadata metadata) throws IOException {
        val metaFile = this.openFiles.get(metaFilename(clipId));
        synchronized (metaFile) {
            metaFile.seek(0);
            try (val generator = factory.createGenerator(metaFile)) {
                metadata.writeTo(generator);
            }
            metaFile.setLength(metaFile.getFilePointer());
        }
    }

    Optional<ClipMetadata> loadMetadata(String clipId) throws IOException {
        val metaFile = new File(this.cacheDirFile, metaFilename(clipId));
        synchronized (metaFile) {
            if (!metaFile.exists()) {
                return Optional.empty();
            }
            val metaFileAccess = this.openFiles.get(metaFilename(clipId));
            metaFileAccess.seek(0);
            return Optional.of(ClipMetadata.readFrom(factory.createParser(metaFileAccess)));
        }
    }

    public synchronized boolean tryCleanupCacheDir(Set<String> currentlyOpenClipIds) {
        val currentlyOpenFilenames = currentlyOpenClipIds.stream().map(this::contentFilename).collect(Collectors.toSet());

        log.info("Cleaning up some space in cache directory");
        return ofNullable(this.cacheDirFile
            .listFiles((dir, name) -> name.endsWith(".mp4"))).stream()
            .flatMap(Arrays::stream)
            .filter(f -> !currentlyOpenFilenames.contains(f.getName()))
            .sorted(Comparator.comparing(File::lastModified))
            .map(contentFileToRemove -> {
                val metaFileToRemove = new File(this.cacheDirFile, contentFileToRemove.getName().replaceAll(".mp4", ".json"));
                log.debug(
                    "Trying to delete {}, and {} with size of {}",
                    metaFileToRemove::getName,
                    contentFileToRemove::getName,
                    () -> metaFileToRemove.length() + contentFileToRemove.length());
                this.openFiles.invalidate(metaFileToRemove.getName());
                this.openFiles.invalidate(contentFileToRemove.getName());
                return metaFileToRemove.delete() && contentFileToRemove.delete();
            })
            .findFirst()
            .orElse(Boolean.FALSE);
    }

    public synchronized void close() {
        this.openFiles.asMap().forEach((name, file) -> closeFile(name, file, RemovalCause.EXPLICIT));
        this.openFiles.invalidateAll();
        this.scheduledExecutorService.shutdownNow();
    }
}
