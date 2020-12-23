package de.corelogics.mediaview.service.proxy;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.service.proxy.downloader.*;
import de.corelogics.mediaview.util.IdUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Request;
import spark.Response;
import spark.Spark;

import javax.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class ForwardingProxyServer implements ClipContentUrlGenerator {
    private final Logger logger = LogManager.getLogger();

    private final ClipRepository clipRepository;
    private final MainConfiguration mainConfiguration;
    private final DownloadManager downloadManager;


    public ForwardingProxyServer(MainConfiguration mainConfiguration, ClipRepository clipRepository, DownloadManager downloadManager) {
        this.mainConfiguration = mainConfiguration;
        this.downloadManager = downloadManager;
        this.clipRepository = clipRepository;

        logger.debug("Starting HTTP proxy server on port {}", mainConfiguration::publicHttpPort);
        Spark.port(mainConfiguration.publicHttpPort());
        Spark.get("/api/v1/clips/:clipId", this::handleGetClip);
        Spark.head("/api/v1/clips/:clipId", this::handleHead);
        logger.info("Successfully started prefetching HTTP proxy server on port {}", mainConfiguration::publicHttpPort);
    }

    public String createLinkTo(ClipEntry e) {
        var baseUrl = mainConfiguration.publicBaseUrl();
        return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "/api/v1/clips/" + IdUtils.encodeId(e.getId());
    }


    private Object handleHead(Request request, Response response) {
        var clipId = new String(Base64.getDecoder().decode(request.params("clipId")), StandardCharsets.UTF_8);
        logger.debug("HEAD Request for clip {}\n{}", clipId::toString, () ->
                String.join("\n",
                        "   H:" + request.host(),
                        "   P:" + request.pathInfo(),
                        request.headers().stream().map(h -> "   " + h + ": " + request.headers(h)).collect(Collectors.joining("\n"))));
        clipRepository.findClipById(clipId).ifPresentOrElse(
                clip -> {
                    var byteRange = new ByteRange(0, 1);
                    try (var stream = downloadManager.openStreamFor(clip, byteRange)) {
                        try {
                            response.header("Content-Type", stream.getContentType());
                            response.header("Accept-Ranges", "bytes");
                            response.header("Content-Length", Long.toString(stream.getMaxSize()));
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
                    logger.debug("Head request for {}: not found", clipId);
                    response.status(404);
                });
        return null;
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
            if (ofNullable(e.getMessage()).map(String::toLowerCase).filter(s -> s.contains("broken pipe")).isPresent()) {
                logger.debug("Client closed connection. Aborting.");
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
