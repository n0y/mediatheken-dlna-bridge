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

package de.corelogics.mediaview;

import de.corelogics.mediaview.client.mediatheklist.MediathekListClient;
import de.corelogics.mediaview.client.mediathekview.MediathekViewImporter;
import de.corelogics.mediaview.config.ConfigurationModule;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.dlna.DlnaServer;
import de.corelogics.mediaview.service.dlna.DlnaServiceModule;
import de.corelogics.mediaview.service.importer.ImporterService;
import de.corelogics.mediaview.service.networking.NetworkingModule;
import de.corelogics.mediaview.service.proxy.ForwardingProxyModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.http.HttpClient;

public class Main {
    private final Logger logger = LogManager.getLogger(Main.class);

    private final ImporterService importerService;
    private final DlnaServer dlnaServer;
    private final ClipRepository clipRepository;

    public Main() throws IOException {
        var configModule = new ConfigurationModule();
        var mainConfiguration = configModule.getMainConfiguration();
        var networkModule = new NetworkingModule(mainConfiguration);

        this.clipRepository = new ClipRepository(mainConfiguration);
        this.dlnaServer =
                new DlnaServiceModule(
                        mainConfiguration,
                        networkModule.getJettyServer(),
                        new ForwardingProxyModule(mainConfiguration, networkModule.getJettyServer(), clipRepository)
                                .buildClipContentUrlGenerator(),
                        clipRepository)
                        .getDlnaServer();
        networkModule.startup();
        this.importerService = new ImporterService(
                mainConfiguration,
                new MediathekListClient(mainConfiguration, HttpClient.newBuilder().build()),
                new MediathekViewImporter(),
                clipRepository);
        this.importerService.scheduleImport();
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        new Main().listenForShutdown();
    }

    private void listenForShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    private void shutdown() {
        logger.info("Shutting down");
        importerService.shutdown();
        dlnaServer.shutdown();
        clipRepository.shutdown();
    }
}
