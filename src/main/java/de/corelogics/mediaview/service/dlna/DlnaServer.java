
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.*;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class DlnaServer {
    private final Logger logger = LogManager.getLogger(DlnaServer.class);

    private final UpnpServiceImpl upnpService;

    public DlnaServer(MainConfiguration mainConfiguration, Set<DlnaRequestHandler> handlers) throws ValidationException {
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

        var localDevice = new LocalDevice(
                new DeviceIdentity(new UDN(UUID.nameUUIDFromBytes(mainConfiguration.displayName().getBytes(StandardCharsets.UTF_8)))),
                type,
                details,
                service);

        this.upnpService = new UpnpServiceImpl();
        upnpService.getRegistry().addDevice(localDevice);
        logger.info(String.format("Successfully started DLNA server '%s'. It may take some time for it to become visible in the network.",
                mainConfiguration.displayName()));
    }

    public void shutdown() {
        upnpService.shutdown();
    }
}
