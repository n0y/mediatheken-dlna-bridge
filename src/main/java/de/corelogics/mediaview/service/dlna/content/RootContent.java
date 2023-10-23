/*
 * MIT License
 *
 * Copyright (c) 2020-2023 Mediatheken DLNA Bridge Authors.
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

import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.service.dlna.DlnaRequest;
import lombok.AllArgsConstructor;
import lombok.val;
import org.jupnp.support.model.DIDLContent;

@AllArgsConstructor
public class RootContent extends BaseDlnaRequestHandler {
    private final MainConfiguration mainConfiguration;
    private final SendungAzContent sendungAzContent;
    private final ShowContent showContent;
    private final MissedShowsContent missedShowsContent;

    @Override
    public boolean canHandle(DlnaRequest request) {
        return "0".equals(request.objectId());
    }

    @Override
    protected DIDLContent respondWithException(DlnaRequest request) {
        val didl = new DIDLContent();
        addFavorites(request, didl);
        didl.addContainer(sendungAzContent.createLink(request));
        didl.addContainer(missedShowsContent.createLink(request));
        return didl;
    }

    private void addFavorites(DlnaRequest request, DIDLContent didl) {
        mainConfiguration.getFavourites().stream()
            .map(s -> s.accept(
                favouriteShow ->
                    showContent.createAsLink(request, favouriteShow.channel(), favouriteShow.title())))
            .forEach(didl::addObject);
    }
}
