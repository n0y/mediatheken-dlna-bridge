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

package de.corelogics.mediaview.repository.clip;

import de.corelogics.mediaview.config.MainConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LuceneDirectoryTest {
    @Mock
    private MainConfiguration config;

    @InjectMocks
    private LuceneDirectory sut;

    @Nested
    @DisplayName("when connecting")
    class WhenConnectingTests {
        @Nested
        @DisplayName("when calculating cache sizes")
        class WhenCalculatingCacheSizes {
            @Test
            void givenInOkRange_thenCalcSizeCorrectly() {
                sut.maxMemorySupplier = () -> 40_000_000L + 150_000_000L;
                assertThat(sut.calcCacheSize()).isEqualTo(40_000_000L);
            }

            @Test
            void givenBelowLimit_thenReturnLowerLimit() {
                sut.maxMemorySupplier = () -> 100_000_000L;
                assertThat(sut.calcCacheSize()).isEqualTo(16_000_000L);
            }

            @Test
            void givenAboveLimit_thenReturnUpperLimit() {
                sut.maxMemorySupplier = () -> 3_000_000_000L;
                assertThat(sut.calcCacheSize()).isEqualTo(100_000_000L);
            }
        }
    }
}
