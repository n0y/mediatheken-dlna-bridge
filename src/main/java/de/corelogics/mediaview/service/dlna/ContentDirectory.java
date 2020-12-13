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

import com.google.common.collect.Ordering;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.support.contentdirectory.AbstractContentDirectoryService;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryErrorCode;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.*;
import org.fourthline.cling.support.model.container.StorageFolder;
import org.fourthline.cling.support.model.item.VideoItem;
import org.seamless.util.MimeType;

import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

class ContentDirectory extends AbstractContentDirectoryService {
    private static final MimeType MIME_TYPE_VIDEO_MP4 = new MimeType("video", "mp4");
    private static final Ordering<String> ORD_ALPHA = Ordering.from(
            Collator.getInstance(Locale.GERMANY)).onResultOf(s -> s.toLowerCase(Locale.GERMANY));
    private static final Ordering<Map.Entry<String, ?>> ORD_ALPHA_STRINGENTRY = ORD_ALPHA.onResultOf(Map.Entry::getKey);
    private static final Ordering<Map.Entry<Character, ?>> ORD_ALPHA_CHARENTRY = ORD_ALPHA.onResultOf(e -> e.getKey().toString());
    private final Ordering<ClipEntry> ORD_ALPHA_CLIP = ORD_ALPHA.onResultOf(ClipEntry::getTitle);

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.");

    private static final String URN_PREFIX_SHOW = "urn:corelogics.de:mediaview:show:";
    private static final String URN_PREFIX_CLIP = "urn:corelogics.de:mediaview:clip:";
    private static final String URN_PREFIX_SHOWGROUP = "urn:corelogics.de:mediaview:showgroup:";
    private static final String URN_PREFIX_CHANNEL = "urn:corelogics.de:mediaview:channel:";
    private static final String URN_PREFIX_SENDUNG_AZ = "urn:corelogics.de:mediaview:sendungaz";

    private final Logger logger = LogManager.getLogger();

    private final ClipRepository clipRepository;

    public ContentDirectory(ClipRepository clipRepository) {
        this.clipRepository = clipRepository;
    }

