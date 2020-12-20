package de.corelogics.mediaview.service.retriever;

import okhttp3.Response;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.InputStream;

public class ClipPart implements Closeable {
    private final Response response;

    public ClipPart(Response response) {
        this.response = response;
    }

    public InputStream getInputStream() {
        if (response.isSuccessful()) {
            return new BufferedInputStream(response.body().byteStream(), 30_000_000);
        }
        throw new IllegalStateException("No response body with status code " + response.code());
    }

    public ClipPartStatus getStatus() {
        if (404 == response.code()) {
            return ClipPartStatus.NOT_FOUND;
        } else if (416 == response.code()) {
            return ClipPartStatus.NOT_SATISFIABLE;
        } else if (response.isSuccessful()) {
            return ClipPartStatus.OK;
        } else {
            throw new IllegalStateException("Unhandled upstream status of " + response.code());
        }
    }

    public String getContentTyp() {
        return response.header("Content-Type");
    }

    public long getPartSize() {
        return Long.parseLong(response.header("Content-Length"));
    }

    public long getCompleteSize() {
        var contentRangeHeader = response.header("Content-Range");
        if (null == contentRangeHeader) {
            return getPartSize();
        }
        var split = contentRangeHeader.split("/");
        return Long.parseLong(split[split.length - 1]);
    }

    @Override
    public void close() {
        response.close();
    }
}
