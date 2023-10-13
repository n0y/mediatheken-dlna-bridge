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
import de.corelogics.mediaview.service.proxy.downloader.*;
import de.corelogics.mediaview.util.HttpUtils;
import de.corelogics.mediaview.util.IdUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.Callback;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.stream.Collectors;

public class ForwardingProxyServer implements ClipContentUrlGenerator {
    private final Logger logger = LogManager.getLogger(ForwardingProxyServer.class);

    private final ClipRepository clipRepository;
    private final MainConfiguration mainConfiguration;
    private final DownloadManager downloadManager;


    public ForwardingProxyServer(MainConfiguration mainConfiguration, Server jettyServer, ClipRepository clipRepository, DownloadManager downloadManager) {
        this.mainConfiguration = mainConfiguration;
        this.downloadManager = downloadManager;
        this.clipRepository = clipRepository;

        if (null == jettyServer.getContext()) {
            var context = new ContextHandlerCollection();
            jettyServer.setHandler(context);
        }
        logger.debug("Starting HTTP proxy server on port {}", mainConfiguration::publicHttpPort);
        var context = (ContextHandlerCollection) jettyServer.getHandler();
        if (null == context) {
            context = new ContextHandlerCollection();
            jettyServer.setHandler(context);
        }
        var handler = new ContextHandler("/api/v1/clips");
        handler.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                try {
                    switch (request.getMethod().toUpperCase(Locale.US)) {
                        case "HEAD":
                            handleHead(request, response, callback);
                            return true;
                        case "GET":
                            handleGetClip(request, response, callback);
                            return true;
                    }
                } catch (RuntimeException e) {
                    Response.writeError(request, response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500);
                }
                return true;
            }
        });
        context.addHandler(handler);
