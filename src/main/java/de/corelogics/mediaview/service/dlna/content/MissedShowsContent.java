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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.LongStream;

public class MissedShowsContent extends BaseDnlaRequestHandler {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("EEEE").localizedBy(Locale.GERMANY);

    private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

    private static final String URN_PREFIX = "urn:corelogics.de:mediaview:missed:";

    private static final String URN_OVERVIEW = "urn:corelogics.de:mediaview:missed:overview";

    private static final String URN_PREFIX_CHANNEL = "urn:corelogics.de:mediaview:missed:channel:";

    private static final String URN_PREFIX_CHANNELTIME = "urn:corelogics.de:mediaview:missed:channeltime:";

    private final ClipContent clipContent;

    private final ClipRepository clipRepository;

    private enum ChannelTime {
        TIME_0_8("0:00 bis 8:00", 0, 8),
        TIME_8_12("8:00 bis 12:00", 8, 12),
        TIME_12_20("12:00 bis 20:00", 12, 20),
        TIME_20_0("20:00 bis 0:00", 20, 0);

        private final String title;

        private final LocalTime startTime;

        private final LocalTime endTime;

        ChannelTime(String title, int startHour, int endHour) {
            this.title = title;
            this.startTime = LocalTime.of(startHour, 0);
            this.endTime = LocalTime.of(endHour, 0).minusSeconds(1);
        }

        public String getTitle() {
            return title;
        }

        public LocalTime getStartTime() {
            return startTime;
        }

        public LocalTime getEndTime() {
            return endTime;
        }
    }

    public MissedShowsContent(ClipContent clipContent, ClipRepository clipRepository) {
        this.clipContent = clipContent;
        this.clipRepository = clipRepository;
    }

    public StorageFolder createLink(DlnaRequest request) {
        return new StorageFolder(
                URN_OVERVIEW,
                request.getObjectId(), "Sendung Verpasst",
                "",
                clipRepository.findAllChannels().size(),
                null);
    }

    @Override
    public boolean canHandle(DlnaRequest request) {
        return request.getObjectId().startsWith(URN_PREFIX);
    }

    @Override
    protected DIDLContent respondWithException(DlnaRequest request) throws Exception {
        var didl = new DIDLContent();
        if (request.getObjectId().equals(URN_OVERVIEW)) {
            addOverview(request, didl);
        } else if (request.getObjectId().startsWith(URN_PREFIX_CHANNEL)) {
            var split = request.getObjectId().split(":");
            var channelName = IdUtils.decodeId(split[split.length - 1]);
            addChannelTimes(request, channelName, didl);
        } else if (request.getObjectId().startsWith(URN_PREFIX_CHANNELTIME)) {
            var split = request.getObjectId().split(":");
            var channelName = IdUtils.decodeId(split[split.length - 3]);
            var daysBefore = Integer.parseInt(split[split.length - 2]);
            var time = ChannelTime.values()[Integer.parseInt(split[split.length - 1])];
            addClips(request, channelName, time, daysBefore, didl);
        }

        return didl;
    }

    private void addClips(DlnaRequest request, String channelName, ChannelTime time, int daysBefore, DIDLContent didl) {
        var chosenDay = LocalDate.now().minusDays(daysBefore);
        clipRepository
                .findAllClipsForChannelBetween(
                        channelName,
                        time.getStartTime().atDate(chosenDay).atZone(ZONE_BERLIN),
                        time.getEndTime().atDate(chosenDay).atZone(ZONE_BERLIN))
                .stream()
                .map(e -> clipContent.createLinkWithTimePrefix(request, e))
                .forEach(didl::addItem);
    }

    private void addChannelTimes(DlnaRequest request, String channelName, DIDLContent didl) {
        var today = ZonedDateTime.now();
        LongStream.rangeClosed(0, 6).mapToObj(l -> Map.entry(l, today.minusDays(l))).flatMap(day ->
                Arrays.stream(ChannelTime.values()).map(ct -> new StorageFolder(
                        URN_PREFIX_CHANNELTIME + IdUtils.encodeId(channelName) + ":" + day.getKey() + ":" + ct.ordinal(),
                        request.getObjectId(),
                        DATE_TIME_FORMAT.format(day.getValue()) + " " + ct.getTitle(),
                        "",
                        10,
                        null)))
                .forEach(didl::addContainer);
    }

    private void addOverview(DlnaRequest request, DIDLContent didl) {
        clipRepository.findAllChannels().stream()
                .map(channel -> new StorageFolder(
                        idChannel(channel),
                        request.getObjectId(),
                        channel,
                        "",
                        100,
                        null))
                .forEach(didl::addContainer);
    }

    private String idChannel(String channel) {
        return URN_PREFIX_CHANNEL + IdUtils.encodeId(channel);
    }
}
