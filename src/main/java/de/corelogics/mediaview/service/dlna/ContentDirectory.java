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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class ContentDirectory extends AbstractContentDirectoryService {
    private final Logger logger = LogManager.getLogger();

    private final ClipRepository clipRepository;
    private final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.");

    public ContentDirectory(ClipRepository clipRepository) {
        this.clipRepository = clipRepository;
    }

    @Override
    public BrowseResult browse(String objectID, BrowseFlag browseFlag,
                               String filter,
                               long firstResult, long maxResults,
                               SortCriterion[] orderby)
            throws ContentDirectoryException {
        try {
            var didl = new DIDLContent();
            if ("0".equals(objectID)) {
                addFavorites(objectID, didl);
                didl.addContainer(new StorageFolder(
                        "urn:corelogics.de:mediaview:sendungaz",
                        objectID, "Sendungen A-Z",
                        "",
                        100,
                        null));
            } else if (objectID.startsWith("urn:corelogics.de:mediaview:sendungaz")) {
                clipRepository.findAllChannels().stream().map(channelName ->
                        new StorageFolder(
                                "urn:corelogics.de:mediaview:channel:" + encodeB64(channelName),
                                objectID,
                                channelName,
                                "",
                                100,
                                null))
                        .forEach(didl::addContainer);
            } else if (objectID.startsWith("urn:corelogics.de:mediaview:channel:")) {
                var channelId = decodeB64(objectID.substring("urn:corelogics.de:mediaview:channel:".length()));
                final List<String> containedIns = clipRepository.findAllContainedIns(channelId);
                if (containedIns.size() < 200) {
                    containedIns.stream().map(containedIn ->
                            containedInToStorageFolder(objectID, channelId, containedIn))
                            .forEach(didl::addContainer);
                } else {
                    var collator = Collator.getInstance(Locale.GERMAN);
                    containedIns.stream().collect(Collectors.groupingBy(c -> Character.isAlphabetic(c.charAt(0)) ? Character.toString(Character.toUpperCase(c.charAt(0))) : "#"))
                            .entrySet().stream().sorted((a, b) -> collator.compare(a.getKey(), b.getKey())).map(e -> new StorageFolder(
                            "urn:corelogics.de:mediaview:showgroup:" + encodeB64(channelId) + ":" + encodeB64(e.getKey().toString()),
                            objectID,
                            e.getKey().toString(),
                            "",
                            e.getValue().size(),
                            null))
                            .forEach(didl::addContainer);
                }
            } else if (objectID.startsWith("urn:corelogics.de:mediaview:showgroup:")) {
                var split = objectID.split(":");
                var channelId = decodeB64(split[split.length - 2]);
                var startingWith = decodeB64(split[split.length - 1]);
                clipRepository.findAllContainedIns(channelId, startingWith).stream()
                        .map(containedIn -> containedInToStorageFolder(objectID, channelId, containedIn))
                        .forEach(didl::addContainer);
            } else if (objectID.startsWith("urn:corelogics.de:mediaview:show:")) {
                var split = objectID.split(":");
                var channelId = decodeB64(split[split.length - 2]);
                var containedIn = decodeB64(split[split.length - 1]);
                clipRepository.listClips(channelId, containedIn).stream().map(entry -> new VideoItem(
                        "urn:corelogics.de:mediaview:clip:" + entry.getTitle().hashCode(),
                        objectID,
                        dateTimeFormat.format(entry.getBroadcastedAt()) + " " + lengthLimit(entry.getTitle()),
                        "",
                        new Res(new MimeType("video", "mp4"), entry.getSize(), entry.getDuration(), 2000L, ofNullable(entry.getUrlHd()).orElse(entry.getUrl()))))
                        .forEach(didl::addItem);
            }

            return new BrowseResult(new DIDLParser().generate(didl), didl.getCount(), didl.getCount());
        } catch (Exception e) {
            logger.warn("Error creating a browse response", e);
            throw new ContentDirectoryException(
                    ContentDirectoryErrorCode.CANNOT_PROCESS,
                    e.toString());
        }
    }

    private void addFavorites(String parentId, DIDLContent didl) {
        didl.addContainer(containedInToStorageFolder(parentId, "ARD", "Rote Rosen"));
        didl.addContainer(containedInToStorageFolder(parentId, "ARD", "Tagesschau"));
        didl.addContainer(containedInToStorageFolder(parentId, "ARD", "Die Sendung mit der Maus"));
    }

    private StorageFolder containedInToStorageFolder(String objectID, String channelId, String containedIn) {
        return new StorageFolder(
                "urn:corelogics.de:mediaview:show:" + encodeB64(channelId) + ":" + encodeB64(containedIn),
                objectID,
                containedIn,
                "",
                100,
                null);
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
