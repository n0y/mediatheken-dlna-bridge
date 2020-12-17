package de.corelogics.mediaview.service.dlna.content;

import com.google.common.collect.Ordering;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.dlna.DlnaRequest;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.container.StorageFolder;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.text.Collator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class SendungAzContent extends BaseDnlaRequestHandler {
	private static final Ordering<String> ORD_ALPHA = Ordering.from(
			Collator.getInstance(Locale.GERMANY)).onResultOf(s -> s.toLowerCase(Locale.GERMANY));

	private static final Ordering<Map.Entry<String, ?>> ORD_ALPHA_STRINGENTRY = ORD_ALPHA.onResultOf(Map.Entry::getKey);

	private static final Ordering<Map.Entry<Character, ?>> ORD_ALPHA_CHARENTRY = ORD_ALPHA.onResultOf(e -> e.getKey().toString());

	private static final String URN_PREFIX_SHOW = "urn:corelogics.de:mediaview:show:";

	private static final String URN_PREFIX_SHOWGROUP = "urn:corelogics.de:mediaview:showgroup:";

	private static final String URN_PREFIX_CHANNEL = "urn:corelogics.de:mediaview:channel:";

	private static final String URN_PREFIX_SENDUNG_AZ = "urn:corelogics.de:mediaview:sendungaz";

	private final ClipRepository clipRepository;

	private final ShowContent showContent;

	@Inject
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
			var channelId = decodeB64(request.getObjectId().substring(URN_PREFIX_CHANNEL.length()));
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
			var channelId = decodeB64(split[split.length - 2]);
			var startingWith = decodeB64(split[split.length - 1]);
			clipRepository.findAllContainedIns(channelId, startingWith).entrySet().stream()
					.sorted(ORD_ALPHA_STRINGENTRY)
					.map(containedIn -> showContent.createAsLink(
							request, channelId, containedIn.getKey(), containedIn.getValue()))
					.forEach(didl::addContainer);
		}

		return didl;
	}

	private String idChannel(String channelName) {
		return URN_PREFIX_CHANNEL + encodeB64(channelName);
	}

	private String idShowGroup(String channelId, Map.Entry<Character, Integer> letterEntry) {
		return URN_PREFIX_SHOWGROUP + encodeB64(channelId) + ":" + encodeB64(letterEntry.getKey().toString());
	}
}
