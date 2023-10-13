package de.corelogics.mediaview.service.networking;

import de.corelogics.mediaview.config.MainConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.jupnp.transport.impl.NetworkAddressFactoryImpl;
import org.jupnp.transport.spi.NetworkAddressFactory;

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

        if (mainConfiguration.publicHttpPort() > 0) {
            var sc = new ServerConnector(server);
            sc.setPort(mainConfiguration.publicHttpPort());
            server.addConnector(sc);
        }

        StreamSupport
                .stream(
                        ((Iterable<NetworkInterface>) networkAddressFactory::getNetworkInterfaces).spliterator(),
                        false)
                .flatMap(NetworkInterface::inetAddresses)
                .map(nwi -> {
                    var sc = new ServerConnector(server);
                    sc.setHost(nwi.getHostAddress());
                    sc.setPort(0);
                    return sc;
                })
                .forEach(server::addConnector);
        return server;
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
