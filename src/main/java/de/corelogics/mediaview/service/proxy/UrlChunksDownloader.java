package de.corelogics.mediaview.service.proxy;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

public class UrlChunksDownloader {
    private final Logger logger = LogManager.getLogger();

    public interface ChunkStore {
        Optional<Chunk> take(Duration waitTime);

        void save(Chunk chunk, byte[] bytes);

        void reject(Chunk chunk);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(1, 5, TimeUnit.SECONDS))
            .callTimeout(20, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build();


    private final String url;

    public UrlChunksDownloader(String url) {
        this.url = url;
    }

    public void runChunks(ChunkStore chunkSupplier) {
        try {
            for (var optChunk = chunkSupplier.take(Duration.of(5, ChronoUnit.SECONDS)); optChunk.isPresent(); optChunk = chunkSupplier.take(Duration.of(5, ChronoUnit.SECONDS))) {
                var chunk = optChunk.get();
                var request = new Request.Builder()
                        .url(this.url)
                        .addHeader("Range", format("bytes=%d-%d", chunk.getFirstBytePosition(), chunk.getLastBytePosition()))
                        .addHeader("Accept", "video/mp4, video/*;q=0.8, */*;q=0.5")
                        .build();
                try (var response = this.client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        chunkSupplier.save(chunk, response.body().bytes());
                    } else {
                        chunkSupplier.reject(chunk);
                    }
                } catch (IOException e) {
                    chunkSupplier.reject(chunk);
                }
            }
        } finally {
            client.connectionPool().evictAll();
        }
    }
}
