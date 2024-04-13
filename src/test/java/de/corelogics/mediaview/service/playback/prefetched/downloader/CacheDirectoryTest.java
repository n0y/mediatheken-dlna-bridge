package de.corelogics.mediaview.service.playback.prefetched.downloader;

import com.github.benmanes.caffeine.cache.Ticker;
import de.corelogics.mediaview.service.base.lifecycle.ShutdownRegistry;
import de.corelogics.mediaview.service.base.threading.BaseThreading;
import de.corelogics.mediaview.util.IdUtils;
import lombok.val;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.stream.Stream.concat;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheDirectoryTest {
    @Mock
    private Ticker ticker;

    @Mock
    private BaseThreading baseThreading;

    @Spy
    private ShutdownRegistry shutdownRegistry = new ShutdownRegistry();

    @TempDir(cleanup = CleanupMode.ALWAYS)
    private File tempDir;

    @AfterEach
    void shutdownAtEnd() {
        shutdownRegistry.shutdown();
    }

    @Test
    void givenCacheSizeToSmall_whenCreating_thenExceptionIsThrown() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> new CacheDirectory(baseThreading, shutdownRegistry, 5, tempDir, ticker));
    }

    @Nested
    class GivenCacheDirectoryCanBeInstantiated {
        private CacheDirectory sut;

        @BeforeEach
        void setupSut() {
            sut = new CacheDirectory(baseThreading, shutdownRegistry, 10, tempDir, ticker);
        }

        @AfterEach
        void closeSut() {
            when(ticker.read()).thenReturn((long) Integer.MAX_VALUE);
        }

        @Test
        void givenMetadataIsOk_thenMakeSureMetaDataIsEqual() throws IOException {
            val clipMetadata = new ClipMetadata()
                .contentType("my-content-type")
                .size(10000)
                .bitSet(new BitSet(100))
                .numberOfChunks(100);

            // writing metadata
            sut.writeMetadata("1234", clipMetadata);
            assertThat(tempDir
                .listFiles(
                    pathname -> (IdUtils.encodeId("1234") + ".json").equals(pathname.getName())))
                .hasSize(1);

            // reading metadata
            val readClipMetadata = sut.loadMetadata("1234");
            assertThat(readClipMetadata).isPresent()
                .get()
                .usingRecursiveComparison()
                .isEqualTo(clipMetadata);
        }

        @Nested
        class WhenWritingContentData {
            private static final String TEST_DATA = "My Test Data";
            private static final byte[] TEST_DATA_BYTES = TEST_DATA.getBytes(US_ASCII);

            @Test
            void whenWritingContent_thenWriteCorrectDataAtCorrectPosition() throws IOException, CacheSizeExhaustedException {
                sut.growContentFile("1234", 2500);
                sut.writeContent("1234", 1000, TEST_DATA_BYTES);
                sut.writeContent("1234", 2000, "Some More Data".getBytes(US_ASCII));

                var data = new byte[TEST_DATA_BYTES.length];
                data[0] = "0".getBytes(US_ASCII)[0];
                data[data.length - 1] = data[0];

                val a = new SoftAssertions();
                a.assertThat(
                        sut.readContentByte("1234", 1000))
                    .isEqualTo('M');
                a.assertThat(sut.readContentBytes("1234", 1000, data, 1, data.length - 2))
                    .isEqualTo(TEST_DATA_BYTES.length - 2);
                a.assertThat(new String(data, US_ASCII)).isEqualTo("0My Test Da0");

                a.assertAll();
            }

            @Test
            void givenContentIsThereButNotOpened_thenReadCorrectly() throws IOException, CacheSizeExhaustedException {
                sut.growContentFile("1234", 1000);
                sut.writeContent("1234", 0, TEST_DATA_BYTES);
                when(ticker.read()).thenReturn(TimeUnit.MINUTES.toNanos(10));
                sut.cleanUp();
                // file should be closed now

                val read = new byte[TEST_DATA_BYTES.length];
                assertThat(sut.readContentBytes("1234", 0, read, 0, read.length))
                    .isEqualTo(read.length);
                assertThat(new String(read, US_ASCII)).isEqualTo(TEST_DATA);

            }

            @Test
            void givenReadPositionIsTooLarge_thenExceptionIsThrown() throws IOException, CacheSizeExhaustedException {
                sut.growContentFile("1234", 12_000);
                sut.writeContent("1234", 10_000, TEST_DATA_BYTES);
                val read = new byte[TEST_DATA_BYTES.length];
                val a = new SoftAssertions();
                a.assertThatExceptionOfType(EOFException.class)
                    .isThrownBy(() -> sut.readContentBytes("1234", 20_000L, read, 0, read.length));
                a.assertThatExceptionOfType(EOFException.class)
                    .isThrownBy(() -> sut.readContentByte("1234", 30_000L));
                a.assertAll();
            }

            @Test
            void whenGrowing_thenSetNewSize() throws IOException, CacheSizeExhaustedException {
                val contentFile = new File(tempDir, IdUtils.encodeId("4321") + ".mp4");

                assertThat(contentFile.exists()).isFalse();
                sut.growContentFile("4321", 1200);
                sut.writeContent("4321", 1000, TEST_DATA_BYTES);

                assertThat(contentFile.exists()).isTrue();
                assertThat(contentFile.length()).isEqualTo(1200);

                sut.growContentFile("4321", 10000);
                assertThat(contentFile.exists()).isTrue();
                assertThat(contentFile.length()).isEqualTo(10000);
            }

            @Test
            void givenPositionIsAfterFileEnd_whenWriting_thenExceptionIsThrown() throws CacheSizeExhaustedException, IOException {
                sut.growContentFile("1234", 1000L);
                assertThatExceptionOfType(EOFException.class)
                    .isThrownBy(() -> sut.writeContent("1234", 1001, new byte[0]));
                assertThatExceptionOfType(EOFException.class)
                    .isThrownBy(() -> sut.writeContent("1234", 9999, TEST_DATA_BYTES));
            }

            @Test
            void givenContentIsNotPresent_whenReading_thenExceptionIsThrown() {
                assertSoftly(a -> {
                    a.assertThatExceptionOfType(FileNotFoundException.class)
                        .isThrownBy(() -> sut.readContentByte("not-existing", 0));
                    a.assertThatExceptionOfType(FileNotFoundException.class)
                        .isThrownBy(() -> sut.readContentBytes("not-existing", 0, new byte[1], 0, 1));
                });
            }

            @Test
            void givenMetadataIsNotPresent_whenLoadingMetadata_thenEmpyResultIsReturned() throws IOException {
                assertThat(sut.loadMetadata("nonexistent")).isEmpty();

            }

            @Test
            void givenNotEnoughSpaceLeft_whenGrowing_thenExceptionIsThrown() {
                assertThatNoException().isThrownBy(() -> sut.growContentFile("4321", 1_000L));
                assertThatExceptionOfType(CacheSizeExhaustedException.class)
                    .isThrownBy(() -> sut.growContentFile("4321", 12_000_000_000L));
            }

            @Nested
            class WhenCleaningUp {
                @Test
                void givenThereIsNothingToCleanUp_thenReturnFalse() {
                    assertThat(sut.tryCleanupCacheDir(Set.of())).isFalse();
                }

                @Nested
                class GivenFilesForCleanupArePresent {
                    private final List<String> createdClipIds = List.of("clip-1", "clip-2", "clip-3");
                    private List<File> contentFiles;
                    private List<File> metaFiles;

                    @BeforeEach
                    void createFiles() throws IOException {
                        contentFiles = createdClipIds.stream()
                            .map(clipId -> new File(tempDir, IdUtils.encodeId(clipId) + ".mp4"))
                            .toList();
                        metaFiles = createdClipIds.stream()
                            .map(clipId -> new File(tempDir, IdUtils.encodeId(clipId) + ".json"))
                            .toList();

                        long lastModified = System.currentTimeMillis() - 10000;
                        for (val file : concat(contentFiles.stream(), metaFiles.stream()).toList()) {
                            Files.writeString(file.toPath(), "Content of " + file.getName());
                            file.setLastModified(lastModified);
                            lastModified += 1000;
                        }
                    }

                    @Test
                    void givenNoFilesExcluded_thenSuccessfullyAllFilesOneAtATime() {
                        val a = new SoftAssertions();
                        a.assertThat(sut.tryCleanupCacheDir(Collections.emptySet())).isTrue();
                        a.assertThat(contentFiles).extracting(File::exists).containsExactly(false, true, true);
                        a.assertThat(metaFiles).extracting(File::exists).containsExactly(false, true, true);

                        a.assertThat(sut.tryCleanupCacheDir(Collections.emptySet())).isTrue();
                        a.assertThat(contentFiles).extracting(File::exists).containsExactly(false, false, true);
                        a.assertThat(metaFiles).extracting(File::exists).containsExactly(false, false, true);

                        a.assertThat(sut.tryCleanupCacheDir(Collections.emptySet())).isTrue();
                        a.assertThat(contentFiles).extracting(File::exists).containsExactly(false, false, false);
                        a.assertThat(metaFiles).extracting(File::exists).containsExactly(false, false, false);

                        a.assertThat(sut.tryCleanupCacheDir(Collections.emptySet())).isFalse();

                        a.assertAll();
                    }

                    @Test
                    void givenCurrentlyOpenClipsPassed_thenSuccessfullForAllOtherFiles() {
                        val a = new SoftAssertions();
                        a.assertThat(sut.tryCleanupCacheDir(Set.of(createdClipIds.get(1)))).isTrue();
                        a.assertThat(contentFiles).extracting(File::exists).containsExactly(false, true, true);
                        a.assertThat(metaFiles).extracting(File::exists).containsExactly(false, true, true);

                        a.assertThat(sut.tryCleanupCacheDir(Set.of(createdClipIds.get(1)))).isTrue();
                        a.assertThat(contentFiles).extracting(File::exists).containsExactly(false, true, false);
                        a.assertThat(metaFiles).extracting(File::exists).containsExactly(false, true, false);

                        a.assertThat(sut.tryCleanupCacheDir(Set.of(createdClipIds.get(1)))).isFalse();

                        a.assertAll();
                    }

                    @Test
                    void givenContentFileAndMetaFileHaveJustBeenWrittenTo_thenStillSuccessfullyCleanThemUp() throws CacheSizeExhaustedException, IOException {
                        sut.growContentFile(createdClipIds.get(1), 1000L);
                        sut.writeMetadata(createdClipIds.get(1), new ClipMetadata().bitSet(new BitSet(100)));

                        val a = new SoftAssertions();
                        a.assertThat(sut.tryCleanupCacheDir(Collections.emptySet())).isTrue();
                        a.assertThat(sut.tryCleanupCacheDir(Collections.emptySet())).isTrue();
                        a.assertThat(sut.tryCleanupCacheDir(Collections.emptySet())).isTrue();

                        a.assertAll();
                    }

                    @Test
                    void givenOnlyMetadataFileIsPresent_thenStillSuccessfullyCleanThemUp() {
                        contentFiles.forEach(File::delete);
                    }
                }
            }
        }
    }
}
