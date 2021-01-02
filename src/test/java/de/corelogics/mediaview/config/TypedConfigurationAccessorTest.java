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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TypedConfigurationAccessorTest {
    @InjectMocks
    private TypedConfigurationAccessor sut;

    @Mock
    private StringPropertySource propSource;

    @AfterEach
    void assertNoUnwantedInvocations() {
        verifyNoMoreInteractions(propSource);
    }

    @Nested
    class WhenGettingStringPropertyTest {
        @ParameterizedTest
        @ValueSource(strings = {"a", "prop-ä", "sd:d"})
        void givenValueExists_thenReturnValueAndCache(String key) {
            var expectedValue = "value:" + key;
            when(propSource.getConfigValue(key)).thenReturn(Optional.of(expectedValue));

            assertSoftly(a -> {
                a.assertThat(sut.get(key, null)).describedAs("First get").isEqualTo(expectedValue);
                a.assertThat(sut.get(key, null)).describedAs("Second get").isEqualTo(expectedValue);
            });
            verify(propSource, times(1)).getConfigValue(key);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "prop-ä", "sd:d"})
        void givenValueDoesntExist_thenReturnDefaultValueAndCache(String key) {
            when(propSource.getConfigValue(any())).thenReturn(Optional.empty());

            assertSoftly(a -> {
                a.assertThat(sut.get(key, "default:" + key)).describedAs("First get").isEqualTo("default:" + key);
                a.assertThat(sut.get(key, "default:" + key)).describedAs("Second get").isEqualTo("default:" + key);
                a.assertThat(sut.get(key, null)).describedAs("Third get").isNull();
            });
            verify(propSource, times(1)).getConfigValue(key);
        }
    }

    @Nested
    class WhenGettingIntPropertyTest {
        @ParameterizedTest
        @ValueSource(strings = {"a", "prop-ä", "sd:d"})
        void givenValueExists_thenReturnValueAndCache(String key) {
            var expectedValue = key.hashCode();
            when(propSource.getConfigValue(key)).thenReturn(Optional.of(Integer.toString(expectedValue)));

            assertSoftly(a -> {
                a.assertThat(sut.get(key, 0)).describedAs("First get").isEqualTo(expectedValue);
                a.assertThat(sut.get(key, 0)).describedAs("Second get").isEqualTo(expectedValue);
            });
            verify(propSource, times(1)).getConfigValue(key);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "prop-ä", "sd:d"})
        void givenValueDoesntExist_thenReturnDefaultValueAndCache(String key) {
            when(propSource.getConfigValue(any())).thenReturn(Optional.empty());

            assertSoftly(a -> {
                a.assertThat(sut.get(key, 100)).describedAs("First get").isEqualTo(100);
                a.assertThat(sut.get(key, 200)).describedAs("Second get").isEqualTo(200);
            });
            verify(propSource, times(1)).getConfigValue(key);
        }
    }

    @Nested
    class WhenGettingBoolPropertyTest {
        @ParameterizedTest
        @ValueSource(strings = {"a", "prop-ä", "sd:d"})
        void givenValueExists_thenReturnValueAndCache(String key) {
            var expectedValue = key.contains(":");
            when(propSource.getConfigValue(key)).thenReturn(Optional.of(Boolean.toString(expectedValue)));

            assertSoftly(a -> {
                a.assertThat(sut.get(key, false)).describedAs("First get").isEqualTo(expectedValue);
                a.assertThat(sut.get(key, false)).describedAs("Second get").isEqualTo(expectedValue);
            });
            verify(propSource, times(1)).getConfigValue(key);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "prop-ä", "sd:d"})
        void givenValueDoesntExist_thenReturnDefaultValueAndCache(String key) {
            when(propSource.getConfigValue(any())).thenReturn(Optional.empty());

            assertSoftly(a -> {
                a.assertThat(sut.get(key, true)).describedAs("First get").isEqualTo(true);
                a.assertThat(sut.get(key, false)).describedAs("Second get").isEqualTo(false);
            });
            verify(propSource, times(1)).getConfigValue(key);
        }
    }

    @Nested
    class WhenGettingPropertiesStartingWithTest {
        @Test
        void thenGetAllKeysWithValues() {
            when(propSource.getConfigKeys()).thenReturn(Set.of("prefix_a", "prefix_b", "nonprefix_c"));
            when(propSource.getConfigValue(anyString())).thenAnswer(a -> Optional.of("value:" + a.getArgument(0).toString()));

            assertThat(sut.getStartingWith("prefix_")).containsExactly(
                    entry("prefix_a", "value:prefix_a"),
                    entry("prefix_b", "value:prefix_b"));

            verify(propSource, times(1)).getConfigKeys();
            verify(propSource, times(2)).getConfigValue(anyString());
        }
    }
}
