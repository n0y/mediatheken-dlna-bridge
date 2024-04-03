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

import lombok.val;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.protocol.ProtocolFactoryImpl;
import org.jupnp.protocol.sync.ReceivingAction;
import org.jupnp.transport.RouterException;

public class UpnpServiceImplFixed extends UpnpServiceImpl {
    public UpnpServiceImplFixed(UpnpServiceConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void activate(Config config) {
        super.activate(config);
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
                        try (val ignored = LocalAddressHolder.memoizeLocalAddress(getInputMessage().getConnection().getLocalAddress())) {
                            return super.executeSync();
                        }
                    }
                };
            }
        };
    }
}
