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
import de.corelogics.mediaview.repository.tracked.TrackedViewRepository;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.util.IdUtils;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

@Log4j2
public class TrackingProxyServer implements ClipContentUrlGenerator{
    private final MainConfiguration mainConfiguration;
    private final ClipRepository clipRepository;
    private final ClipContentUrlGenerator downstreamUrlGenerator;
    private final TrackedViewRepository trackedViewRepository;

    public TrackingProxyServer(
        Server jettyServer,
        MainConfiguration mainConfiguration,
        ClipContentUrlGenerator downstreamUrlGenerator,
        ClipRepository clipRepository,
        TrackedViewRepository trackedViewRepository
    ) {
        this.mainConfiguration = mainConfiguration;
        this.clipRepository = clipRepository;
        this.downstreamUrlGenerator = downstreamUrlGenerator;
        this.trackedViewRepository = trackedViewRepository;

        if (null == jettyServer.getContext()) {
            val context = new ContextHandlerCollection();
            jettyServer.setHandler(context);
            ;
        }
        final ContextHandlerCollection context;
        if (jettyServer.getHandler() instanceof ContextHandlerCollection coll) {
            context = coll;
        } else {
            context = new ContextHandlerCollection();
            jettyServer.setHandler(context);
        }

        val servletHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletHandler.setDisplayName("Track Playback");
        servletHandler.setContextPath("/api/v1/clip-trackings");

        val holder = new ServletHolder("trackingServlet", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                handleGetClip(req, resp);
            }

            @Override
            protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                handleGetClip(req, resp);
            }
        });
        servletHandler.addServlet(holder, "/*");
        context.addHandler(servletHandler);
    }

    private void handleGetClip(HttpServletRequest request, HttpServletResponse response) throws IOException {
        val clipId = extractClipId(request);
        val optionalClip = clipRepository.findClipById(clipId);
        if (optionalClip.isPresent()) {
            trackedViewRepository.addTrackedView(optionalClip.get(), ZonedDateTime.now());
            response.sendRedirect(downstreamUrlGenerator.createLinkTo(
                optionalClip.get(),
                InetAddress.getByName(request.getLocalAddr())));
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

    @Override
    public String createLinkTo(ClipEntry e, @Nullable InetAddress optionalLocalAddressQueried) {
        var baseUrl = mainConfiguration.publicBaseUrl()
            .orElseGet(() -> null == optionalLocalAddressQueried ?
                "" :
                "http://%s:%d".formatted(optionalLocalAddressQueried.getHostName(), mainConfiguration.publicHttpPort()));
        return "%s%sapi/v1/clip-trackings/%s".formatted(
            baseUrl,
            baseUrl.endsWith("/") ? "" : "/",
            IdUtils.encodeId(e.getId()));
    }

}
