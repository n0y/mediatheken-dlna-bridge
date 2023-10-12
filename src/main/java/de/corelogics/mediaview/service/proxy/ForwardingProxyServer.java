/*
 * MIT License
 *
 * Copyright (c) 2020-2021 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.service.proxy;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.service.proxy.downloader.DownloadManager;
import de.corelogics.mediaview.util.IdUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ForwardingProxyServer implements ClipContentUrlGenerator {
    private final Logger logger = LogManager.getLogger(ForwardingProxyServer.class);

    private final ClipRepository clipRepository;
    private final MainConfiguration mainConfiguration;
    private final DownloadManager downloadManager;


    public ForwardingProxyServer(MainConfiguration mainConfiguration, ClipRepository clipRepository, DownloadManager downloadManager) {
        this.mainConfiguration = mainConfiguration;
        this.downloadManager = downloadManager;
        this.clipRepository = clipRepository;

        logger.debug("Starting HTTP proxy server on port {}", mainConfiguration::publicHttpPort);
//        Spark.port(mainConfiguration.publicHttpPort());
//        Spark.get("/api/v1/clips/:clipId", this::handleGetClip);
//        Spark.head("/api/v1/clips/:clipId", this::handleHead);
        logger.info("Successfully started prefetching HTTP proxy server on port {}", mainConfiguration::publicHttpPort);
    }

    public String createLinkTo(ClipEntry e) {
        var baseUrl = mainConfiguration.publicBaseUrl();
        return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "/api/v1/clips/" + IdUtils.encodeId(e.getId());
    }


//    private Object handleHead(Request request, Response response) {
//        var clipId = new String(Base64.getDecoder().decode(request.params("clipId")), StandardCharsets.UTF_8);
//        logger.debug("HEAD Request for clip {}\n{}", clipId::toString, () ->
//                String.join("\n",
//                        "   H:" + request.host(),
//                        "   P:" + request.pathInfo(),
//                        request.headers().stream().map(h -> "   " + h + ": " + request.headers(h)).collect(Collectors.joining("\n"))));
//        clipRepository.findClipById(clipId).ifPresentOrElse(
//                clip -> {
//                    var byteRange = new ByteRange(0, 1);
//                    try (var stream = downloadManager.openStreamFor(clip, byteRange)) {
//                        try {
//                            response.header(HttpUtils.HEADER_CONTENT_TYPE, stream.getContentType());
//                            response.header(HttpUtils.HEADER_ACCEPT_RANGES, "bytes");
//                            response.header(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(stream.getMaxSize()));
//                        } finally {
//                            logger.debug("Closing consumer stream");
//                        }
//                    } catch (UpstreamNotFoundException e) {
//                        logger.info("Clip {} wasn't found at upstream url {}", clip::getTitle, clip::getBestUrl);
//                        response.status(HttpServletResponse.SC_NOT_FOUND);
//                    } catch (EOFException e) {
//                        logger.info("Requested range {} of clip {} is beyond clip. Redirecting client to original url: {}", byteRange, clip.getId(), clip.getBestUrl());
//                        logger.debug("e");
//                        response.status(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
//                    } catch (UpstreamReadFailedException e) {
//                        logger.info("Upstream server failed for clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
//                        logger.debug(e);
//                        response.redirect(clip.getBestUrl());
//                    } catch (TooManyConcurrentConnectionsException e) {
//                        logger.info("Can't proxy clip {}[{}], because there are too many concurrent connections open. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
//                        response.redirect(clip.getBestUrl());
//                    } catch (CacheSizeExhaustedException e) {
//                        logger.info("Cache size exhausted when requesting clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
//                        logger.debug(e);
//                        response.redirect(clip.getBestUrl());
//                    }
//                },
//                () -> {
//                    logger.debug("Head request for {}: not found", clipId);
//                    response.status(HttpServletResponse.SC_NOT_FOUND);
//                });
//        return null;
//    }
//
//    private Object handleGetClip(Request request, Response response) {
//        var clipId = new String(Base64.getDecoder().decode(request.params("clipId")), StandardCharsets.UTF_8);
//        logger.debug("Request for clip {}\n{}", clipId::toString, () ->
//                String.join("\n",
//                        "   H:" + request.host(),
//                        "   P:" + request.pathInfo(),
//                        request.headers().stream().map(h -> "   " + h + ": " + request.headers(h)).collect(Collectors.joining("\n"))));
//        clipRepository.findClipById(clipId).ifPresentOrElse(
//                clip -> {
//                    var byteRange = new ByteRange(request.headers(HttpUtils.HEADER_RANGE));
//                    try (var stream = downloadManager.openStreamFor(clip, byteRange)) {
//                        try {
//                            if (byteRange.getFirstPosition() >= stream.getMaxSize()) {
//                                response.status(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
//                            } else {
//                                response.header(HttpUtils.HEADER_CONTENT_TYPE, stream.getContentType());
//                                response.header(HttpUtils.HEADER_ACCEPT_RANGES, "bytes");
//                                if (request.headers(HttpUtils.HEADER_RANGE) != null) {
//                                    response.header(HttpUtils.HEADER_CONTENT_RANGE, "bytes " + byteRange.getFirstPosition() + "-" + byteRange.getLastPosition().orElse(stream.getMaxSize() - 1) + "/" + stream.getMaxSize());
//                                    response.header(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(byteRange.getLastPosition().orElse(stream.getMaxSize()) - byteRange.getFirstPosition()));
//                                } else {
//                                    response.header(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(stream.getMaxSize()));
//                                }
//                                copyBytes(stream.getStream(), response.raw());
//                            }
//                        } finally {
//                            logger.debug("Closing consumer stream");
//                        }
//                    } catch (UpstreamNotFoundException e) {
//                        logger.info("Clip {} wasn't found at upstream url {}", clip::getTitle, clip::getBestUrl);
//                        response.status(HttpServletResponse.SC_NOT_FOUND);
//                    } catch (EOFException e) {
//                        logger.info("Requested range {} of clip {} is beyond clip. Redirecting client to original url: {}", byteRange, clip.getId(), clip.getBestUrl());
//                        logger.debug("e");
//                        response.status(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
//                    } catch (UpstreamReadFailedException e) {
//                        logger.info("Upstream server failed for clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
//                        logger.debug(e);
//                        response.redirect(clip.getBestUrl());
//                    } catch (TooManyConcurrentConnectionsException e) {
//                        logger.info("Can't proxy clip {}[{}], because there are too many concurrent connections open. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
//                        response.redirect(clip.getBestUrl());
//                    } catch (CacheSizeExhaustedException e) {
//                        logger.info("Cache size exhausted when requesting clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
//                        logger.debug(e);
//                        response.redirect(clip.getBestUrl());
//                    }
//                },
//                () -> {
//                    logger.info("Requested clipId {} wasn't found in DB.", clipId);
//                    response.status(HttpServletResponse.SC_NOT_FOUND);
//                }
//        );
//        return null;
//    }
//
//    private void copyBytes(InputStream from, HttpServletResponse to) {
//        try (var toStream = to.getOutputStream()) {
//            IOUtils.copyLarge(from, toStream);
//        } catch (final IOException e) {
//            logger.debug("Client closed connection. Aborting.");
//        }
//    }
}
