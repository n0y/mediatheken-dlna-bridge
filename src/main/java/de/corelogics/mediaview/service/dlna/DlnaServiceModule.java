package de.corelogics.mediaview.service.dlna;

import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.service.dlna.content.*;
import org.fourthline.cling.model.ValidationException;

import java.util.Set;

public class DlnaServiceModule {
    private final MainConfiguration mainConfiguration;
    private final ClipContentUrlGenerator clipContentUrlGenerator;
    private final ClipRepository clipRepository;

    public DlnaServiceModule(MainConfiguration mainConfiguration, ClipContentUrlGenerator clipContentUrlGenerator, ClipRepository clipRepository) {
        this.mainConfiguration = mainConfiguration;
        this.clipContentUrlGenerator = clipContentUrlGenerator;
        this.clipRepository = clipRepository;
    }

    public DlnaServer buildServer() {
        try {
            return new DlnaServer(mainConfiguration, buildRequestHandlers());
        } catch (ValidationException e) {
            throw new RuntimeException("Initialization failed", e);
        }
    }

    private Set<DlnaRequestHandler> buildRequestHandlers() {
        var clipContent = new ClipContent(this.clipContentUrlGenerator);
        var showContent = new ShowContent(clipContent, this.clipRepository);
        var sendungAzContent = new SendungAzContent(this.clipRepository, showContent);
        var missedShowsContent = new MissedShowsContent(clipContent, this.clipRepository);
        var rootContent = new RootContent(mainConfiguration, sendungAzContent, showContent, missedShowsContent);
        return Set.of(clipContent, missedShowsContent, sendungAzContent, rootContent, showContent);
    }
}
