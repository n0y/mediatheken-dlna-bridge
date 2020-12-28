package de.corelogics.mediaview.service.dlna.content;

import de.corelogics.mediaview.config.FavouriteShow;
import de.corelogics.mediaview.config.FavouriteVisitor;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.service.dlna.DlnaRequest;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.container.StorageFolder;

public class RootContent extends BaseDnlaRequestHandler {
    private final MainConfiguration mainConfiguration;
    private final SendungAzContent sendungAzContent;
    private final ShowContent showContent;
    private final MissedShowsContent missedShowsContent;

    public RootContent(
            MainConfiguration mainConfiguration,
            SendungAzContent sendungAzContent,
            ShowContent showContent,
            MissedShowsContent missedShowsContent) {
        this.mainConfiguration = mainConfiguration;
        this.sendungAzContent = sendungAzContent;
        this.showContent = showContent;
        this.missedShowsContent = missedShowsContent;
    }

    @Override
    public boolean canHandle(DlnaRequest request) {
        return "0".equals(request.getObjectId());
    }

    @Override
    protected DIDLContent respondWithException(DlnaRequest request) {
        var didl = new DIDLContent();
        addFavorites(request, didl);
        didl.addContainer(sendungAzContent.createLink(request));
        didl.addContainer(missedShowsContent.createLink(request));
        return didl;
    }

    private void addFavorites(DlnaRequest request, DIDLContent didl) {
        mainConfiguration.getFavourites().stream().map(s -> s.accept(new FavouriteVisitor<StorageFolder>() {
            @Override
            public StorageFolder visitShow(FavouriteShow favouriteShow) {
                return showContent.createAsLink(request, favouriteShow.getChannel(), favouriteShow.getTitle());
            }
        })).forEach(didl::addObject);
    }
}
