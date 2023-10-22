package de.corelogics.mediaview.service.dlna.jupnp;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
        var context = (ContextHandlerCollection) server.getHandler();
        if (null == context) {
            context = new ContextHandlerCollection();
            server.setHandler(context);
        }

        if (context.getDescendants(ContextHandler.class).stream()
                .map(ContextHandler::getDisplayName)
                .noneMatch(CONTEXT_DISPLAY_NAME::equals)) {
            log.debug("Registering DLNA servlet below {}", contextPath);
            var servletHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            servletHandler.setDisplayName(CONTEXT_DISPLAY_NAME);
            if (contextPath != null && !contextPath.isEmpty()) {
                servletHandler.setContextPath(contextPath);
            }
            final ServletHolder holder = new ServletHolder("jUpnpServler", servlet);
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
