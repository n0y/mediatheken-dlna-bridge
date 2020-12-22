package de.corelogics.mediaview.service.proxy;

import com.google.inject.AbstractModule;
import com.netflix.governator.configuration.ConfigurationKey;
import com.netflix.governator.configuration.ConfigurationProvider;
import com.netflix.governator.configuration.KeyParser;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;

import java.util.Map;

public class ForwardingProxyModule extends AbstractModule {
    private final ConfigurationProvider configurationProvider;

    public ForwardingProxyModule(ConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    @Override
    protected void configure() {
        if (configurationProvider.getBooleanSupplier(new ConfigurationKey("ENABLE_PREFETCHING", KeyParser.parse("ENABLE_PREFETCHING", Map.of())), false).get()) {
            bind(ClipContentUrlGenerator.class).to(ForwardingProxyServer.class).asEagerSingleton();
        } else {
            bind(ClipContentUrlGenerator.class).to(DirectDownloadClipContentUrlGenerator.class);
        }
    }
}
