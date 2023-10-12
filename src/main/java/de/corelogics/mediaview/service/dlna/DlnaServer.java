
/*
 * MIT License
 *
 * Copyright (c) 2020-2021 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.service.dlna;

import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.service.fixups.JettyServletContainerFixed;
import de.corelogics.mediaview.service.fixups.JettyStreamClientImplFixed;
import de.corelogics.mediaview.service.fixups.ServletStreamServerImplFixed;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.binding.annotations.AnnotationLocalServiceBinder;
import org.jupnp.model.DefaultServiceManager;
import org.jupnp.model.ValidationException;
import org.jupnp.model.meta.*;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDN;
import org.jupnp.transport.impl.ServletStreamServerConfigurationImpl;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;

public class DlnaServer {
    private final Logger logger = LogManager.getLogger(DlnaServer.class);

    private static class DlnaUpnpServiceConfiguration extends DefaultUpnpServiceConfiguration {
        private final JettyServletContainerFixed servletContainer;

        private DlnaUpnpServiceConfiguration(Server jettyServer) {
            this.servletContainer = new JettyServletContainerFixed(jettyServer);
        }

        @Override
        protected ExecutorService createDefaultExecutorService() {
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        @Override
        public StreamClient createStreamClient() {
            return new JettyStreamClientImplFixed(new StreamClientConfigurationImpl(getSyncProtocolExecutorService()));
        }

        @Override
        public StreamServer createStreamServer(NetworkAddressFactory networkAddressFactory) {
            return new ServletStreamServerImplFixed(
                    new ServletStreamServerConfigurationImpl(
                            servletContainer,
                            networkAddressFactory.getStreamListenPort())) {
                @Override
                public synchronized int getPort() {
                    return super.getPort();
                }
            };
        }

        @Override
        public int getRegistryMaintenanceIntervalMillis() {
            return (int) SECONDS.toMillis(60);
        }
    }

    private final UpnpServiceImpl upnpService;

    public DlnaServer(MainConfiguration mainConfiguration, Server jettyServer, Set<DlnaRequestHandler> handlers) throws ValidationException {
        logger.debug("Starting DLNA server");

        var type = new UDADeviceType("MediaServer", 1);
        var details = new DeviceDetails(
                mainConfiguration.displayName(),
                new ManufacturerDetails("Mediatheken DLNA Gateway"),
                new ModelDetails("Mediatheken", "v1", "v.1.0.0", "https://github.com/n0y/mediatheken-dlna-bridge"));
        var service = (LocalService<ContentDirectory>) new AnnotationLocalServiceBinder().read(ContentDirectory.class);
        service.setManager(new DefaultServiceManager<>(service, ContentDirectory.class) {
            @Override
            protected ContentDirectory createServiceInstance() {
                return new ContentDirectory(handlers);
            }
        });

        final var localDevice = new LocalDevice(
                new DeviceIdentity(new UDN(UUID.nameUUIDFromBytes(mainConfiguration.displayName().getBytes(StandardCharsets.UTF_8)))),
                type,
                details,
                service);

        this.upnpService = new UpnpServiceImpl(new DlnaUpnpServiceConfiguration(jettyServer));
        this.upnpService.startup();
        this.upnpService.getRegistry().addDevice(localDevice);
        this.upnpService.getProtocolFactory().createSendingNotificationAlive(localDevice).run();
        logger.info(String.format("Successfully started DLNA server '%s'. It may take some time for it to become visible in the network.",
                mainConfiguration.displayName()));

    }

    public void shutdown() {
        upnpService.shutdown();
    }
}
