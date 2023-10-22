package de.corelogics.mediaview.service.proxy.downloader;

import com.github.benmanes.caffeine.cache.Ticker;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.BitSet;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheDirectoryTest {
    @Mock
    private Ticker ticker;

    @TempDir(cleanup = CleanupMode.ALWAYS)
    private File tempDir;

    @Test
    void givenCacheSizeToSmall_whenCreating_thenExceptionIsThrown() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> new CacheDirectory(5, tempDir, ticker).close());
    }

    @Nested
    class GivenCacheDirectoryCanBeInstantiated {
        private CacheDirectory sut;

        @BeforeEach
        void setupSut() {
            sut = new CacheDirectory(10, tempDir, ticker);
        }

        @AfterEach
        void closeSut() {
            when(ticker.read()).thenReturn((long) Integer.MAX_VALUE);
            sut.close();
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
            @Test
            void whenWritingContent_thenWriteCorrectDataAtCorrectPosition() throws IOException {
                val testData = "My Test Data";
                val testDataBytes = testData.getBytes(US_ASCII);

                sut.writeContent("1234", 1000, testDataBytes);
                sut.writeContent("1234", 2000, "Some More Data".getBytes(US_ASCII));

                var data = new byte[testData.length()];
                data[0] = "0".getBytes(US_ASCII)[0];
                data[data.length - 1] = data[0];

                val a = new SoftAssertions();
                a.assertThat(
                        sut.readContentByte("1234", 1000))
                    .isEqualTo((int) 'M');
                a.assertThat(sut.readContentBytes("1234", 1000, data, 1, data.length - 2))
                    .isEqualTo(testData.length() - 2);
                a.assertThat(new String(data, US_ASCII)).isEqualTo("0My Test Da0");

                a.assertAll();
            }

            @Test
            void whenGrowing_thenSetNewSize() throws IOException, CacheSizeExhaustedException {
                val testData = "Some more test data";
                val testDataBytes = testData.getBytes(US_ASCII);
                val contentFile = new File(tempDir, IdUtils.encodeId("4321") + ".mp4");

                assertThat(contentFile.exists()).isFalse();
                sut.writeContent("4321", 1000, testDataBytes);
                ;
                assertThat(contentFile.exists()).isTrue();
                assertThat(contentFile.length()).isEqualTo(1000 + testDataBytes.length);

                sut.growContentFile("4321", 10000);
                ;
                assertThat(contentFile.exists()).isTrue();
                assertThat(contentFile.length()).isEqualTo(10000);
            }
        }
    }
}
