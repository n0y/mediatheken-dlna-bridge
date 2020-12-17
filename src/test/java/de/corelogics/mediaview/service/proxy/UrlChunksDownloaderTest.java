package de.corelogics.mediaview.service.proxy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

class UrlChunksDownloaderTest {
    public static final long CHUNK_SIZE = 1_000_000L;
    public static final int MAX_SIZE = 2_000_000_000;

    @Test
    void test() {
        var url = "https://pdvideosdaserste-a.akamaihd.net/int/2020/12/03/1b4a3c95-87d2-4b1c-ab24-acd23cdb140f/1920-1_791697.mp4";
        var downloader = new UrlChunksDownloader(url);
        var chunks = LongStream
                .range(0, MAX_SIZE / CHUNK_SIZE)
                .map(l -> l * CHUNK_SIZE)
                .mapToObj(start -> new Chunk(start, start + CHUNK_SIZE - 1))
                .iterator();

        var start = System.currentTimeMillis();
        var bytesRead = new AtomicLong(0);
        downloader.runChunks(new UrlChunksDownloader.ChunkStore() {
            @Override
            public Optional<Chunk> take(Duration waitTime) {
                return chunks.hasNext() ? Optional.of(chunks.next()) : Optional.empty();
            }

            @Override
            public void save(Chunk chunk, byte[] bytes) {
                var overallRead = bytesRead.addAndGet(bytes.length);
                var durationMs = System.currentTimeMillis() - start + 0D;
                var overallRateKBPerSec = (int) ((overallRead / 1024) / (durationMs / 1000));
                System.out.printf("Saving chunk %s: %d bytes (%d KB/s)%n", chunk, bytes.length, overallRateKBPerSec);
            }

            @Override
            public void reject(Chunk chunk) {
                System.out.printf("Rejecting chunk %s%n", chunk);
            }
        });
    }
}