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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MapConfigurationSourceTest {
    @Nested
    class WhenRetrievingValueTests {
        @ParameterizedTest
        @ValueSource(strings = {"test", "key with space", "key with ä"})
        void givenValueIsInMap_thenReturnValue(String key) {
            var expectedValue = "value:" + key;
            assertThat(
                    new MapConfigurationSource(
                            Map.of(
                                    key, expectedValue))
                            .getConfigValue(key))
                    .isPresent()
                    .get().isEqualTo(expectedValue);
        }

        @ParameterizedTest
        @ValueSource(strings = {"test", "key with space", "key with ä"})
        void givenValueIsNotInMap_thenReturnEmptyValue(String key) {
            assertThat(
                    new MapConfigurationSource(Map.of())
                            .getConfigValue(key))
                    .isEmpty();
        }
    }

    @Nested
    class WhenRetrievingAllKeysTests {
        @Test
        void givenValuesInMap_thenKeysAreReturned() {
            assertThat(
                    new MapConfigurationSource(
                            Map.of("a", "value-a", "b", "value-b")).getConfigKeys())
                    .containsExactly("a", "b");
        }
    }
}
