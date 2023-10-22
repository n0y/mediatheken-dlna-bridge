package de.corelogics.mediaview.service.networking;

import de.corelogics.mediaview.config.MainConfiguration;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.eclipse.jetty.server.AbstractNetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.jetbrains.annotations.NotNull;
import org.jupnp.transport.impl.NetworkAddressFactoryImpl;
import org.jupnp.transport.spi.NetworkAddressFactory;

import java.net.NetworkInterface;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Log4j2
public class NetworkingModule {
    @Getter
    private final Server jettyServer;
    private final NetworkAddressFactory networkAddressFactory = new NetworkAddressFactoryImpl();
    private final MainConfiguration mainConfiguration;

    public NetworkingModule(MainConfiguration mainConfiguration) {
        this.mainConfiguration = mainConfiguration;
        jettyServer = createJettyServer();
    }

    private Server createJettyServer() {
        var threadPool = new QueuedThreadPool();
        threadPool.setVirtualThreadsExecutor(Executors.newVirtualThreadPerTaskExecutor());
        final var server = new Server(threadPool);
        server.setStopAtShutdown(true);

        val connectors = StreamSupport
                .stream(
                        ((Iterable<NetworkInterface>) networkAddressFactory::getNetworkInterfaces).spliterator(),
                        false)
                .flatMap(NetworkInterface::inetAddresses)
                .map(nwi -> createConnector(nwi.getHostAddress(), server))
                .toList();
        connectors.forEach(server::addConnector);
        server.addConnector(createConnector("127.0.0.1", server));
        server.addConnector(createConnector("::1", server));
        log.info("Started HTTP server on port {} for this IPs: 127.0.0.1, ::1, {}",
                mainConfiguration::publicHttpPort,
                () -> connectors.stream().map(AbstractNetworkConnector::getHost).collect(Collectors.joining(", ")));
        return server;
    }

    @NotNull
    private ServerConnector createConnector(String host, Server server) {
        var sc = new ServerConnector(server);
        sc.setHost(host);
        sc.setPort(mainConfiguration.publicHttpPort());
        return sc;
    }

    public void startup() {
        try {
            this.jettyServer.start();
        } catch (Exception e) {
            throw new RuntimeException("Could not start Jetty server", e);
        }
    }
}
