package de.corelogics.mediaview.service.dlna.content;

import de.corelogics.mediaview.service.dlna.DlnaRequest;
import org.fourthline.cling.support.model.DIDLContent;

public class RootContent extends BaseDnlaRequestHandler {
    private final SendungAzContent sendungAzContent;

    private final ShowContent showContent;

    private final MissedShowsContent missedShowsContent;

    public RootContent(
            SendungAzContent sendungAzContent,
            ShowContent showContent,
            MissedShowsContent missedShowsContent) {
        this.sendungAzContent = sendungAzContent;
        this.showContent = showContent;
        this.missedShowsContent = missedShowsContent;
    }

    @Override
    public boolean canHandle(DlnaRequest request) {
        return "0".equals(request.getObjectId());
    }

    @Override
    protected DIDLContent respondWithException(DlnaRequest request) throws Exception {
        var didl = new DIDLContent();
        addFavorites(request, didl);
        didl.addContainer(sendungAzContent.createLink(request));
        didl.addContainer(missedShowsContent.createLink(request));
        return didl;
    }

    private void addFavorites(DlnaRequest request, DIDLContent didl) {
        didl.addContainer(showContent.createAsLink(request, "ARD", "Rote Rosen"));
        didl.addContainer(showContent.createAsLink(request, "ARD", "Tagesschau"));
        didl.addContainer(showContent.createAsLink(request, "ARD", "Die Sendung mit der Maus"));
    }
}
