/*
 * MIT License
 *
 * Copyright (c) 2021 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PropertiesConfigurationSourceTest {
    @TempDir
    File tempDir;

    @Nested
    class GivenLoadingFromPropertiesFileTest {
        @Nested
        class GivenPropertiesFileExistsTest {
            private File propertiesFile;
            private PropertiesConfigurationSource sut;

            @BeforeEach
            void createPropertiesFile(TestInfo info) throws IOException {
                propertiesFile = new File(tempDir, info.getDisplayName().hashCode() + ".properties");
                Files.writeString(propertiesFile.toPath(), "a=value:a\nb=value:b", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                sut = new PropertiesConfigurationSource(propertiesFile);
            }

            @ParameterizedTest
            @ValueSource(strings = {"a", "b"})
            void whenGettingExistingKeys_thenReturnValues(String key) {
                var expectedValue = "value:" + key;
                assertThat(sut.getConfigValue(key)).isPresent().get().isEqualTo(expectedValue);
            }

            @ParameterizedTest
            @ValueSource(strings = {"c", "d"})
            void whenGettingNonExistingKeys_thenReturnEmpty(String key) {
                assertThat(sut.getConfigValue(key)).isEmpty();
            }

            @Test
            void whenGettingExistingKeys_thenReturnAllPropertyNamed() {
                assertThat(sut.getConfigKeys()).containsExactly("a", "b");
            }
        }
    }

    @Nested
    class GivenLoadingFromClassPathUriTests {
        private PropertiesConfigurationSource sut;

        @Nested
        class GivenPropertiesFileExistsTest {
            @BeforeEach
            void createPropertiesFile() throws IOException {
                sut = new PropertiesConfigurationSource(PropertiesConfigurationSourceTest.class.getResource("/prop-test.properties"));
            }

            @ParameterizedTest
            @ValueSource(strings = {"a", "b"})
            void whenGettingExistingKeys_thenReturnValues(String key) {
                var expectedValue = "value:" + key;
                assertThat(sut.getConfigValue(key)).isPresent().get().isEqualTo(expectedValue);
            }

            @ParameterizedTest
            @ValueSource(strings = {"c", "d"})
            void whenGettingNonExistingKeys_thenReturnEmpty(String key) {
                assertThat(sut.getConfigValue(key)).isEmpty();
            }

            @Test
            void whenGettingExistingKeys_thenReturnAllPropertyNamed() {
                assertThat(sut.getConfigKeys()).containsExactly("a", "b");
            }
        }
    }

    @Nested
    class GivenUsingExistingPropertiesTests {
        private PropertiesConfigurationSource sut;

        @Nested
        class GivenPropertiesFileExistsTest {
            @BeforeEach
            void createPropertiesFile() {
                var props = new Properties();
                props.setProperty("a", "value:a");
                props.setProperty("b", "value:b");
                sut = new PropertiesConfigurationSource(props);
            }

            @ParameterizedTest
            @ValueSource(strings = {"a", "b"})
            void whenGettingExistingKeys_thenReturnValues(String key) {
                var expectedValue = "value:" + key;
                assertThat(sut.getConfigValue(key)).isPresent().get().isEqualTo(expectedValue);
            }

            @ParameterizedTest
            @ValueSource(strings = {"c", "d"})
            void whenGettingNonExistingKeys_thenReturnEmpty(String key) {
                assertThat(sut.getConfigValue(key)).isEmpty();
            }

            @Test
            void whenGettingExistingKeys_thenReturnAllPropertyNamed() {
                assertThat(sut.getConfigKeys()).containsExactly("a", "b");
            }
        }
    }

}
