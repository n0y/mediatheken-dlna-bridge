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

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.service.dlna.DlnaRequest;
import lombok.AllArgsConstructor;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.Res;
import org.jupnp.support.model.item.VideoItem;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@AllArgsConstructor
public class ClipContent extends BaseDnlaRequestHandler {
    private static final String MIME_TYPE_VIDEO_MP4 = "video/mp4";
    private static final String URN_PREFIX_CLIP = "urn:corelogics.de:mediaview:clip:";

    private static final DateTimeFormatter DTF_DATE = DateTimeFormatter.ofPattern("dd.MM.").withLocale(Locale.GERMANY);
    private static final DateTimeFormatter DTF_TIME = DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.GERMANY);

    private final ClipContentUrlGenerator clipContentUrlGenerator;

    @Override
    public boolean canHandle(DlnaRequest request) {
        return false;
    }

    @Override
    protected DIDLContent respondWithException(DlnaRequest request) throws Exception {
        return new DIDLContent();
    }

    public VideoItem createLinkWithTimePrefix(DlnaRequest request, ClipEntry entry) {
        return createLink(request, entry, DTF_TIME);
    }

    public VideoItem createLinkWithDatePrefix(DlnaRequest request, ClipEntry entry) {
        return createLink(request, entry, DTF_DATE);
    }

    private VideoItem createLink(DlnaRequest request, ClipEntry entry, DateTimeFormatter dateTimeFormat) {
        return new VideoItem(
                idClip(entry),
                request.objectId(),
                dateTimeFormat.format(entry.getBroadcastedAt()) + " " + lengthLimit(entry.getTitle()),
                "",
                new Res(
                        MIME_TYPE_VIDEO_MP4,
                        entry.getSize(),
                        entry.getDuration(),
                        2000L,
                        clipContentUrlGenerator.createLinkTo(entry)));
    }

    private String idClip(ClipEntry entry) {
        return URN_PREFIX_CLIP + entry.getTitle().hashCode();
    }

    private String lengthLimit(String in) {
        if (in.length() > 80) {
            return in.substring(0, 80);
        }
        return in;
    }
}
