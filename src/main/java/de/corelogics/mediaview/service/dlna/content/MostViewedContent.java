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

package de.corelogics.mediaview.service.dlna.content;

import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.repository.tracked.TrackedContainedIn;
import de.corelogics.mediaview.repository.tracked.TrackedViewRepository;
import de.corelogics.mediaview.service.dlna.DlnaRequest;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.container.StorageFolder;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
public class MostViewedContent extends BaseDlnaRequestHandler {
    private final TrackedViewRepository trackedViewRepository;
    private final ClipRepository clipRepository;
    private final ShowContent showContent;

    private static final String URN_PREFIX_MOST_VIEWED = "urn:corelogics.de:mediaview:mostviewed";

    public StorageFolder createLink(DlnaRequest request) {
        return new StorageFolder(
            URN_PREFIX_MOST_VIEWED,
            request.objectId(),
            "Meistgesehen",
            "",
            queryRecentlySeen()
                .size(),
            null);
    }

    @Override
    public boolean canHandle(DlnaRequest request) {
        return URN_PREFIX_MOST_VIEWED.equals(request.objectId());
    }

    @Override
    protected DIDLContent respondWithException(DlnaRequest request) {
        val didl = new DIDLContent();
        queryRecentlySeen()
            .stream()
            .sorted(Comparator.comparing(TrackedContainedIn::numberViewed).reversed())
            .map(tci -> showContent.createAsLinkWithName(
                STR."\{tci.channelName()}: \{tci.containedIn()}",
                request,
                tci.channelName(),
                tci.containedIn(),
                clipRepository.findAllClips(tci.channelName(), tci.containedIn()).size()))
            .forEach(didl::addContainer);
        return didl;
    }

    private List<TrackedContainedIn> queryRecentlySeen() {
        return trackedViewRepository.getRecentlySeenContainedIns(
            ZonedDateTime.now().minusMonths(1).truncatedTo(ChronoUnit.DAYS),
            ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS));
    }
}
