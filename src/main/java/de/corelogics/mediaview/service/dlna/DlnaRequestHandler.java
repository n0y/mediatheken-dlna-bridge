package de.corelogics.mediaview.service.dlna;

import org.fourthline.cling.support.model.BrowseResult;

public interface DlnaRequestHandler {
	boolean canHandle(DlnaRequest request);

	BrowseResult respond(DlnaRequest request);
}
