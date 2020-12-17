package de.corelogics.mediaview.service.dlna.content;

import de.corelogics.mediaview.service.dlna.DlnaRequest;
import de.corelogics.mediaview.service.dlna.DlnaRequestHandler;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

	protected String encodeB64(String in) {
		return Base64.getEncoder().withoutPadding().encodeToString(in.getBytes(StandardCharsets.UTF_8));
	}

	protected String decodeB64(String in) {
		return new String(Base64.getDecoder().decode(in), StandardCharsets.UTF_8);
	}
}
