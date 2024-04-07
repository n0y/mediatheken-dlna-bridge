/*
 * MIT License
 *
 * Copyright (c) 2020-2024 Mediatheken DLNA Bridge Authors.
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
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static javax.servlet.http.HttpServletResponse.*;

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
            val context = new ContextHandlerCollection();
            jettyServer.setHandler(context);
        }
        final ContextHandlerCollection context;
        if (jettyServer.getHandler() instanceof ContextHandlerCollection coll) {
            context = coll;
        } else {
            context = new ContextHandlerCollection();
            jettyServer.setHandler(context);
        }

        val servletHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletHandler.setDisplayName("Buffered Playback");
        servletHandler.setContextPath("/api/v1/clip-contents");
        val holder = new ServletHolder("jUpnpServlet", new HttpServlet() {
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
        log.debug("Successfully registering prefetching HTTP servlet.");
    }

    @Override
    public String createLinkTo(ClipEntry e, @Nullable InetAddress optionalLocalAddressQueried) {
        var baseUrl = mainConfiguration.publicBaseUrl()
            .orElseGet(() -> null == optionalLocalAddressQueried ?
                "" :
                "http://%s:%d".formatted(optionalLocalAddressQueried.getHostName(), mainConfiguration.publicHttpPort()));
        return "%s%sapi/v1/clip-contents/%s".formatted(
            baseUrl,
            baseUrl.endsWith("/") ? "" : "/",
            IdUtils.encodeId(e.getId()));
    }

    private void handleHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        val clipId = extractClipId(request);
        val optionalClip = clipRepository.findClipById(clipId);
        if (optionalClip.isPresent()) {
            val clip = optionalClip.get();
            val byteRange = new ByteRange(0, 1);
            try (val stream = downloadManager.openStreamFor(clip, byteRange)) {
                try {
                    response.addHeader(HttpUtils.HEADER_CONTENT_TYPE, stream.getContentType());
                    response.addHeader(HttpUtils.HEADER_ACCEPT_RANGES, "bytes");
                    response.addHeader(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(stream.getMaxSize()));
                } finally {
                    log.debug("Closing consumer stream");
                }
            } catch (UpstreamNotFoundException e) {
                log.info("Clip {} wasn't found at upstream url {}", clip::getTitle, clip::getBestUrl);
                response.sendError(SC_NOT_FOUND);
            } catch (EOFException e) {
                log.info("Requested range {} of clip {} is beyond clip. Redirecting client to original url: {}", byteRange, clip.getId(), clip.getBestUrl());
                log.debug("e");
                response.sendError(SC_REQUESTED_RANGE_NOT_SATISFIABLE);
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
            response.sendError(SC_NOT_FOUND);
        }
    }

    private void handleGetClip(HttpServletRequest request, HttpServletResponse response) throws IOException {
        val clipId = extractClipId(request);
        val optionalClip = clipRepository.findClipById(clipId);
        if (optionalClip.isPresent()) {
            val clip = optionalClip.get();
            val byteRange = new ByteRange(request.getHeader(HttpUtils.HEADER_RANGE));
            try (val stream = downloadManager.openStreamFor(clip, byteRange)) {
                try {
                    if (byteRange.getFirstPosition() >= stream.getMaxSize()) {
                        response.setStatus(SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    } else {
                        if (byteRange.isPartial()) {
                            response.setStatus(SC_PARTIAL_CONTENT);
                            response.addHeader(HttpUtils.HEADER_CONTENT_RANGE, STR."bytes \{byteRange.getFirstPosition()}-\{byteRange.getLastPosition().orElse(stream.getMaxSize() - 1)}/\{stream.getMaxSize()}");
                            response.addHeader(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(byteRange.getLastPosition().orElse(stream.getMaxSize()) - byteRange.getFirstPosition()));
                        } else {
                            response.setStatus(SC_OK);
                            response.addHeader(HttpUtils.HEADER_CONTENT_LENGTH, Long.toString(stream.getMaxSize()));
                        }
                        log.debug("Answering with: {}", headerStrings(response));
                        copyBytes(stream.getStream(), response);
                    }
                } finally {
                    log.debug("Closing consumer stream");
                }
            } catch (UpstreamNotFoundException e) {
                log.info("Clip {} wasn't found at upstream url {}", clip::getTitle, clip::getBestUrl);
                response.sendError(SC_NOT_FOUND);
            } catch (EOFException e) {
                log.info("Requested range {} of clip {} is beyond clip. Redirecting client to original url: {}", byteRange, clip.getId(), clip.getBestUrl());
                log.debug("e");
                response.sendError(SC_REQUESTED_RANGE_NOT_SATISFIABLE);
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
            response.sendError(SC_NOT_FOUND);
        }
    }

    private String extractClipId(HttpServletRequest request) {
        val pathInContextString = request.getPathInfo();
        val pathInContext = pathInContextString.split("/");
        if (pathInContext.length == 0) {
            throw new RuntimeException(STR."cant extract clip from URL \{pathInContextString}");
        }
        val clipIdString = pathInContext[pathInContext.length - 1];
        val clipId = new String(Base64.getDecoder().decode(clipIdString), StandardCharsets.UTF_8);
        log.debug(
            "Loading clip for {} request: clip {}\n{}",
            request::getMethod,
            clipId::toString,
            () -> String.join(
                "\n",
                    STR."   H:\{request.getServerName()}",
                    STR."   P:\{pathInContextString}",
                headerStrings(request)));
        return clipId;
    }

    private String headerStrings(HttpServletRequest request) {
        return StreamSupport.stream(((Iterable<String>) () -> request.getHeaderNames().asIterator()).spliterator(), false)
            .map(h -> STR."   \{h}: \{request.getHeader(h)}")
            .collect(Collectors.joining("\n"));
    }

    private String headerStrings(HttpServletResponse response) {
        return response.getHeaderNames().stream()
            .map(h -> STR."   \{h}: \{response.getHeader(h)}")
            .collect(Collectors.joining("\n"));
    }

    private void copyBytes(InputStream from, HttpServletResponse to) {
        try (val toStream = to.getOutputStream()) {
            IOUtils.copy(from, toStream);
        } catch (final IOException e) {
            log.debug("Client closed connection. Aborting.");
        }
    }
}
