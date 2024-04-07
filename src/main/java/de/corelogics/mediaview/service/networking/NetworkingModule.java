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

package de.corelogics.mediaview.service.networking;

import de.corelogics.mediaview.config.MainConfiguration;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.jetbrains.annotations.NotNull;
import org.jupnp.transport.impl.NetworkAddressFactoryImpl;
import org.jupnp.transport.spi.NetworkAddressFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        val threadPool = new QueuedThreadPool();
        threadPool.setVirtualThreadsExecutor(Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("jetty-", 0L).factory()));
        val server = new Server(threadPool);
        server.setStopAtShutdown(true);

        StreamSupport
            .stream(
                ((Iterable<NetworkInterface>) networkAddressFactory::getNetworkInterfaces).spliterator(),
                false)
            .flatMap(NetworkInterface::inetAddresses)
            .map(nwi -> createConnector(nwi.getHostAddress(), server))
            .forEach(server::addConnector);
        try {
            Stream.of(InetAddress.getAllByName("localhost"))
                .map(InetAddress::getHostAddress)
                .map(a -> createConnector(a, server))
                .forEach(server::addConnector);
        } catch (UnknownHostException e) {
            log.info("Could not add localhost addresses to Jetty server. It's unknown. Ignoring it.");
        }
        return server;
    }

    @NotNull
    private ServerConnector createConnector(String host, Server server) {
        val sc = new ServerConnector(server);
        sc.setHost(host);
        sc.setPort(mainConfiguration.publicHttpPort());
        return sc;
    }

    public void startup() {
        try {
            this.jettyServer.start();
            log.info("Started HTTP server listening to: {}",
                () -> Arrays.stream(this.jettyServer.getConnectors())
                    .filter(c -> c instanceof ServerConnector)
                    .map(c -> (ServerConnector) c)
                    .map(sc -> STR."\{sc.getHost()}:\{sc.getPort()}")
                    .collect(Collectors.joining(", ")));
        } catch (Exception e) {
            throw new RuntimeException("Could not start Jetty server", e);
        }
    }
}
