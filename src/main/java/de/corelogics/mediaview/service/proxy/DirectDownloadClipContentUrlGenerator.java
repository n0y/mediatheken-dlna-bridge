package de.corelogics.mediaview.service.proxy;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;

public class DirectDownloadClipContentUrlGenerator implements ClipContentUrlGenerator {
    @Override
    public String createLinkTo(ClipEntry e) {
        return e.getBestUrl();
    }
}
