/*
 * MIT License
 *
 * Copyright (c) 2020-2021 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.service.dlna.content;

import de.corelogics.mediaview.service.dlna.DlnaRequest;
import de.corelogics.mediaview.service.dlna.DlnaRequestHandler;
import org.jupnp.support.contentdirectory.DIDLParser;
import org.jupnp.support.model.BrowseResult;
import org.jupnp.support.model.DIDLContent;

import java.util.stream.Collectors;

abstract class BaseDnlaRequestHandler implements DlnaRequestHandler {
    public BrowseResult respond(DlnaRequest request) {
        try {
            var didl = respondWithException(request);
            var totalNumResults = didl.getCount();
            didl.setContainers(
                    didl.getContainers().stream().skip(request.firstResult()).limit(request.maxResults()).collect(Collectors.toList()));
            didl.setItems(didl.getItems().stream().skip(request.firstResult()).limit(request.maxResults()).collect(Collectors.toList()));
            return new BrowseResult(new DIDLParser().generate(didl), didl.getCount(), totalNumResults);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract DIDLContent respondWithException(DlnaRequest request) throws Exception;
}
