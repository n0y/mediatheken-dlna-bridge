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
import lombok.AllArgsConstructor;
import lombok.val;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.container.StorageFolder;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
public class SendungAzContent extends BaseDlnaRequestHandler {
    private static final Comparator<String> ORD_ALPHA = Comparator.comparing(
        s -> s.toLowerCase(Locale.GERMANY),
        Collator.getInstance(Locale.GERMANY));
    private static final Comparator<Map.Entry<String, Integer>> ORD_ALPHA_STRINGENTRY = Map.Entry.comparingByKey(ORD_ALPHA);
    private static final Comparator<Map.Entry<Character, ?>> ORD_ALPHA_CHARENTRY = Comparator.comparing(e -> e.getKey().toString(), ORD_ALPHA);

    private static final String URN_PREFIX_SHOW = "urn:corelogics.de:mediaview:show:";

    private static final String URN_PREFIX_SHOWGROUP = "urn:corelogics.de:mediaview:showgroup:";

    private static final String URN_PREFIX_CHANNEL = "urn:corelogics.de:mediaview:channel:";

    private static final String URN_PREFIX_SENDUNG_AZ = "urn:corelogics.de:mediaview:sendungaz";

    private final ClipRepository clipRepository;

    private final ShowContent showContent;

    public StorageFolder createLink(DlnaRequest request) {
        return new StorageFolder(
            URN_PREFIX_SENDUNG_AZ,
            request.objectId(), "Sendungen A-Z",
            "",
            clipRepository.findAllChannels().size(),
            null);
    }

    @Override
    public boolean canHandle(DlnaRequest request) {
        return URN_PREFIX_SENDUNG_AZ.equals(request.objectId()) ||
            request.objectId().startsWith(URN_PREFIX_CHANNEL) ||
            request.objectId().startsWith(URN_PREFIX_SHOWGROUP);

    }

    @Override
    protected DIDLContent respondWithException(DlnaRequest request) {
        val didl = new DIDLContent();
        if (request.objectId().startsWith(URN_PREFIX_SENDUNG_AZ)) {
            clipRepository.findAllChannels().stream()
                .map(channelName ->
                    new StorageFolder(
                        idChannel(channelName),
                        request.objectId(),
                        channelName,
                        "",
                        100,
                        null))
                .forEach(didl::addContainer);
        } else if (request.objectId().startsWith(URN_PREFIX_CHANNEL)) {
            val channelId = IdUtils.decodeId(request.objectId().substring(URN_PREFIX_CHANNEL.length()));
            val containedIns = clipRepository.findAllContainedIns(channelId);
            if (containedIns.size() < 200) {
                containedIns.entrySet().stream()
                    .sorted(ORD_ALPHA_STRINGENTRY)
                    .map(containedIn -> showContent.createAsLink(request, channelId, containedIn.getKey(),
                        containedIn.getValue()))
                    .forEach(didl::addContainer);
            } else {
                containedIns.entrySet().stream()
                    .map(e -> {
                        val char0 = Character.toUpperCase(e.getKey().charAt(0));
                        return Map.entry(Character.isAlphabetic(char0) ? char0 : '#', e.getValue());
                    })
                    .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Map.Entry::getValue)))
                    .entrySet().stream()
                    .sorted(ORD_ALPHA_CHARENTRY)
                    .map(letterEntry -> new StorageFolder(
                        idShowGroup(channelId, letterEntry),
                        request.objectId(),
                        letterEntry.getKey().toString(),
                        "",
                        letterEntry.getValue(),
                        null))
                    .forEach(didl::addContainer);
            }
        } else if (request.objectId().startsWith(URN_PREFIX_SHOWGROUP)) {
            val split = request.objectId().split(":");
            val channelId = IdUtils.decodeId(split[split.length - 2]);
            val startingWith = IdUtils.decodeId(split[split.length - 1]);
            clipRepository.findAllContainedIns(channelId, startingWith).entrySet().stream()
                .sorted(ORD_ALPHA_STRINGENTRY)
                .map(containedIn -> showContent.createAsLink(
                    request, channelId, containedIn.getKey(), containedIn.getValue()))
                .forEach(didl::addContainer);
        }

        return didl;
    }

    private String idChannel(String channelName) {
        return URN_PREFIX_CHANNEL + IdUtils.encodeId(channelName);
    }

    private String idShowGroup(String channelId, Map.Entry<Character, Integer> letterEntry) {
        return URN_PREFIX_SHOWGROUP + IdUtils.encodeId(channelId) + ":" + IdUtils.encodeId(letterEntry.getKey().toString());
    }
}