    @Override
    public BrowseResult browse(String objectID, BrowseFlag browseFlag,
                               String filter,
                               long firstResult, long maxResults,
                               SortCriterion[] orderby)
            throws ContentDirectoryException {
        logger.debug("Received browse request for oid={}, first={}, max={}", objectID, firstResult, maxResults);
        try {
            var didl = new DIDLContent();
            if ("0".equals(objectID)) {
                addFavorites(objectID, didl);
                didl.addContainer(new StorageFolder(
                        URN_PREFIX_SENDUNG_AZ,
                        objectID, "Sendungen A-Z",
                        "",
                        clipRepository.findAllChannels().size(),
                        null));
            } else if (objectID.startsWith(URN_PREFIX_SENDUNG_AZ)) {
                clipRepository.findAllChannels().stream()
                        .map(channelName ->
                                new StorageFolder(
                                        idChannel(channelName),
                                        objectID,
                                        channelName,
                                        "",
                                        100,
                                        null))
                        .forEach(didl::addContainer);
            } else if (objectID.startsWith(URN_PREFIX_CHANNEL)) {
                var channelId = decodeB64(objectID.substring(URN_PREFIX_CHANNEL.length()));
                var containedIns = clipRepository.findAllContainedIns(channelId);
                if (containedIns.size() < 200) {
                    containedIns.entrySet().stream()
                            .sorted(ORD_ALPHA_STRINGENTRY)
                            .map(containedIn -> containedInToStorageFolder(objectID, channelId, containedIn.getKey(), containedIn.getValue()))
                            .forEach(didl::addContainer);
                } else {
                    containedIns.entrySet().stream()
                            .map(e -> {
                                var char0 = Character.toUpperCase(e.getKey().charAt(0));
                                return Map.entry(Character.isAlphabetic(char0) ? char0 : '#', e.getValue());
                            })
                            .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Map.Entry::getValue)))
                            .entrySet().stream()
                            .sorted(ORD_ALPHA_CHARENTRY)
                            .map(letterEntry -> new StorageFolder(
                                    idShowGroup(channelId, letterEntry),
                                    objectID,
                                    letterEntry.getKey().toString(),
                                    "",
                                    letterEntry.getValue(),
                                    null))
                            .forEach(didl::addContainer);
                }
            } else if (objectID.startsWith(URN_PREFIX_SHOWGROUP)) {
                var split = objectID.split(":");
                var channelId = decodeB64(split[split.length - 2]);
                var startingWith = decodeB64(split[split.length - 1]);
                clipRepository.findAllContainedIns(channelId, startingWith).entrySet().stream()
                        .sorted(ORD_ALPHA_STRINGENTRY)
                        .map(containedIn -> containedInToStorageFolder(objectID, channelId, containedIn.getKey(), containedIn.getValue()))
                        .forEach(didl::addContainer);
            } else if (objectID.startsWith(URN_PREFIX_SHOW)) {
                var split = objectID.split(":");
                var channelId = decodeB64(split[split.length - 2]);
                var containedIn = decodeB64(split[split.length - 1]);
                clipRepository.findAllClips(channelId, containedIn).stream().map(entry -> new VideoItem(
                        idClip(entry),
                        objectID,
                        DATE_TIME_FORMAT.format(entry.getBroadcastedAt()) + " " + lengthLimit(entry.getTitle()),
                        "",
                        new Res(MIME_TYPE_VIDEO_MP4, entry.getSize(), entry.getDuration(), 2000L, ofNullable(entry.getUrlHd()).orElse(entry.getUrl()))))
                        .forEach(didl::addItem);
            }

            var totalNumResults = didl.getCount();
            didl.setContainers(didl.getContainers().stream().skip(firstResult).limit(maxResults).collect(Collectors.toList()));
            didl.setItems(didl.getItems().stream().skip(firstResult).limit(maxResults).collect(Collectors.toList()));

            return new BrowseResult(new DIDLParser().generate(didl), didl.getCount(), totalNumResults);
        } catch (Exception e) {
            logger.warn("Error creating a browse response", e);
            throw new ContentDirectoryException(
                    ContentDirectoryErrorCode.CANNOT_PROCESS,
                    e.toString());
        }
    }

    private void addFavorites(String parentId, DIDLContent didl) {
        didl.addContainer(containedInToStorageFolder(parentId, "ARD", "Rote Rosen", clipRepository.findAllClips("ARD", "Rote Rosen").size()));
        didl.addContainer(containedInToStorageFolder(parentId, "ARD", "Tagesschau", clipRepository.findAllClips("ARD", "Tagesschau").size()));
        didl.addContainer(containedInToStorageFolder(parentId, "ARD", "Die Sendung mit der Maus", clipRepository.findAllClips("ARD", "Die Sendung mit der Maus").size()));
    }

    private StorageFolder containedInToStorageFolder(String objectID, String channelId, String containedIn, int numberOfElements) {
        return new StorageFolder(
                idShow(channelId, containedIn),
                objectID,
                containedIn,
                "",
                numberOfElements,
                null);
    }


    private String idChannel(String channelName) {
        return URN_PREFIX_CHANNEL + encodeB64(channelName);
    }

    private String idShow(String channelId, String containedIn) {
        return URN_PREFIX_SHOW + encodeB64(channelId) + ":" + encodeB64(containedIn);
    }

    private String idShowGroup(String channelId, Map.Entry<Character, Integer> letterEntry) {
        return URN_PREFIX_SHOWGROUP + encodeB64(channelId) + ":" + encodeB64(letterEntry.getKey().toString());
    }

    private String idClip(ClipEntry entry) {
        return URN_PREFIX_CLIP + entry.getTitle().hashCode();
    }

    private String encodeB64(String in) {
        return Base64.getEncoder().withoutPadding().encodeToString(in.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeB64(String in) {
        return new String(Base64.getDecoder().decode(in), StandardCharsets.UTF_8);
    }

    private String lengthLimit(String in) {
        if (in.length() > 80) {
            return in.substring(0, 80);
        }
        return in;
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
