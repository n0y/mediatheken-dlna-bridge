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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LayeredPropertySourceTest {
    private LayeredPropertySource sut;

    @Mock
    private StringPropertySource sourceOne;

    @Mock
    private StringPropertySource sourceTwo;

    @BeforeEach
    void constructSut() {
        sut = new LayeredPropertySource(sourceOne, sourceTwo);
    }

    @AfterEach
    void verifyThatOnlyExpectedInteractionsOccurred() {
        verifyNoMoreInteractions(sourceOne, sourceTwo);
    }

    @Nested
    class WhenGettingConfigValuesTests {
        @ParameterizedTest
        @ValueSource(strings = {"test", "key with space", "key with ä"})
        void givenFirstHasKey_thenReturnValueOfFirst(String key) {
            var expectedValue = "value:" + key;
            when(sourceOne.getConfigValue(key)).thenReturn(Optional.of(expectedValue));

            assertThat(sut.getConfigValue(key)).isPresent().get().isEqualTo(expectedValue);

            verify(sourceOne).getConfigValue(key);
        }

        @ParameterizedTest
        @ValueSource(strings = {"test", "key with space", "key with ä"})
        void givenSecondHasKey_thenReturnValueOfSecond(String key) {
            var expectedValue = "value:" + key;
            when(sourceOne.getConfigValue(key)).thenReturn(Optional.empty());
            when(sourceTwo.getConfigValue(key)).thenReturn(Optional.of(expectedValue));

            assertThat(sut.getConfigValue(key)).isPresent().get().isEqualTo(expectedValue);

            verify(sourceOne).getConfigValue(key);
            verify(sourceTwo).getConfigValue(key);
        }

        @ParameterizedTest
        @ValueSource(strings = {"test", "key with space", "key with ä"})
        void givenNoneHasKey_thenReturnEmptyValue(String key) {
            when(sourceOne.getConfigValue(any())).thenReturn(Optional.empty());
            when(sourceTwo.getConfigValue(any())).thenReturn(Optional.empty());

            assertThat(sut.getConfigValue(key)).isEmpty();

            verify(sourceOne).getConfigValue(key);
            verify(sourceTwo).getConfigValue(key);
        }
    }

    @Nested
    class WhenGettingAllConfigKeysTests {
        @Test
        void givenBothContainKeys_thenReturnUnionOfKeys() {
            when(sourceOne.getConfigKeys()).thenReturn(Set.of("a", "b", "c"));
            when(sourceTwo.getConfigKeys()).thenReturn(Set.of("c", "d", "e"));

            assertThat(sut.getConfigKeys()).containsExactly("a", "b", "c", "d", "e");

            verify(sourceOne).getConfigKeys();
            verify(sourceTwo).getConfigKeys();
        }
    }
}
