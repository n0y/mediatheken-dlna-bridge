package de.corelogics.mediaview.service.proxy;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.service.retriever.ByteRange;
import de.corelogics.mediaview.service.retriever.ClipPart;
import de.corelogics.mediaview.service.retriever.ClipRetriever;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.utils.IOUtils;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Singleton
public class ForwardingProxyServer implements ClipContentUrlGenerator {
    private final Logger logger = LogManager.getLogger();

    private final ClipRepository clipRepository;
    private final ClipRetriever clipRetriever;

    @Inject
    public ForwardingProxyServer(ClipRetriever clipRetriever, ClipRepository clipRepository) {
        this.clipRetriever = clipRetriever;
        this.clipRepository = clipRepository;
    }

    public String createLinkTo(ClipEntry e) {
        return "http://hurricane.lan.corelogics.de:8080/api/v1/clips/" + e.getId();
    }

    @PostConstruct
    void setupServer() {
        logger.debug("Starting HTTP proxy server on port 8080");
        Spark.port(8080);
        Spark.get("/api/v1/clips/:clipId", this::handleGetClip);
        Spark.head("/api/v1/clips/:clipId", (req, resp) -> {
            logger.debug("Head for clip '{}': {}", req.params("clipId"), req);
            resp.status(404);
            return null;
        });
        logger.info("Started HTTP proxy server on port 8080");
    }

    private Object handleGetClip(Request request, Response response) {
        var clipId = request.params("clipId");
        logger.debug("Received request for clip {}", clipId);
        logger.debug("Request: {}", String.join(",", request.host(), request.pathInfo(), request.headers().stream().map(request::headers).collect(Collectors.joining(","))));
        clipRepository.findClipById(clipId).ifPresentOrElse(
                clip -> {
                    var byteRange = new ByteRange(request.headers("Range"));
                    try (var clipPart = clipRetriever.fetchClipRange(clip, byteRange)) {
                        switch (clipPart.getStatus()) {
                            case OK:
                                response.type(clipPart.getContentTyp());
                                response.header("Accept-Ranges", "bytes");
                                if (byteRange.isPartial()) {
                                    response.header("Content-Length", Long.toString(byteRange.rangeSize(clipPart.getCompleteSize())));
                                    response.header(
                                            "Content-Range",
                                            "bytes " + byteRange.getFirstPosition() + "-" + byteRange.getLastPosition() + "/" + clipPart.getCompleteSize());
                                } else {
                                    response.header("Content-Length", Long.toString(clipPart.getPartSize()));
                                }
                                copyBytes(clipPart, response.raw());
                                break;
                            case NOT_SATISFIABLE:
                                response.status(416);
                                break;
                            case NOT_FOUND:
                                response.status(404);
                                break;
                            default:
                                response.status(500);
                        }
                    }
                },
                () -> response.status(404)
        );
        return null;
    }

    private void copyBytes(ClipPart from, HttpServletResponse to) {
        try {
            IOUtils.copy(from.getInputStream(), to.getOutputStream());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
