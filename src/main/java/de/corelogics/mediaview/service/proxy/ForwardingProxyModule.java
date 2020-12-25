package de.corelogics.mediaview.service.proxy;

import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.service.proxy.downloader.CacheDirectory;
import de.corelogics.mediaview.service.proxy.downloader.DownloadManager;

public class ForwardingProxyModule {
    private final MainConfiguration mainConfiguration;
    private final ClipRepository clipRepository;

    public ForwardingProxyModule(MainConfiguration mainConfiguration, ClipRepository clipRepository) {
        this.mainConfiguration = mainConfiguration;
        this.clipRepository = clipRepository;
    }

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
