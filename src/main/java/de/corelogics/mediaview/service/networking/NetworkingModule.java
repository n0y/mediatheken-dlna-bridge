package de.corelogics.mediaview.service.networking;

import de.corelogics.mediaview.config.MainConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.jetbrains.annotations.NotNull;
import org.jupnp.transport.impl.NetworkAddressFactoryImpl;
import org.jupnp.transport.spi.NetworkAddressFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.concurrent.Executors;
import java.util.stream.StreamSupport;

public class NetworkingModule {
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

        StreamSupport
                .stream(
                        ((Iterable<NetworkInterface>) networkAddressFactory::getNetworkInterfaces).spliterator(),
                        false)
                .flatMap(NetworkInterface::inetAddresses)
                .map(nwi -> {
                    return createConnector(nwi.getHostAddress(), server);
                })
                .forEach(server::addConnector);
        server.addConnector(createConnector("127.0.0.1", server));
        server.addConnector(createConnector("::1", server));
        return server;
    }

    @NotNull
    private ServerConnector createConnector(String host, Server server) {
        var sc = new ServerConnector(server);
        sc.setHost(host);
        sc.setPort(mainConfiguration.publicHttpPort());
        return sc;
    }

    public Server getJettyServer() {
        return jettyServer;
    }

    public void startup() {
        try {
            this.jettyServer.start();
        } catch (Exception e) {
            throw new RuntimeException("Could not start Jetty server", e);
        }
    }
}
