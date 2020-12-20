package de.corelogics.mediaview.service;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;

public interface ClipContentUrlGenerator {
    String createLinkTo(ClipEntry e);
}
