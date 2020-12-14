/*
 * MIT License
 *
 * Copyright (c) 2020 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.repository.clip;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ClipRepositoryTest {
    private final ZonedDateTime REF_TIME = ZonedDateTime.of(2020, 10, 4, 8, 30, 20, 0, ZoneId.of("Europe/Berlin"));

    private ClipRepository sut;

    @BeforeEach
    void createDatabase() {
        sut = new ClipRepository();
        sut.initialize("jdbc:h2:mem:test");
    }

    @AfterEach
    void closeDbase() {
        sut.destroy();
    }

    @Nested
    @DisplayName("given everything works fine")
    class GivenOnHapyPathTests {
        @BeforeEach
        void insertClips() {
            var importedAt = ZonedDateTime.now();
            sut.addClips(
                    List.of(
                            createClip("A", "1", "A1-1", 10),
                            createClip("A", "1", "A1-2", 8),
                            createClip("A", "2", "A2-1", 4),
                            createClip("B", "1", "B1-1", 10),
                            createClip("B", "1", "B1-2", 8),
                            createClip("B", "2", "B2-1", 4),
                            createClip("B", "B3", "B3-1", 2),
                            createClip("B", "B3", "B3-2", 1)),
                    importedAt);
        }

        @Test
        void whenFindingAllChannels_thenReturnChannelsSortedByClips() {
            assertThat(sut.findAllChannels()).containsExactly("channel:B", "channel:A");
        }

        @Test
        void whenFindingAllContainedIns_thenReturnShowsAndClipCounts() {
            assertSoftly(a -> {
                a.assertThat(sut.findAllContainedIns("channel:A"))
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                "show:1", 2,
                                "show:2", 1));
                a.assertThat(sut.findAllContainedIns("channel:B"))
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                "show:1", 2,
                                "show:2", 1,
                                "show:B3", 2));
            });
        }

        @Test
        void whenFindingAllContainsInsStartingWith_thenReturnOnlyThoseShowsAndCounts() {
            assertThat(sut.findAllContainedIns("channel:B", "show:B"))
                    .containsExactly(entry("show:B3", 2));
        }

        @Test
        void whenFindingAllClipsFromShow_thenReturnClipsOrderedByBroadcastDateDesc() {
            assertSoftly(a -> {
                a.assertThat(sut.findAllClips("channel:A", "show:1"))
                        .extracting(ClipEntry::getTitle)
                        .containsExactly("title:A1-2", "title:A1-1");
                a.assertThat(sut.findAllClips("channel:B", "show:1"))
                        .extracting(ClipEntry::getTitle)
                        .containsExactly("title:B1-2", "title:B1-1");
            });
        }

        @Test
        void whenFindingClip_thenAllFieldsAreReturned() {
            var expectedClip = createClip("A", "2", "A2-1", 4);
            assertThat(sut.findAllClips(expectedClip.getChannelName(), expectedClip.getContainedIn()))
                    .containsExactly(expectedClip);
        }
    }

    @Nested
    @DisplayName("when managing import runs")
    class ManageImportRunsTests {
        @Test
        void givenNoImportRan_thenNoTimestampIsReturned() {
            assertThat(sut.findLastFullImport()).isEmpty();
        }

        @Test
        void givenImportRunInserted_thenReturnThisRun() {
            sut.updateLastFullImport(REF_TIME);
            assertThat(sut.findLastFullImport()).isPresent().get()
                    .extracting(ZonedDateTime::toEpochSecond)
                    .isEqualTo(REF_TIME.toEpochSecond());
        }

        @Test
        void givenImportRunUpdated_thenReturnNewValue() {
            sut.updateLastFullImport(REF_TIME.minusDays(10));
            sut.updateLastFullImport(REF_TIME.minusDays(2));
            assertThat(sut.findLastFullImport()).isPresent().get()
                    .extracting(ZonedDateTime::toEpochSecond)
                    .isEqualTo(REF_TIME.minusDays(2).toEpochSecond());
        }
    }

    @Nested
    @DisplayName("when deleting clips not imported in last full run")
    class DeleteNotImportedAtTests {
        @Test
        void thenAllOtherClipsAreRemoved() {
            var oldImportTime = REF_TIME.minusDays(5);
            var newImportTime = REF_TIME.minusDays(1);
            sut.addClips(
                    List.of(
                            createClip("A", "1", "o1", 10),
                            createClip("A", "1", "o2", 10)),
                    oldImportTime);
            sut.addClips(
                    List.of(
                            createClip("A", "1", "n1", 10),
                            createClip("A", "1", "n2", 10)),
                    newImportTime);
            assertSoftly(a -> {
                a.assertThat(sut.findAllClips("channel:A", "show:1")).extracting(ClipEntry::getTitle)
                        .containsExactlyInAnyOrder("title:o1", "title:o2", "title:n1", "title:n2");

                sut.deleteClipsNotImportedAt(newImportTime);

                a.assertThat(sut.findAllClips("channel:A", "show:1")).extracting(ClipEntry::getTitle)
                        .containsExactlyInAnyOrder("title:n1", "title:n2");
            });
        }
    }

    @Nested
    @DisplayName("when connecting")
    class WhenConnectingTests {
        @Nested
        @DisplayName("when calculating cache sizes")
        class WhenCalculatingCacheSizes {
            @Test
            void givenInOkRange_thenCalcSizeCorrectly() {
                sut.maxMemorySupplier = () -> 400_000_000L;
                assertThat(sut.calcCacheSize()).isEqualTo(250_000_000L);
            }

            @Test
            void givenBelowLimit_thenReturnLowerLimit() {
                sut.maxMemorySupplier = () -> 100_000_000L;
                assertThat(sut.calcCacheSize()).isEqualTo(16_000_000L);
            }

            @Test
            void givenAboveLimit_thenReturnUpperLimit() {
                sut.maxMemorySupplier = () -> 3_000_000_000L;
                assertThat(sut.calcCacheSize()).isEqualTo(1_500_000_000L);
            }
        }

        @Nested
        @DisplayName("when calculating jdbc url")
        class WhenCalculatingJdbcUrl {
            @ParameterizedTest
            @ValueSource(strings = {"../test.url/location", "/home/x", "./data/calc"})
            void whenDatabaseLocationIsInserted_thenEnsureItsInTheUrl(String location) {
                sut.databaseLocation = location;
                assertThat(sut.calcJdbcUrl(0)).startsWith("jdbc:h2:" + location + ";");
            }

            @ParameterizedTest
            @ValueSource(longs = {1L, 1_000_000L, 5_000_000_000L})
            void whenCacheSizeIsInserted_thenEnsureItsInTheUrl(long cacheSize) {
                var cacheSizeKb = cacheSize / 1024;
                sut.databaseLocation = "nowhere";
                assertThat(sut.calcJdbcUrl(cacheSize))
                        .contains(";CACHE_SIZE=" + cacheSizeKb);
            }
        }
    }

    private ClipEntry createClip(String channel, String show, String title, int daysBefore) {
        return new ClipEntry(
                "channel:" + channel,
                "show:" + show,
                REF_TIME.minusDays(daysBefore),
                "title:" + title,
                "desc:" + channel + "," + show + "," + title,
                "04:12:00",
                100L,
                "https://" + show + "." + channel + ".test/" + title + ".mp4",
                "https://hd." + show + "." + channel + ".test/" + title + ".mp4");
    }
}
