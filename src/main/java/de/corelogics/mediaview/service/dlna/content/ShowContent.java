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

import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.dlna.DlnaRequest;
import de.corelogics.mediaview.util.IdUtils;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.container.StorageFolder;

public class ShowContent extends BaseDnlaRequestHandler {
    private static final String URN_PREFIX_SHOW = "urn:corelogics.de:mediaview:show:";

    private final ClipContent clipContent;

    private final ClipRepository clipRepository;

    public ShowContent(ClipContent clipContent, ClipRepository clipRepository) {
        this.clipContent = clipContent;
        this.clipRepository = clipRepository;
    }

    @Override
    public boolean canHandle(DlnaRequest request) {
        return request.getObjectId().startsWith(URN_PREFIX_SHOW);
    }

    public StorageFolder createAsLink(DlnaRequest request, String channelId, String containedIn) {
        return this.createAsLink(request, channelId, containedIn,
                clipRepository.findAllClips(channelId, containedIn).size());
    }

    public StorageFolder createAsLink(DlnaRequest request, String channelId, String containedIn, int numberOfElements) {
        return new StorageFolder(
                idShow(channelId, containedIn),
                request.getObjectId(),
                containedIn,
                "",
                numberOfElements,
                null);
    }

    @Override
    protected DIDLContent respondWithException(DlnaRequest request) throws Exception {
        var didl = new DIDLContent();
        if (request.getObjectId().startsWith(URN_PREFIX_SHOW)) {
            var split = request.getObjectId().split(":");
            var channelId = IdUtils.decodeId(split[split.length - 2]);
            var containedIn = IdUtils.decodeId(split[split.length - 1]);
            clipRepository.findAllClips(channelId, containedIn).stream()
                    .map(e -> clipContent.createLinkWithDatePrefix(request, e))
                    .forEach(didl::addItem);
        }
        return didl;
    }

    private String idShow(String channelId, String containedIn) {
        return URN_PREFIX_SHOW + IdUtils.encodeId(channelId) + ":" + IdUtils.encodeId(containedIn);
    }
}
