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

package de.corelogics.mediaview.repository.tracked;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.repository.LuceneDirectory;
import lombok.val;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackedViewRepositoryTest {
    @Nested
    class WhenAddingTrackedView {
        @InjectMocks
        private TrackedViewRepository sut;

        @Mock
        private LuceneDirectory luceneDirectoryMock;

        @Mock
        private IndexWriter writerMock;

        @Mock(answer = Answers.RETURNS_SELF)
        private LuceneDirectory.DocumentBuilder documentBuilderMock;

        @BeforeEach
        void mockPerformUpdate() throws IOException {
            doAnswer(a -> {
                a.getArgument(1, LuceneDirectory.UpdateFunction.class).update(writerMock);
                return (Void) null;
            }).when(luceneDirectoryMock).performUpdate(any(), any());
        }

        @BeforeEach
        void mockCreateBuilder() {
            when(luceneDirectoryMock.buildDocument(any(), anyLong())).thenReturn(documentBuilderMock);
        }

        @Test
        void thenFieldsAreFilledCorrectly() throws IOException {
            val viewedAt = ZonedDateTime.now(ZoneId.of("Europe/Berlin")).minusDays(1).withHour(10);
            val entry = new ClipEntry("my channel", "my container", null, "my title", null, 0, null, "url-hd");
            val document = new Document();

            when(documentBuilderMock.build()).thenReturn(document);

            sut.addTrackedView(entry, viewedAt);

            verify(documentBuilderMock).addField(TrackedViewRepository.TrackedViewField.CHANNELNAME, "my channel");
            verify(documentBuilderMock).addField(TrackedViewRepository.TrackedViewField.CONTAINEDIN, "my container");
            verify(documentBuilderMock).addField(TrackedViewRepository.TrackedViewField.TITLE, "my title");
            verify(documentBuilderMock).addField(TrackedViewRepository.TrackedViewField.LAST_VIEWED_AT, viewedAt);
            verify(documentBuilderMock, times(1)).build();
            verify(luceneDirectoryMock, times(1)).performUpdate(any(), any());
            ;
            verify(writerMock).updateDocument(any(), eq(document));
        }
    }

    @Nested
    class WhenQueryingRecentlySeenContainedIns {
        private final ClipEntry clip1Container1 = new ClipEntry("my-chan-1", "container 1", null, "clip 1 of container 1 in chan-1", null, 0, null, "url-1");
        private final ClipEntry clip2Container1 = new ClipEntry("my-chan-1", "container 1", null, "clip 2 of container 1 in chan-1", null, 0, null, "url-2");
        private final ClipEntry clip1Container2 = new ClipEntry("my-chan-1", "container 2", null, "clip 1 of container 2 in chan-1", null, 0, null, "url-3");
        private final ClipEntry clip2Container2 = new ClipEntry("my-chan-1", "container 2", null, "clip 2 of container 2 in chan-1", null, 0, null, "url-4");
        private final ClipEntry clip1Container3 = new ClipEntry("my-chan-2", "container 1", null, "clip 1 of container 1 in chan-2", null, 0, null, "url-5");
        private final ClipEntry clip2Container3 = new ClipEntry("my-chan-2", "container 1", null, "clip 2 of container 1 in chan-2", null, 0, null, "url-6");

        @Mock
        private MainConfiguration mainConfiguration;

        private TrackedViewRepository sut;

        @BeforeEach
        void createSut() {
            this.sut = new TrackedViewRepository(new LuceneDirectory(mainConfiguration));
        }

        @Test
        void thenReturnFound() {
            val refTime = ZonedDateTime.now(ZoneId.of("Europe/Berlin")).withHour(10);

            sut.addTrackedView(clip1Container1, refTime);
            sut.addTrackedView(clip1Container1, refTime.minusDays(1));
            sut.addTrackedView(clip2Container1, refTime);

            sut.addTrackedView(clip1Container2, refTime.minusDays(1));
            sut.addTrackedView(clip1Container2, refTime.minusDays(2));
            sut.addTrackedView(clip2Container2, refTime.minusDays(1));

            sut.addTrackedView(clip1Container3, refTime.minusDays(5));
            sut.addTrackedView(clip2Container3, refTime.minusDays(20));

            val resp = sut.getRecentlySeenContainedIns(refTime.minusDays(6), refTime.plusDays(1).truncatedTo(ChronoUnit.DAYS));
            assertSoftly(a -> {
                a.assertThat(resp).extracting(TrackedContainedIn::channelName, TrackedContainedIn::containedIn)
                    .describedAs("We expect three ContainedIns. The 3rd one is named like the first one, but on another channel")
                    .contains(
                        tuple(clip1Container1.getChannelName(), clip1Container1.getContainedIn()),  // has 3 views, last one today
                        tuple(clip1Container1.getChannelName(), clip1Container1.getContainedIn()),  // has 3 views, last one yesterday
                        tuple(clip1Container1.getChannelName(), clip1Container1.getContainedIn())); // has 1 view
                a.assertThat(resp).extracting(TrackedContainedIn::numberViewed)
                    .describedAs("first two have three views, the last one has two, but one of them is before the earliest date")
                    .contains(3, 3,
                        1); // 1 view, because the second view is before the cutoff date
                a.assertThat(resp).extracting(TrackedContainedIn::earliestViewed)
                    .contains(
                        refTime.minusDays(1),
                        refTime.minusDays(2),
                        refTime.minusDays(5)); // minusDays(20) is before cutoff date
                a.assertThat(resp).extracting(TrackedContainedIn::latestViewed)
                    .contains(
                        refTime,
                        refTime.minusDays(1),
                        refTime.minusDays(5));
            });
        }
    }
}
