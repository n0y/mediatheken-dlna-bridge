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

package de.corelogics.mediaview.service.base.networking;

import de.corelogics.mediaview.service.base.lifecycle.ShutdownRegistry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Log4j2
public class WebServer {
    @Getter
    private final Server server;
    private final ShutdownRegistry shutdownRegistry;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public ContextHandlerCollection getContextHandlerCollection() {
        if (null == server.getContext()) {
            val context = new ContextHandlerCollection();
            server.setHandler(context);
        }
        final ContextHandlerCollection context;
        if (server.getHandler() instanceof ContextHandlerCollection coll) {
            context = coll;
        } else {
            context = new ContextHandlerCollection();
            server.setHandler(context);
        }
        return context;
    }

    @SneakyThrows
    public void startup() {
        if (started.compareAndSet(false, true)) {
            this.server.start();
            this.shutdownRegistry.registerShutdown(this::stop);
            log.info("Started HTTP server listening to: {}",
                () -> Arrays.stream(server.getConnectors())
                    .filter(c -> c instanceof ServerConnector)
                    .map(c -> (ServerConnector) c)
                    .map(sc -> STR."\{sc.getHost()}:\{sc.getPort()}")
                    .collect(Collectors.joining(", ")));
        }
    }

    @SneakyThrows
    private void stop() {
        log.debug("Shutting down");
        this.server.stop();
    }
}
