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

import org.eclipse.jetty.server.Server;
import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.model.Namespace;
import org.jupnp.model.types.ServiceType;
import org.jupnp.transport.impl.ServletStreamServerConfigurationImpl;
import org.jupnp.transport.impl.ServletStreamServerImpl;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

import static java.util.concurrent.TimeUnit.DAYS;

public class DlnaUpnpServiceConfiguration extends DefaultUpnpServiceConfiguration {
    private final JettyServletContainerFixed servletContainer;

    public DlnaUpnpServiceConfiguration(Server jettyServer, int port) {
        this.servletContainer = new JettyServletContainerFixed(jettyServer, port);
    }

    @Override
    protected Namespace createNamespace() {
        return new Namespace("/dlna");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public StreamClient createStreamClient() {
        // this disables discovery, which we don't need here
        return null;
    }

    @Override
    public ServiceType[] getExclusiveServiceTypes() {
        // this disables discovery, which we don't need here
        return null;
    }

    @Override
    public StreamServer<ServletStreamServerConfigurationImpl> createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new ServletStreamServerImpl(
            new ServletStreamServerConfigurationImpl(
                servletContainer,
                networkAddressFactory.getStreamListenPort()));
    }


    @Override
    public int getRegistryMaintenanceIntervalMillis() {
        // we disabled discovery, so maintenance is not needed
        return (int) DAYS.toMillis(1);
    }
}
