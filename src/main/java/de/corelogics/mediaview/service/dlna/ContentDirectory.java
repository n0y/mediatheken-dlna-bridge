/*
 * MIT License
 *
 * Copyright (c) 2020 corelogics.de
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.corelogics.mediaview.service.dlna;

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.support.contentdirectory.AbstractContentDirectoryService;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryErrorCode;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.SortCriterion;

import java.util.Collection;

class ContentDirectory extends AbstractContentDirectoryService {
    private final Logger logger = LogManager.getLogger(ContentDirectory.class);

    private final Collection<DlnaRequestHandler> handlers;

    public ContentDirectory(Collection<DlnaRequestHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public BrowseResult browse(String objectID, BrowseFlag browseFlag,
                               String filter,
			long firstResult, long maxResults,
			SortCriterion[] orderby)
			throws ContentDirectoryException {
		logger.debug("Received browse request for oid={}, first={}, max={}", objectID, firstResult, maxResults);
		try {
			var request = new DlnaRequest(
					objectID, browseFlag, filter, firstResult, maxResults,
					ImmutableList.copyOf(orderby));
			return handlers.stream()
					.filter(h -> h.canHandle(request))
					.findAny()
					.map(h -> h.respond(request))
					.orElseGet(this::emptyResult);
		} catch (RuntimeException e) {
			logger.warn("Error creating a browse response", e);
			throw new ContentDirectoryException(
					ContentDirectoryErrorCode.CANNOT_PROCESS,
					e.toString());
		}
	}

	private BrowseResult emptyResult() {
		try {
			return new BrowseResult(new DIDLParser().generate(new DIDLContent()), 0, 0);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public BrowseResult search(String containerId,
			String searchCriteria, String filter,
			long firstResult, long maxResults,
			SortCriterion[] orderBy) throws ContentDirectoryException {
		// You can override this method to implement searching!
		return super.search(containerId, searchCriteria, filter, firstResult, maxResults, orderBy);
	}
}
