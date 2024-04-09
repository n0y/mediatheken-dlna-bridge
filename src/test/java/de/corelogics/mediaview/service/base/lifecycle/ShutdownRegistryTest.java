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

package de.corelogics.mediaview.service.base.lifecycle;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class ShutdownRegistryTest {
    private ShutdownRegistry sut = new ShutdownRegistry();

    @Test
    void whenRegisteringTwoHooks_thenHooksAreExecuted() {
        val values = new HashSet<String>();
        sut.registerShutdown(() -> values.add("hook-1"));
        sut.registerShutdown(() -> values.add("hook-2"));
        sut.shutdown();
        assertThat(values).contains("hook-1", "hook-2");
    }

    @Test
    void givenFirstHookThrowsException_whenRegisteringTwoHooks_thenBothHooksAreExecuted() {
        val values = new HashSet<String>();
        sut.registerShutdown(() -> {
            values.add("failure-hook");
            throw new RuntimeException("Test-Exception");
        });
        sut.registerShutdown(() -> values.add("hook-justfine"));
        sut.shutdown();
        assertThat(values).contains("hook-justfine");
    }
}
