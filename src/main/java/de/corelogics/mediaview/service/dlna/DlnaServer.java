/*
 * MIT License
 *
 * Copyright (c) 2020 corelogics.de
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

import de.corelogics.mediaview.repository.clip.ClipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.*;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;

import javax.inject.Inject;
import java.util.UUID;


public class DlnaServer {
    private final Logger logger = LogManager.getLogger();

    private final ClipRepository clipRepository;

    @Inject
    public DlnaServer(ClipRepository clipRepository) {
        this.clipRepository = clipRepository;
    }

    public void start() throws ValidationException {
        logger.debug("Starting DLNA server");
        var type = new UDADeviceType("MediaServer", 1);
        var details = new DeviceDetails(
                "Mediatheken",
                new ManufacturerDetails("Corelogics Mediathek Gateway"),
                new ModelDetails("First", "Mein Erstes Modell", "v1"));
        var service = (LocalService<ContentDirectory>) new AnnotationLocalServiceBinder().read(ContentDirectory.class);
        service.setManager(new DefaultServiceManager<>(service, ContentDirectory.class) {
            @Override
            protected ContentDirectory createServiceInstance() {
                return new ContentDirectory(clipRepository);
            }
        });

        var localDevice = new LocalDevice(
                new DeviceIdentity(new UDN(UUID.nameUUIDFromBytes("Corelogics Mediathek Gateway".getBytes()))),
                type,
                details,
                service);

        var upnpService = new UpnpServiceImpl();
        upnpService.getRegistry().addDevice(localDevice);
        logger.info("Successfully started DLNA server. It may take some time for it to become visible in the network.");
    }
}
