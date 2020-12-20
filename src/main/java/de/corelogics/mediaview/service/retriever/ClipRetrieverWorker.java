package de.corelogics.mediaview.service.retriever;

import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;

public class ClipRetrieverWorker {
    private final OkHttpClient client = new OkHttpClient.Builder().build();
    private final String url;

    public ClipRetrieverWorker(String url) {
        this.url = url;
    }

    public void close() {
        client.connectionPool().evictAll();
    }

    public ClipPart fetchClipRange(ByteRange byteRange) {
        var builder = new Request.Builder();
        builder.url(this.url);
        if (byteRange.isPartial()) {
            builder.addHeader(
                    "Range",
                    String.format(
                            "bytes %d-%s",
                            byteRange.getFirstPosition(),
                            byteRange.getLastPosition().map(Object::toString).orElse("")));
        }
        builder.addHeader("Application", "mediathek-dlna-bridge");
        try {
            return new ClipPart(client.newCall(builder.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
