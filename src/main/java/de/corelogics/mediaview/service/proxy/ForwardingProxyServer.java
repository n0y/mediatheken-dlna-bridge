package de.corelogics.mediaview.service.proxy;

import de.corelogics.mediaview.repository.clip.ClipRepository;
import org.eclipse.jetty.http.HttpStatus;
import spark.Request;
import spark.Response;
import spark.Spark;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

@Singleton
public class ForwardingProxyServer {
    private ClipRepository clipRepository;

    @PostConstruct
    void setupServer() {
        Spark.port(8080);
        Spark.get("/api/v1/clips/:clipId", this::handleGetClip);
    }

    private Object handleGetClip(Request request, Response response) {
        clipRepository.findClipById("some-id").ifPresentOrElse(
                clip -> {
                    response.type("video/mp4");
                    response.header("Accept-Ranges", "bytes");
                    response.header("Content-Length", Long.toString(clip.getSize()));
                },
                () -> {
                    response.status(HttpStatus.NOT_FOUND_404);
                }
        );
    }
}
