package de.corelogics.mediaview.service.proxy;

import com.google.inject.AbstractModule;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;

public class ForwardingProxyModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ClipContentUrlGenerator.class).to(ForwardingProxyServer.class).asEagerSingleton();
    }
}
