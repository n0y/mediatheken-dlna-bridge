/*
 * MIT License
 *
 * Copyright (c) 2020-2024 Mediatheken DLNA Bridge Authors.
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

import de.corelogics.mediaview.service.dlna.jupnp.LocalAddressHolder;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.logging.log4j.CloseableThreadContext;
import org.jupnp.support.contentdirectory.AbstractContentDirectoryService;
import org.jupnp.support.contentdirectory.ContentDirectoryErrorCode;
import org.jupnp.support.contentdirectory.ContentDirectoryException;
import org.jupnp.support.contentdirectory.DIDLParser;
import org.jupnp.support.model.BrowseFlag;
import org.jupnp.support.model.BrowseResult;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.SortCriterion;

import java.util.Arrays;
import java.util.Collection;

@AllArgsConstructor
@Log4j2
class ContentDirectory extends AbstractContentDirectoryService {
    private final Collection<DlnaRequestHandler> handlers;

    @Override
    public BrowseResult browse(
        String objectID,
        BrowseFlag browseFlag,
        String filter,
        long firstResult, long maxResults,
        SortCriterion[] orderBy)
        throws ContentDirectoryException {
        log.debug("Received browse request for oid={}, first={}, max={}", objectID, firstResult, maxResults);
        try {
            val request = new DlnaRequest(
                objectID,
                browseFlag,
                filter,
                firstResult,
                maxResults,
                Arrays.asList(orderBy),
                LocalAddressHolder.getMemoizedLocalAddress());
            try (val ignored = CloseableThreadContext.put("REQUEST", request.toString())) {
                return handlers.stream()
                    .filter(h -> h.canHandle(request))
                    .findAny()
                    .map(h -> h.respond(request))
                    .orElseGet(this::emptyResult);
            }
        } catch (RuntimeException e) {
            log.warn("Error creating a browse response", e);
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
}
