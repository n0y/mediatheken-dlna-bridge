package de.corelogics.mediaview.service.dlna.fixups;

import org.jupnp.transport.Router;
import org.jupnp.transport.impl.ServletStreamServerConfigurationImpl;
import org.jupnp.transport.impl.ServletStreamServerImpl;
import org.jupnp.transport.spi.InitializationException;

import java.io.IOException;
import java.net.InetAddress;

public class ServletStreamServerImplFixed extends ServletStreamServerImpl {
    private String hostAddress = "not set";

    public ServletStreamServerImplFixed(ServletStreamServerConfigurationImpl configuration) {
        super(configuration);
    }


    @Override
    public synchronized void init(InetAddress bindAddress, Router router) throws InitializationException {
        this.hostAddress = bindAddress.getHostAddress();
        super.init(bindAddress, router);
    }

    @Override
    public synchronized int getPort() {
        try {
            return configuration.getServletContainerAdapter().addConnector(this.hostAddress, 0);
        } catch (IOException e) {
            return 0;
        }
    }
}
