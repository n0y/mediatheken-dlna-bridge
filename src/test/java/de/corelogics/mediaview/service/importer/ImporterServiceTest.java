/*
 * MIT License
 *
 * Copyright (c) 2020-2025 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.service.importer;

import de.corelogics.mediaview.client.mediatheklist.MediathekListClient;
import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.client.mediathekview.MediathekListe;
import de.corelogics.mediaview.client.mediathekview.MediathekViewImporter;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.service.base.lifecycle.ShutdownRegistry;
import de.corelogics.mediaview.service.base.threading.BaseThreading;
import de.corelogics.mediaview.service.repository.clip.ClipRepository;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImporterServiceTest {
    @Mock
    private MainConfiguration mainConfiguration;
    @Mock
    private BaseThreading baseThreading;
    @Mock
    private ShutdownRegistry shutdownRegistry;
    @Mock
    private MediathekListClient mediathekListClient;
    @Mock
    private MediathekViewImporter importer;
    @Mock
    private ClipRepository clipRepository;
    @Spy
    private Supplier<ZonedDateTime> currentTimeProvider = new Supplier<ZonedDateTime>() {
        @Override
        public ZonedDateTime get() {
            return ZonedDateTime.now();
        }
    };

    @InjectMocks
    private ImporterService sut;

    @BeforeEach
    void setupTime() {
        sut.currentTimeProvider = this.currentTimeProvider;
    }

    @Nested
    class WhenScheduleImport {

        @Test
        void thenRegistersShutdownAndSchedulesNextImport() {
            when(mainConfiguration.updateIntervalFullHours()).thenReturn(24);
            when(clipRepository.findLastFullImport()).thenReturn(Optional.empty());

            sut.scheduleImport();

            verify(shutdownRegistry).registerShutdown(any());
            verify(baseThreading).schedule(any(), any());
        }

        @Test
        void givenImportWithExistingImport_thenSchedulesAtCorrectTime() {
            val now = ZonedDateTime.now();
            val lastImport = now.minusHours(12);
            when(mainConfiguration.updateIntervalFullHours()).thenReturn(24);
            when(clipRepository.findLastFullImport()).thenReturn(Optional.of(lastImport));
            when(currentTimeProvider.get()).thenReturn(now);

            sut.scheduleImport();

            val expectedDelay = Duration.ofHours(12);
            verify(baseThreading).schedule(any(), eq(expectedDelay));
        }

        @Test
        void givenNoExistingImport_thenSchedulesImmediately() {
            when(mainConfiguration.updateIntervalFullHours()).thenReturn(24);
            when(clipRepository.findLastFullImport()).thenReturn(Optional.empty());

            sut.scheduleImport();

            val expectedDelay = Duration.ofSeconds(10);
            verify(baseThreading).schedule(any(), eq(expectedDelay));
        }
    }

    @Test
    void whenShutdown_thenStopsService() {
        sut.shutdown();
        // Verify shutdown completes without exception
    }

    @Nested
    class WhenFullImport {
        @Test
        void thenUsesConsistentTimeForAllOperations() throws Exception {
            val startTime = ZonedDateTime.now();
            val endTime = startTime.plusMinutes(5);
            when(currentTimeProvider.get()).thenReturn(startTime, endTime);

            val mockInputStream = mock(InputStream.class);
            val mockMediathekListe = mock(MediathekListe.class);
            when(mediathekListClient.openMediathekListeFull()).thenReturn(mockInputStream);
            when(importer.createList(mockInputStream)).thenReturn(mockMediathekListe);
            when(mockMediathekListe.getStream()).thenReturn(createClipEntries(1).stream());

            sut.fullImport();

            verify(clipRepository).addClips(any(), eq(startTime));
            verify(clipRepository).deleteClipsImportedBefore(eq(startTime));
            verify(clipRepository).updateLastFullImport(eq(endTime));
        }

        @Test
        void givenClipListIsEmpty_thenDoNotCallsAddClips() throws Exception {
            setupFullImportTest(Stream.empty());

            sut.fullImport();

            verify(clipRepository, never()).addClips(any(), any());
        }

        @Test
        void given20ClipFound_thenCallsAddClipsOnce() throws Exception {
            val entries = createClipEntries(20);
            setupFullImportTest(entries.stream());

            sut.fullImport();

            verify(clipRepository, times(1)).addClips(any(), any());
        }

        @Test
        void given2500ClipsFound_thenCallsAddClipsThreeTimes() throws Exception {
            val entries = createClipEntries(2500);
            setupFullImportTest(entries.stream());

            sut.fullImport();

            verify(clipRepository, times(3)).addClips(any(), any());

            val captor = ArgumentCaptor.forClass(List.class);
            verify(clipRepository, times(3)).addClips(captor.capture(), any());
            val calls = captor.getAllValues();
            assertSoftly(softly -> {
                softly.assertThat(calls.get(0)).hasSize(1000);
                softly.assertThat(calls.get(1)).hasSize(1000);
                softly.assertThat(calls.get(2)).hasSize(500);
            });
        }

        private void setupFullImportTest(Stream<ClipEntry> entries) throws Exception {
            val mockInputStream = mock(InputStream.class);
            val mockMediathekListe = mock(MediathekListe.class);
            when(mediathekListClient.openMediathekListeFull()).thenReturn(mockInputStream);
            when(importer.createList(mockInputStream)).thenReturn(mockMediathekListe);
            when(mockMediathekListe.getStream()).thenReturn(entries);
        }

        @Test
        void givenShutdownCalledDuringProcessing_thenStopsEarly() throws Exception {
            val entries = createClipEntries(2000);
            val callCount = new AtomicInteger(0);

            val mockInputStream = mock(InputStream.class);
            val mockMediathekListe = mock(MediathekListe.class);
            when(mediathekListClient.openMediathekListeFull()).thenReturn(mockInputStream);
            when(importer.createList(mockInputStream)).thenReturn(mockMediathekListe);
            when(mockMediathekListe.getStream()).thenReturn(entries.stream().peek(e -> {
                if (callCount.incrementAndGet() == 500) {
                    sut.shutdown();
                }
            }));

            sut.fullImport();

            verify(clipRepository, never()).deleteClipsImportedBefore(any());
            verify(clipRepository, never()).updateLastFullImport(any());
        }

        private List<ClipEntry> createClipEntries(int count) {
            return IntStream.range(0, count)
                .mapToObj(i -> new ClipEntry("channel" + i, "show" + i, ZonedDateTime.now(),
                    "title" + i, "duration" + i, 1000L, "url" + i, "urlHd" + i))
                .toList();
        }
    }
}
