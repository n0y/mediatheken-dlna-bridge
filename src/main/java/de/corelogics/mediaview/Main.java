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

package de.corelogics.mediaview;

import de.corelogics.mediaview.config.ConfigurationModule;
import de.corelogics.mediaview.service.base.BaseServicesModule;
import de.corelogics.mediaview.service.base.lifecycle.ShutdownRegistry;
import de.corelogics.mediaview.service.dlna.DlnaServiceModule;
import de.corelogics.mediaview.service.importer.ImporterModule;
import de.corelogics.mediaview.service.playback.PlaybackModule;
import de.corelogics.mediaview.service.repository.RepositoryModule;
import lombok.val;

import java.io.IOException;

public class Main {
    private final ShutdownRegistry shutdownRegistry;

    public Main() throws IOException {
        val configModule = new ConfigurationModule();
        val baseServicesModule = new BaseServicesModule(configModule.getMainConfiguration());
        val repositoryModule = new RepositoryModule(baseServicesModule);
        val playbackModule = new PlaybackModule(configModule.getMainConfiguration(), baseServicesModule, repositoryModule);
        val dlnaServerModule = new DlnaServiceModule(configModule.getMainConfiguration(), baseServicesModule, playbackModule, repositoryModule);
        val importerModule = new ImporterModule(configModule.getMainConfiguration(), baseServicesModule, repositoryModule);

        dlnaServerModule.getDlnaServer().startup();
        baseServicesModule.getNetworkingModule().getWebserver().startup();
        importerModule.getImporterService().scheduleImport();

        this.shutdownRegistry = baseServicesModule.getShutdownRegistry();
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        new Main().listenForShutdown();
    }

    private void listenForShutdown() {
        var shutdownThread = new Thread(this.shutdownRegistry::shutdown);
        shutdownThread.setName("shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownThread);
    }
}
