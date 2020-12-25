package de.corelogics.mediaview.service.dlna.content;

import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.dlna.DlnaRequest;
import de.corelogics.mediaview.util.IdUtils;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.container.StorageFolder;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SendungAzContent extends BaseDnlaRequestHandler {
    private static final Comparator<String> ORD_ALPHA = Comparator.comparing(
            s -> s.toLowerCase(Locale.GERMANY),
            Collator.getInstance(Locale.GERMANY));
    private static final Comparator<Map.Entry<String, ?>> ORD_ALPHA_STRINGENTRY = Comparator.comparing(Map.Entry::getKey, ORD_ALPHA);
    private static final Comparator<Map.Entry<Character, ?>> ORD_ALPHA_CHARENTRY = Comparator.comparing(e -> e.getKey().toString(), ORD_ALPHA);

    private static final String URN_PREFIX_SHOW = "urn:corelogics.de:mediaview:show:";

    private static final String URN_PREFIX_SHOWGROUP = "urn:corelogics.de:mediaview:showgroup:";

    private static final String URN_PREFIX_CHANNEL = "urn:corelogics.de:mediaview:channel:";

    private static final String URN_PREFIX_SENDUNG_AZ = "urn:corelogics.de:mediaview:sendungaz";

    private final ClipRepository clipRepository;

    private final ShowContent showContent;

    public SendungAzContent(ClipRepository clipRepository, ShowContent showContent) {
        this.clipRepository = clipRepository;
        this.showContent = showContent;
    }

    public StorageFolder createLink(DlnaRequest request) {
        return new StorageFolder(
                URN_PREFIX_SENDUNG_AZ,
                request.getObjectId(), "Sendungen A-Z",
                "",
                clipRepository.findAllChannels().size(),
                null);
    }

    @Override
    public boolean canHandle(DlnaRequest request) {
        return URN_PREFIX_SENDUNG_AZ.equals(request.getObjectId()) ||
                request.getObjectId().startsWith(URN_PREFIX_CHANNEL) ||
                request.getObjectId().startsWith(URN_PREFIX_SHOWGROUP);

    }

    @Override
    protected DIDLContent respondWithException(DlnaRequest request) throws Exception {
        var didl = new DIDLContent();
        if (request.getObjectId().startsWith(URN_PREFIX_SENDUNG_AZ)) {
            clipRepository.findAllChannels().stream()
                    .map(channelName ->
                            new StorageFolder(
                                    idChannel(channelName),
                                    request.getObjectId(),
                                    channelName,
                                    "",
                                    100,
                                    null))
                    .forEach(didl::addContainer);
        } else if (request.getObjectId().startsWith(URN_PREFIX_CHANNEL)) {
            var channelId = IdUtils.decodeId(request.getObjectId().substring(URN_PREFIX_CHANNEL.length()));
            var containedIns = clipRepository.findAllContainedIns(channelId);
            if (containedIns.size() < 200) {
                containedIns.entrySet().stream()
                        .sorted(ORD_ALPHA_STRINGENTRY)
                        .map(containedIn -> showContent.createAsLink(request, channelId, containedIn.getKey(),
                                containedIn.getValue()))
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
                                request.getObjectId(),
                                letterEntry.getKey().toString(),
                                "",
                                letterEntry.getValue(),
                                null))
                        .forEach(didl::addContainer);
            }
        } else if (request.getObjectId().startsWith(URN_PREFIX_SHOWGROUP)) {
            var split = request.getObjectId().split(":");
            var channelId = IdUtils.decodeId(split[split.length - 2]);
            var startingWith = IdUtils.decodeId(split[split.length - 1]);
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
