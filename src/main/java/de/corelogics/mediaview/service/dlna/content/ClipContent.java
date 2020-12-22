package de.corelogics.mediaview.service.dlna.content;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.service.dlna.DlnaRequest;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.item.VideoItem;
import org.seamless.util.MimeType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Singleton
public class ClipContent extends BaseDnlaRequestHandler {
    private static final MimeType MIME_TYPE_VIDEO_MP4 = new MimeType("video", "mp4");
    private static final String URN_PREFIX_CLIP = "urn:corelogics.de:mediaview:clip:";

    private static final DateTimeFormatter DTF_DATE = DateTimeFormatter.ofPattern("dd.MM.").withLocale(Locale.GERMANY);
    private static final DateTimeFormatter DTF_TIME = DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.GERMANY);

    private final ClipContentUrlGenerator clipContentUrlGenerator;

    @Inject
    public ClipContent(ClipContentUrlGenerator clipContentUrlGenerator) {
        this.clipContentUrlGenerator = clipContentUrlGenerator;
    }

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
                request.getObjectId(),
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
