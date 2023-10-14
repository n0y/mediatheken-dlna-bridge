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
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Log4j2
public class ForwardingProxyServer implements ClipContentUrlGenerator {
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
        log.debug("Starting HTTP proxy server on port {}", mainConfiguration::publicHttpPort);
        var context = (ContextHandlerCollection) jettyServer.getHandler();
        if (null == context) {
            context = new ContextHandlerCollection();
            jettyServer.setHandler(context);
        }
        var servletHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletHandler.setDisplayName("asd");
        servletHandler.setContextPath("/api/v1/clips");
        final ServletHolder holder = new ServletHolder("jUpnpServler", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                handleGetClip(req, resp);
            }

            @Override
            protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                handleHead(req, resp);
            }
        });
        servletHandler.addServlet(holder, "/*");
        context.addHandler(servletHandler);
        log.info("Successfully started prefetching HTTP proxy server on port {}", mainConfiguration::publicHttpPort);
    }

    public String createLinkTo(ClipEntry e) {
        var baseUrl = mainConfiguration.publicBaseUrl();
        return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v1/clips/" + IdUtils.encodeId(e.getId());
    }


    private void handleHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var pathInContextString = request.getPathInfo();
        var pathInContext = pathInContextString.split("/");
        if (pathInContext.length == 0) {
            throw new RuntimeException("cant extract clip from URL " + pathInContextString);
        }
        var clipIdString = pathInContext[pathInContext.length - 1];
        var clipId = new String(Base64.getDecoder().decode(clipIdString), StandardCharsets.UTF_8);
        log.debug("HEAD Request for clip {}\n{}", clipId::toString, () ->
                String.join("\n",
                        "   H:" + request.getServerName(),
                        "   P:" + pathInContextString,
                        headerStrings(request)));
        var optionalClip = clipRepository.findClipById(clipId);
        if (optionalClip.isPresent()) {
            var clip = optionalClip.get();
            var byteRange = new ByteRange(0, 1);
            try (var stream = downloadManager.openStreamFor(clip, byteRange)) {
                try {
                    response.addHeader(HttpUtils.HEADER_CONTENT_TYPE, stream.getContentType());
                    response.addHeader(HttpUtils.HEADER_ACCEPT_RANGES, "bytes");
                    response.addHeader(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(stream.getMaxSize()));
                } finally {
                    log.debug("Closing consumer stream");
                }
            } catch (UpstreamNotFoundException e) {
                log.info("Clip {} wasn't found at upstream url {}", clip::getTitle, clip::getBestUrl);
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (EOFException e) {
                log.info("Requested range {} of clip {} is beyond clip. Redirecting client to original url: {}", byteRange, clip.getId(), clip.getBestUrl());
                log.debug("e");
                response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            } catch (UpstreamReadFailedException e) {
                log.info("Upstream server failed for clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                log.debug(e);
                response.sendRedirect(clip.getBestUrl());
            } catch (TooManyConcurrentConnectionsException e) {
                log.info("Can't proxy clip {}[{}], because there are too many concurrent connections open. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                response.sendRedirect(clip.getBestUrl());
            } catch (CacheSizeExhaustedException e) {
                log.info("Cache size exhausted when requesting clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                log.debug(e);
                response.sendRedirect(clip.getBestUrl());
            }
        } else {
            log.debug("Head request for {}: not found", clipId);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    //
    private void handleGetClip(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var pathInContextString = request.getPathInfo();
        var pathInContext = pathInContextString.split("/");
        if (pathInContext.length == 0) {
            throw new RuntimeException("cant extract clip from URL " + pathInContextString);
        }
        var clipIdString = pathInContext[pathInContext.length - 1];
        var clipId = new String(Base64.getDecoder().decode(clipIdString), StandardCharsets.UTF_8);
        log.debug("Request for clip {}\n{}", clipId::toString, () ->
                String.join("\n",
                        "   H:" + request.getServerName(),
                        "   P:" + pathInContextString,
                        headerStrings(request)));
        var optionalClip = clipRepository.findClipById(clipId);
        if (optionalClip.isPresent()) {
            var clip = optionalClip.get();
            var byteRange = new ByteRange(request.getHeader(HttpUtils.HEADER_RANGE));
            try (var stream = downloadManager.openStreamFor(clip, byteRange)) {
                try {
                    if (byteRange.getFirstPosition() >= stream.getMaxSize()) {
                        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    } else {
                        if (request.getHeader(HttpUtils.HEADER_RANGE) != null) {
                            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                            response.addHeader(HttpUtils.HEADER_CONTENT_RANGE, "bytes " + byteRange.getFirstPosition() + "-" + byteRange.getLastPosition().orElse(stream.getMaxSize() - 1) + "/" + stream.getMaxSize());
                            response.addHeader(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(byteRange.getLastPosition().orElse(stream.getMaxSize()) - byteRange.getFirstPosition()));
                        } else {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.addHeader(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(stream.getMaxSize()));
                        }
                        response.addHeader(HttpUtils.HEADER_CONTENT_TYPE, stream.getContentType());
                        response.addHeader(HttpUtils.HEADER_ACCEPT_RANGES, "bytes");
                        log.debug("Answering with: " + headerStrings(response));
                        copyBytes(stream.getStream(), response);
                    }
                } finally {
                    log.debug("Closing consumer stream");
                }
            } catch (UpstreamNotFoundException e) {
                log.info("Clip {} wasn't found at upstream url {}", clip::getTitle, clip::getBestUrl);
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (EOFException e) {
                log.info("Requested range {} of clip {} is beyond clip. Redirecting client to original url: {}", byteRange, clip.getId(), clip.getBestUrl());
                log.debug("e");
                response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            } catch (UpstreamReadFailedException e) {
                log.info("Upstream server failed for clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                log.debug(e);
                response.sendRedirect(clip.getBestUrl());
            } catch (TooManyConcurrentConnectionsException e) {
                log.info("Can't proxy clip {}[{}], because there are too many concurrent connections open. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                response.sendRedirect(clip.getBestUrl());
            } catch (CacheSizeExhaustedException e) {
                log.info("Cache size exhausted when requesting clip {}[{}]. Redirecting client to original url: {}", clip.getId(), byteRange, clip.getBestUrl());
                log.debug(e);
                response.sendRedirect(clip.getBestUrl());
            }
        } else {
            log.info("Requested clipId {} wasn't found in DB.", clipId);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private String headerStrings(HttpServletRequest request) {
        return StreamSupport.stream(((Iterable<String>) () -> request.getHeaderNames().asIterator()).spliterator(), false)
                .map(h -> "   " + h + ": " + request.getHeader(h))
                .collect(Collectors.joining("\n"));
    }

    private String headerStrings(HttpServletResponse response) {
        return response.getHeaderNames().stream()
                .map(h -> "   " + h + ": " + response.getHeader(h))
                .collect(Collectors.joining("\n"));
    }

    private void copyBytes(InputStream from, HttpServletResponse to) {
        try (var toStream = to.getOutputStream()) {
            IOUtils.copy(from, toStream);
        } catch (final IOException e) {
            log.debug("Client closed connection. Aborting.", e);
        }
    }
}
