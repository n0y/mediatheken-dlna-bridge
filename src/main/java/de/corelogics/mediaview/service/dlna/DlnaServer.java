
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

package de.corelogics.mediaview.service.dlna;

import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.service.base.lifecycle.ShutdownRegistry;
import de.corelogics.mediaview.service.base.networking.WebServer;
import de.corelogics.mediaview.service.dlna.jupnp.DlnaUpnpServiceConfiguration;
import de.corelogics.mediaview.service.dlna.jupnp.UpnpServiceImplFixed;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.binding.annotations.AnnotationLocalServiceBinder;
import org.jupnp.model.DefaultServiceManager;
import org.jupnp.model.ValidationException;
import org.jupnp.model.meta.*;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDN;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class DlnaServer {
    private final ShutdownRegistry shutdownRegistry;
    private final MainConfiguration mainConfiguration;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final UpnpServiceImplFixed upnpService;
    private final LocalDevice localDevice;

    public DlnaServer(MainConfiguration mainConfiguration, WebServer webServer, ShutdownRegistry shutdownRegistry, Set<DlnaRequestHandler> handlers) throws ValidationException {
        this.shutdownRegistry = shutdownRegistry;
        this.mainConfiguration = mainConfiguration;

        val type = new UDADeviceType("MediaServer", 1);
        val details = new DeviceDetails(
            mainConfiguration.displayName(),
            new ManufacturerDetails("Mediatheken DLNA Gateway"),
            new ModelDetails("Mediatheken", "v1", "v.1.0.0", "https://github.com/n0y/mediatheken-dlna-bridge"));
        val service = (LocalService<ContentDirectory>) new AnnotationLocalServiceBinder().read(ContentDirectory.class);
        service.setManager(new DefaultServiceManager<>(service, ContentDirectory.class) {
            @Override
            protected ContentDirectory createServiceInstance() {
                return new ContentDirectory(handlers);
            }
        });

        this.localDevice = new LocalDevice(
            new DeviceIdentity(new UDN(UUID.nameUUIDFromBytes(mainConfiguration.displayName().getBytes(StandardCharsets.UTF_8)))),
            type,
            details,
            service);

        this.upnpService = new UpnpServiceImplFixed(new DlnaUpnpServiceConfiguration(webServer.getServer(), mainConfiguration.publicHttpPort()));
        this.upnpService.activate(new UpnpServiceImpl.Config() {
            @Override
            public Class<UpnpServiceImpl.Config> annotationType() {
                return UpnpServiceImpl.Config.class;
            }

            @Override
            public boolean initialSearchEnabled() {
                return false;
            }
        });
    }

    public void startup() {
        if (started.compareAndSet(false, true)) {
            log.debug("Initializing DLNA server");
            this.upnpService.startup();
            this.upnpService.getRegistry().addDevice(this.localDevice);
            this.upnpService.getProtocolFactory().createSendingNotificationAlive(localDevice).run();
            this.shutdownRegistry.registerShutdown(this::shutdown);
            log.info("Successfully started DLNA server '{}'. It may take some time for it to become visible in the network.", mainConfiguration.displayName());
        } else {
            log.debug("DLNA server is already started");
        }
    }

    private void shutdown() {
        log.debug("Shutting down");
        upnpService.shutdown();
    }
}
