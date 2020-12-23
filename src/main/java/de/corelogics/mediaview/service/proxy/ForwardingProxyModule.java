package de.corelogics.mediaview.service.proxy;

import com.google.inject.AbstractModule;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;

public class ForwardingProxyModule extends AbstractModule {
    private final MainConfiguration mainConfiguration;

    public ForwardingProxyModule(MainConfiguration mainConfiguration) {
        this.mainConfiguration = mainConfiguration;
    }

    @Override
    protected void configure() {
        if (mainConfiguration.isPrefetchingEnabled()) {
            bind(ClipContentUrlGenerator.class).to(ForwardingProxyServer.class).asEagerSingleton();
        } else {
            bind(ClipContentUrlGenerator.class).to(DirectDownloadClipContentUrlGenerator.class);
        }
    }
}
