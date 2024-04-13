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

package de.corelogics.mediaview.service.dlna.jupnp;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.jupnp.transport.spi.ServletContainerAdapter;

import javax.servlet.Servlet;
import java.util.concurrent.ExecutorService;

@AllArgsConstructor
@Log4j2
public class JettyServletContainerFixed implements ServletContainerAdapter {
    public static final String CONTEXT_DISPLAY_NAME = "jupnp-dlna";

    private final Server server;
    private final int port;

    @Override
    public synchronized void setExecutorService(ExecutorService executorService) {
        // jetty is already configured here
    }

    @Override
    public synchronized int addConnector(String host, int port) {
        return this.port;
    }

    @Override
    public synchronized void registerServlet(String contextPath, Servlet servlet) {
        final ContextHandlerCollection context;
        if (server.getHandler() instanceof ContextHandlerCollection coll) {
            context = coll;
        } else {
            context = new ContextHandlerCollection();
            server.setHandler(context);
        }

        if (context.getDescendants(ContextHandler.class).stream()
            .map(ContextHandler::getDisplayName)
            .noneMatch(CONTEXT_DISPLAY_NAME::equals)) {
            log.debug("Registering DLNA servlet below {}", contextPath);
            val servletHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            servletHandler.setDisplayName(CONTEXT_DISPLAY_NAME);
            if (contextPath != null && !contextPath.isEmpty()) {
                servletHandler.setContextPath(contextPath);
            }
            final ServletHolder holder = new ServletHolder("jUpnpServlet", servlet);
            servletHandler.addServlet(holder, "/*");
            context.addHandler(servletHandler);
        }
    }

    @Override
    public synchronized void startIfNotRunning() {
        // won't do that here
    }

    @Override
    public synchronized void stopIfRunning() {
        // won't do that here
    }
}
