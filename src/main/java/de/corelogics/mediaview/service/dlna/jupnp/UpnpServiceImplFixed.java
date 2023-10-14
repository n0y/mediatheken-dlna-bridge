package de.corelogics.mediaview.service.dlna.jupnp;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;

import java.util.Map;

public class UpnpServiceImplFixed extends UpnpServiceImpl {
    public UpnpServiceImplFixed(UpnpServiceConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void activate(Map<String, Object> configProperties) {
        super.activate(configProperties);
        // Didn't understand why upstream initializes an ExecutorService here
        scheduledExecutorService.shutdown();
    }
}
