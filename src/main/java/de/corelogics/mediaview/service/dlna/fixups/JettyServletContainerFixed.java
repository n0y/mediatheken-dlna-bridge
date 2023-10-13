package de.corelogics.mediaview.service.dlna.fixups;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.jupnp.transport.spi.ServletContainerAdapter;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

public class JettyServletContainerFixed implements ServletContainerAdapter {
    public static final String CONTEXT_DISPLAY_NAME = "jupnp-dlna";
    private final Logger log = LogManager.getLogger(JettyServletContainerFixed.class);
    private final Server server;

    public JettyServletContainerFixed(Server server) {
        this.server = server;
    }

    @Override
    public synchronized void setExecutorService(ExecutorService executorService) {
        // won't do that here
    }

    @Override
    public synchronized int addConnector(String host, int port) throws IOException {
        return Arrays.stream(server.getConnectors())
                .filter(c -> c instanceof ServerConnector)
                .map(c -> (ServerConnector) c)
                .filter(sc -> host.equals(sc.getHost()))
                .flatMapToInt(sc -> 0 == sc.getPort() ? IntStream.of(sc.getLocalPort()) : IntStream.empty())
                .findFirst()
                .orElse(port);
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
            log.info("Registering UPnP servlet under context path: " + contextPath);
            var servletHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            servletHandler.setDisplayName(CONTEXT_DISPLAY_NAME);
            if (contextPath != null && !contextPath.isEmpty()) {
                servletHandler.setContextPath(contextPath);
            }
            final ServletHolder s = new ServletHolder("jUpnpServler", servlet);
            servletHandler.addServlet(s, "/*");
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
