package de.corelogics.mediaview.service.dlna.content;

import de.corelogics.mediaview.service.dlna.DlnaRequest;
import de.corelogics.mediaview.service.dlna.DlnaRequestHandler;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;

import java.util.stream.Collectors;

abstract class BaseDnlaRequestHandler implements DlnaRequestHandler {
	public BrowseResult respond(DlnaRequest request) {
		try {
			var didl = respondWithException(request);
			var totalNumResults = didl.getCount();
			didl.setContainers(
					didl.getContainers().stream().skip(request.getFirstResult()).limit(request.getMaxResults()).collect(Collectors.toList()));
			didl.setItems(didl.getItems().stream().skip(request.getFirstResult()).limit(request.getMaxResults()).collect(Collectors.toList()));
			return new BrowseResult(new DIDLParser().generate(didl), didl.getCount(), totalNumResults);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected abstract DIDLContent respondWithException(DlnaRequest request) throws Exception;
}
