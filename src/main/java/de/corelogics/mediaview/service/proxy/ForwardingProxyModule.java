/*
 * MIT License
 *
 * Copyright (c) 2020-2023 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.service.proxy;

import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.service.proxy.downloader.CacheDirectory;
import de.corelogics.mediaview.service.proxy.downloader.DownloadManager;
import lombok.RequiredArgsConstructor;
import org.eclipse.jetty.server.Server;

@RequiredArgsConstructor
public class ForwardingProxyModule {
    private final MainConfiguration mainConfiguration;
    private final Server jettyServer;
    private final ClipRepository clipRepository;

    public ClipContentUrlGenerator buildClipContentUrlGenerator() {
        if (mainConfiguration.isPrefetchingEnabled()) {
            return buildForwardingProxyServer();
        } else {
            return new DirectDownloadClipContentUrlGenerator();
        }
    }

    private ClipContentUrlGenerator buildForwardingProxyServer() {
        return new ForwardingProxyServer(
            this.mainConfiguration,
            this.jettyServer,
            this.clipRepository,
            buildDownloadManager());
    }

    private DownloadManager buildDownloadManager() {
        return new DownloadManager(this.mainConfiguration, buildCacheDirectory());
    }

    private CacheDirectory buildCacheDirectory() {
        return new CacheDirectory(this.mainConfiguration);
    }
}