//        Spark.port(mainConfiguration.publicHttpPort());
        logger.info("Successfully started prefetching HTTP proxy server on port {}", mainConfiguration::publicHttpPort);
    }

    public String createLinkTo(ClipEntry e) {
        var baseUrl = mainConfiguration.publicBaseUrl();
        return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "/api/v1/clips/" + IdUtils.encodeId(e.getId());
    }


    private Object handleHead(Request request, Response response, Callback callback) {
        var pathInContext = Request.getPathInContext(request).split("/");
        if (pathInContext.length == 0) {
            throw new RuntimeException("cant extract clip from URL " + pathInContext);
        }
        var clipIdString = pathInContext[pathInContext.length - 1];
        var clipId = new String(Base64.getDecoder().decode(clipIdString), StandardCharsets.UTF_8);
        logger.debug("HEAD Request for clip {}\n{}", clipId::toString, () ->
                String.join("\n",
                        "   H:" + request.getHttpURI().getHost(),
                        "   P:" + pathInContext,
                        request.getHeaders().stream().map(h -> "   " + h.getName() + ": " + h.getValue()).collect(Collectors.joining("\n"))));
        clipRepository.findClipById(clipId).ifPresentOrElse(
                clip -> {
                    var byteRange = new ByteRange(0, 1);
                    try (var stream = downloadManager.openStreamFor(clip, byteRange)) {
                        try {
                            response.getHeaders().add(HttpUtils.HEADER_CONTENT_TYPE, stream.getContentType());
                            response.getHeaders().add(HttpUtils.HEADER_ACCEPT_RANGES, "bytes");
                            response.getHeaders().add(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(stream.getMaxSize()));
                        } finally {
                            logger.debug("Closing consumer stream");
                        }
                    } catch (UpstreamNotFoundException e) {
                        logger.info("Clip {} wasn't found at upstream url {}", clip::getTitle, clip::getBestUrl);
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    } catch (EOFException e) {
                        logger.info("Requested range {} of clip {} is beyond clip. Redirecting client to original url: {}", byteRange, clip.getId(), clip.getBestUrl());
                        logger.debug("e");
                        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    } catch (UpstreamReadFailedException e) {
                        logger.info("Upstream server failed for clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                        logger.debug(e);
                        Response.sendRedirect(request, response, callback, clip.getBestUrl());
                    } catch (TooManyConcurrentConnectionsException e) {
                        logger.info("Can't proxy clip {}[{}], because there are too many concurrent connections open. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                        Response.sendRedirect(request, response, callback, clip.getBestUrl());
                    } catch (CacheSizeExhaustedException e) {
                        logger.info("Cache size exhausted when requesting clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                        logger.debug(e);
                        Response.sendRedirect(request, response, callback, clip.getBestUrl());
                    }
                },
                () -> {
                    logger.debug("Head request for {}: not found", clipId);
                    Response.writeError(request, response, callback, HttpServletResponse.SC_NOT_FOUND);
                });
        return null;
    }

    //
    private Object handleGetClip(Request request, Response response, Callback callback) {
        var pathInContext = Request.getPathInContext(request).split("/");
        if (pathInContext.length == 0) {
            throw new RuntimeException("cant extract clip from URL " + pathInContext);
        }
        var clipIdString = pathInContext[pathInContext.length - 1];
        var clipId = new String(Base64.getDecoder().decode(clipIdString), StandardCharsets.UTF_8);
        logger.debug("Request for clip {}\n{}", clipId::toString, () ->
                String.join("\n",
                        "   H:" + request.getHttpURI().getHost(),
                        "   P:" + pathInContext,
                        request.getHeaders().stream().map(h -> "   " + h.getName() + ": " + h.getValue()).collect(Collectors.joining("\n"))));
        clipRepository.findClipById(clipId).ifPresentOrElse(
                clip -> {
                    var byteRange = new ByteRange(request.getHeaders().get(HttpUtils.HEADER_RANGE));
                    try (var stream = downloadManager.openStreamFor(clip, byteRange)) {
                        try {
                            if (byteRange.getFirstPosition() >= stream.getMaxSize()) {
                                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                            } else {
                                response.getHeaders().add(HttpUtils.HEADER_CONTENT_TYPE, stream.getContentType());
                                response.getHeaders().add(HttpUtils.HEADER_ACCEPT_RANGES, "bytes");
                                if (request.getHeaders().get(HttpUtils.HEADER_RANGE) != null) {
                                    response.getHeaders().add(HttpUtils.HEADER_CONTENT_RANGE, "bytes " + byteRange.getFirstPosition() + "-" + byteRange.getLastPosition().orElse(stream.getMaxSize() - 1) + "/" + stream.getMaxSize());
                                    response.getHeaders().add(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(byteRange.getLastPosition().orElse(stream.getMaxSize()) - byteRange.getFirstPosition()));
                                } else {
                                    response.getHeaders().add(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(stream.getMaxSize()));
                                }
                                copyBytes(request, stream.getStream(), response);
                                callback.succeeded();
                            }
                        } finally {
                            logger.debug("Closing consumer stream");
                        }
                    } catch (UpstreamNotFoundException e) {
                        logger.info("Clip {} wasn't found at upstream url {}", clip::getTitle, clip::getBestUrl);
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    } catch (EOFException e) {
                        logger.info("Requested range {} of clip {} is beyond clip. Redirecting client to original url: {}", byteRange, clip.getId(), clip.getBestUrl());
                        logger.debug("e");
                        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    } catch (UpstreamReadFailedException e) {
                        logger.info("Upstream server failed for clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                        logger.debug(e);
                        Response.sendRedirect(request, response, callback, clip.getBestUrl());
                    } catch (TooManyConcurrentConnectionsException e) {
                        logger.info("Can't proxy clip {}[{}], because there are too many concurrent connections open. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                        Response.sendRedirect(request, response, callback, clip.getBestUrl());
                    } catch (CacheSizeExhaustedException e) {
                        logger.info("Cache size exhausted when requesting clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                        logger.debug(e);
                        Response.sendRedirect(request, response, callback, clip.getBestUrl());
                    }
                },
                () -> {
                    logger.info("Requested clipId {} wasn't found in DB.", clipId);
                    Response.writeError(request, response, callback, HttpServletResponse.SC_NOT_FOUND);
                }
        );
        return null;
    }

    private void copyBytes(Request request, InputStream from, Response to) {
        try (var toStream = Response.asBufferedOutputStream(request, to)) {
            IOUtils.copyLarge(from, toStream);
        } catch (final IOException e) {
            logger.debug("Client closed connection. Aborting.");
        }
    }
}
