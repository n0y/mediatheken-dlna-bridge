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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.google.inject.Singleton;
import com.netflix.governator.annotations.Configuration;
import de.corelogics.mediaview.util.IdUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.util.IOUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Singleton
public class CacheDirectory {
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new BitsetSerializerModule());
    private final Logger logger = LogManager.getLogger();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Configuration("CACHE_DIRECTORY")
    private String cacheDir = "./cache";

    @Configuration("CACHE_SIZE_GB")
    private int cacheSizeGb = 10;

    private File cacheDirFile;
    private long cacheSizeBytes;

    private LoadingCache<String, RandomAccessFile> openFiles = CacheBuilder.newBuilder()
            .maximumSize(40)
            .expireAfterAccess(120, TimeUnit.SECONDS)
            .removalListener(this::closeFile)
            .build(new CacheLoader<>() {
                @Override
                public RandomAccessFile load(String filename) throws Exception {
                    return openFile(filename);
                }
            });


    @PostConstruct
    void scheduleExpireThread() {
        if (this.cacheSizeGb < 10) {
            throw new IllegalStateException("Configuration: CACHE_SIZE_GB is " + cacheSizeGb + ", but at least 10 GB are required");
        }
        this.cacheSizeBytes = 1024L * 1024L * 1024L * this.cacheSizeGb;
        this.cacheDirFile = new File(this.cacheDir);
        logger.info("Initializing cache download manager, with cache in directory [{}]", this.cacheDirFile::getAbsolutePath);
        if (!cacheDirFile.exists() && !cacheDirFile.mkdirs()) {
            throw new IllegalStateException("Could not create nonexisting cache directory at " + this.cacheDirFile.getAbsolutePath());
        }
        this.scheduler.scheduleAtFixedRate(this.openFiles::cleanUp, 10, 10, TimeUnit.SECONDS);
    }

    private RandomAccessFile openFile(String filename) throws FileNotFoundException {
        return new RandomAccessFile(new File(cacheDirFile, filename), "rw");
    }

    private void closeFile(RemovalNotification<String, RandomAccessFile> notification) {
        IOUtils.closeSilently(notification.getValue());
    }

    private String contentFilename(String clipId) {
        return IdUtils.encodeId(clipId) + ".mp4";
    }

    private String metaFilename(String clipId) {
        return IdUtils.encodeId(clipId) + ".json";
    }


    private long directorySize() {
        return Arrays.stream(this.cacheDirFile.listFiles())
                .filter(File::isFile)
                .mapToLong(File::length)
                .sum();
    }

    public synchronized void writeContent(String clipId, long position, byte[] bytes) throws IOException {
        try {
            var contentFile = this.openFiles.get(contentFilename(clipId));
            synchronized (contentFile) {
                contentFile.seek(position);
                contentFile.write(bytes);
            }
        } catch (final ExecutionException e) {
            throw new IOException(e);
        }
    }

    public synchronized int readContentByte(String clipId, long position) throws IOException {
        try {
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
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
    }

    public synchronized int readContentBytes(String clipId, long position, byte[] buffer, int off, int len) throws IOException {
        try {
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
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
    }

    public synchronized void growContentFile(String clipId, long newSize) throws IOException, CacheSizeExhaustedException {
        try {
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
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
    }


    public synchronized void writeMetadata(String clipId, ClipMetadata metadata) throws IOException {
        try {
            var metaFile = this.openFiles.get(metaFilename(clipId));
            metaFile.seek(0);
            objectMapper.writeValue(metaFile, metadata);
            metaFile.setLength(metaFile.getFilePointer());
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
    }

    public synchronized Optional<ClipMetadata> loadMetadata(String clipId) throws IOException {
        try {
            var metaFile = new File(this.cacheDirFile, metaFilename(clipId));
            if (!metaFile.exists()) {
                return Optional.empty();
            }
            var metaFileAccess = this.openFiles.get(metaFilename(clipId));
            metaFileAccess.seek(0);
            return Optional.of(objectMapper.readValue(metaFileAccess, ClipMetadata.class));
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
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
