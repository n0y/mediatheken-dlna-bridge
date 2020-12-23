/*
 * MIT License
 *
 * Copyright (c) 2020 Mediatheken DLNA Bridge Authors.
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
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.util.IdUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.util.IOUtils;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class CacheDirectory {
    private final Logger logger = LogManager.getLogger(CacheDirectory.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final JsonFactory factory = new JsonFactory();

    private final File cacheDirFile;
    private final long cacheSizeBytes;

    private LoadingCache<String, RandomAccessFile> openFiles = Caffeine.newBuilder()
            .maximumSize(40)
            .expireAfterAccess(120, TimeUnit.SECONDS)
            .removalListener(this::closeFile)
            .build(this::openFile);


    public CacheDirectory(MainConfiguration mainConfiguration) {
        if (mainConfiguration.cacheSizeGb() < 10) {
            throw new IllegalStateException("Configuration: CACHE_SIZE_GB is " + mainConfiguration.cacheSizeGb() + ", but at least 10 GB are required");
        }
        this.cacheSizeBytes = 1024L * 1024L * 1024L * mainConfiguration.cacheSizeGb();
        this.cacheDirFile = new File(mainConfiguration.cacheDir());
        logger.debug("Initializing cache download manager, with cache in directory [{}]", this.cacheDirFile::getAbsolutePath);
        if (!cacheDirFile.exists() && !cacheDirFile.mkdirs()) {
            throw new IllegalStateException("Could not create nonexisting cache directory at " + this.cacheDirFile.getAbsolutePath());
        }
        this.scheduler.scheduleAtFixedRate(this.openFiles::cleanUp, 10, 10, TimeUnit.SECONDS);
        logger.info("Successfully started cache download manager, with cache in directory [{}]", this.cacheDirFile::getAbsolutePath);
    }

    private RandomAccessFile openFile(String filename) throws FileNotFoundException {
        return new RandomAccessFile(new File(cacheDirFile, filename), "rw");
    }

    private void closeFile(String name, RandomAccessFile file, RemovalCause cause) {
        IOUtils.closeSilently(file);
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

    public synchronized void writeContent(String clipId, long position, byte[] bytes) throws IOException {
        var contentFile = this.openFiles.get(contentFilename(clipId));
        synchronized (contentFile) {
            contentFile.seek(position);
            contentFile.write(bytes);
        }
    }

    public synchronized int readContentByte(String clipId, long position) throws IOException {
        var contentFilename = contentFilename(clipId);
        var contentFile = new File(this.cacheDirFile, contentFilename);
        if (!contentFile.exists()) {
            throw new FileNotFoundException(contentFilename);
        }

        var contentAccess = this.openFiles.get(contentFilename);
        if (position >= contentAccess.length()) {
            throw new EOFException(contentFilename + ": " + position + " >" + contentFile.length());
        }
        contentAccess.seek(position);
        return contentAccess.read();
    }

    public synchronized int readContentBytes(String clipId, long position, byte[] buffer, int off, int len) throws IOException {
        var contentFilename = contentFilename(clipId);
        var contentFile = new File(this.cacheDirFile, contentFilename);
        if (!contentFile.exists()) {
            throw new FileNotFoundException(contentFilename);
        }

        var contentAccess = this.openFiles.get(contentFilename);
        if (position >= contentAccess.length()) {
            return -1;
        }
        contentAccess.seek(position);
        var bytesLeftInFile = contentAccess.length() - position;
        return contentAccess.read(buffer, off, (int) Math.min(bytesLeftInFile, len));
    }

    public synchronized void growContentFile(String clipId, long newSize) throws IOException, CacheSizeExhaustedException {
        var contentFilename = contentFilename(clipId);
        var contentFile = this.openFiles.get(contentFilename);
        var dirSize = directorySize();
        var contentFileSizePrior = contentFile.length();
        var dirSizeAfter = dirSize - contentFileSizePrior + newSize;
        if (dirSizeAfter > this.cacheSizeBytes) {
            throw new CacheSizeExhaustedException(
                    String.format(
                            "growing [%s] to [%d] would exceed cache size limit of %d with new size of %d",
                            contentFilename,
                            newSize,
                            this.cacheSizeBytes,
                            dirSizeAfter));
        }
        contentFile.setLength(newSize);
    }


    public synchronized void writeMetadata(String clipId, ClipMetadata metadata) throws IOException {
        var metaFile = this.openFiles.get(metaFilename(clipId));
        metaFile.seek(0);
        try (var generator = factory.createGenerator(metaFile)) {
            metadata.writeTo(generator);
        }
        metaFile.setLength(metaFile.getFilePointer());
    }

    public synchronized Optional<ClipMetadata> loadMetadata(String clipId) throws IOException {
        var metaFile = new File(this.cacheDirFile, metaFilename(clipId));
        if (!metaFile.exists()) {
            return Optional.empty();
        }
        var metaFileAccess = this.openFiles.get(metaFilename(clipId));
        metaFileAccess.seek(0);
        return Optional.of(ClipMetadata.readFrom(factory.createParser(metaFileAccess)));
    }

    public synchronized boolean tryCleanupCacheDir(Set<String> currentlyOpenClipIds) {
        var currentlyOpenFilenames = currentlyOpenClipIds.stream().map(this::contentFilename).collect(Collectors.toSet());

        logger.info("Cleaning up some space in cache directory");
        return ofNullable(this.cacheDirFile
                .listFiles((dir, name) -> name.endsWith(".mp4"))).stream()
                .flatMap(Arrays::stream)
                .filter(f -> !currentlyOpenFilenames.contains(f.getName()))
                .sorted(Comparator.comparing(File::lastModified))
                .map(contentFileToRemove -> {
                    var metaFileToRemove = new File(this.cacheDirFile, contentFileToRemove.getName().replaceAll(".mp4", ".json"));
                    logger.debug(
                            "Trying to delete {}, and {} with size of {}",
                            metaFileToRemove::getName,
                            contentFileToRemove::getName,
                            () -> metaFileToRemove.length() + contentFileToRemove.length());
                    this.openFiles.invalidate(metaFileToRemove);
                    this.openFiles.invalidate(contentFileToRemove);
                    return metaFileToRemove.delete() && contentFileToRemove.delete();
                })
                .filter(Boolean.TRUE::equals)
                .findFirst()
                .isPresent();
    }
}
