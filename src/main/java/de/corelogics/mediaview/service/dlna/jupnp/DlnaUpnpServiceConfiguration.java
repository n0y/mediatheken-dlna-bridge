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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    protected ExecutorService createDefaultExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
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
