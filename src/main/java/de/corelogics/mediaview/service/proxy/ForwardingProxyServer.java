package de.corelogics.mediaview.service.proxy;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.service.downloader.*;
import de.corelogics.mediaview.util.IdUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Request;
import spark.Response;
import spark.Spark;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;

@Singleton
public class ForwardingProxyServer implements ClipContentUrlGenerator {
    private final Logger logger = LogManager.getLogger();

    private final ClipRepository clipRepository;
    private final DownloadManager downloadManager;

    @Inject
    public ForwardingProxyServer(ClipRepository clipRepository, DownloadManager downloadManager) {
        this.downloadManager = downloadManager;
        this.clipRepository = clipRepository;
    }

    public String createLinkTo(ClipEntry e) {
        return "http://storm.lan.corelogics.de:8080/api/v1/clips/" + IdUtils.encodeId(e.getId());
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
        var clipId = new String(Base64.getDecoder().decode(request.params("clipId")), StandardCharsets.UTF_8);
        logger.debug("Request for clip {}\n{}", clipId::toString, () ->
                String.join("\n",
                        "   H:" + request.host(),
                        "   P:" + request.pathInfo(),
                        request.headers().stream().map(h -> "   " + h + ": " + request.headers(h)).collect(Collectors.joining("\n"))));
        clipRepository.findClipById(clipId).ifPresentOrElse(
                clip -> {
                    var byteRange = new ByteRange(request.headers("Range"));
                    try (var stream = downloadManager.openStreamFor(clip, byteRange)) {
                        try {
                            if (byteRange.getFirstPosition() >= stream.getMaxSize()) {
                                response.status(416);
                            } else {
                                response.header("Content-Type", stream.getContentType());
                                response.header("Accept-Ranges", "bytes");
                                if (request.headers("Range") != null) {
                                    response.header("Content-Range", "bytes " + byteRange.getFirstPosition() + "-" + byteRange.getLastPosition().orElse(stream.getMaxSize() - 1) + "/" + stream.getMaxSize());
                                    response.header("Content-Length", Long.toString(byteRange.getLastPosition().orElse(stream.getMaxSize()) - byteRange.getFirstPosition()));
                                } else {
                                    response.header("Content-Length", Long.toString(stream.getMaxSize()));
                                }
                                copyBytes(stream.getStream(), response.raw());
                            }
                        } finally {
                            logger.debug("Closing consumer stream");
                            org.h2.util.IOUtils.closeSilently(stream.getStream());
                        }
                    } catch (UpstreamNotFoundException e) {
                        logger.info("Clip {} wasn't found at upstream url {}", clip::getTitle, clip::getBestUrl);
                        response.status(404);
                    } catch (EOFException e) {
                        logger.info("Requested range {} of clip {} is beyond clip. Redirecting client to original url: {}", byteRange, clip.getId(), clip.getBestUrl());
                        logger.debug("e");
                        response.status(416);
                    } catch (UpstreamReadFailedException e) {
                        logger.info("Upstream server failed for clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                        logger.debug(e);
                        response.redirect(clip.getBestUrl());
                    } catch (TooManyConcurrentConnectionsException e) {
                        logger.info("Can't proxy clip {}[{}], because there are too many concurrent connections open. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                        response.redirect(clip.getBestUrl());
                    } catch (CacheSizeExhaustedException e) {
                        logger.info("Cache size exhausted when requesting clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                        logger.debug(e);
                        response.redirect(clip.getBestUrl());
                    }
                },
                () -> {
                    logger.info("Requested clipId {} wasn't found in DB.", clipId);
                    response.status(404);
                }
        );
        return null;
    }

    private void copyBytes(InputStream from, HttpServletResponse to) {
        try (var toStream = to.getOutputStream()) {
            byte[] buffer = new byte[256_000];
            for (var bytesRead = from.read(buffer, 0, buffer.length); bytesRead >= 0; bytesRead = from.read(buffer, 0, buffer.length)) {
                toStream.write(buffer, 0, bytesRead);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
