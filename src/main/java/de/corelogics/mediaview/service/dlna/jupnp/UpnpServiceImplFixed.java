package de.corelogics.mediaview.service.dlna.jupnp;

import lombok.val;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.protocol.ProtocolFactoryImpl;
import org.jupnp.protocol.sync.ReceivingAction;
import org.jupnp.transport.RouterException;

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

    @Override
    protected ProtocolFactory createProtocolFactory() {
        return new ProtocolFactoryImpl(this) {
            @Override
            protected ReceivingAction createReceivingAction(StreamRequestMessage message) {
                return new ReceivingAction(getUpnpService(), message) {
                    @Override
                    protected StreamResponseMessage executeSync() throws RouterException {
                        // This hack is here because JUpnp doesn't hand over the local address
                        // to the ContentDirectory, where we need it to propagate the
                        // correct clip URLs.
                        try (val _ = LocalAddressHolder.memoizeLocalAddress(getInputMessage().getConnection().getLocalAddress())) {
                            return super.executeSync();
                        }
                    }
                };
            }
        };
    }
}
