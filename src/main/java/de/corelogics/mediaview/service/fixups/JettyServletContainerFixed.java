package de.corelogics.mediaview.service.fixups;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.jupnp.transport.spi.ServletContainerAdapter;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

public class JettyServletContainerFixed implements ServletContainerAdapter {
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
                .filter(sc -> sc.getHost().equals(host))
                .mapToInt(ServerConnector::getLocalPort)
                .findFirst()
                .orElse(port);
    }

    @Override
    public synchronized void registerServlet(String contextPath, Servlet servlet) {
        if (server.getHandler() != null) {
            log.trace("Server handler is already set: {}", server.getHandler());
            return;
        }
        log.info("Registering UPnP servlet under context path: " + contextPath);
        ServletContextHandler servletHandler =
                new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        if (contextPath != null && contextPath.length() > 0) {
            servletHandler.setContextPath(contextPath);
        }
        final ServletHolder s = new ServletHolder(servlet);
        servletHandler.addServlet(s, "/*");
        server.setHandler(servletHandler);
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
