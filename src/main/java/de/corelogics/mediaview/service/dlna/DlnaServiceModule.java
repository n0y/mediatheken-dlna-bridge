package de.corelogics.mediaview.service.dlna;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import de.corelogics.mediaview.service.dlna.content.*;

public class DlnaServiceModule extends AbstractModule {
	public void configure() {
		var handlerBinder = Multibinder.newSetBinder(binder(), DlnaRequestHandler.class);
		handlerBinder.addBinding().to(ClipContent.class);
		handlerBinder.addBinding().to(MissedShowsContent.class);
		handlerBinder.addBinding().to(RootContent.class);
		handlerBinder.addBinding().to(SendungAzContent.class);
		handlerBinder.addBinding().to(ShowContent.class);

		bind(DlnaServer.class).asEagerSingleton();
	}
}
