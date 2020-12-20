package de.corelogics.mediaview.service.retriever;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;

import javax.inject.Singleton;
import java.util.concurrent.ExecutionException;

@Singleton
public class ClipRetriever {
    private final LoadingCache<String, ClipRetrieverWorker> workers = CacheBuilder.<String, ClipRetrieverWorker>newBuilder()
            .maximumSize(10)
            .removalListener(e -> ((ClipRetrieverWorker) e.getValue()).close())
            .build(new CacheLoader<>() {
                @Override
                public ClipRetrieverWorker load(String url) {
                    return createClipRetrieverWorker(url);
                }
            });

    public ClipPart fetchClipRange(ClipEntry clip, ByteRange byteRange) {
        try {
            return this.workers.get(clip.getBestUrl()).fetchClipRange((byteRange));
        } catch(ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private ClipRetrieverWorker createClipRetrieverWorker(String url) {
        return new ClipRetrieverWorker(url);
    }
}
