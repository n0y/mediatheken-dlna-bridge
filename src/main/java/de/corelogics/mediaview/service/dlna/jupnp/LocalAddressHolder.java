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

package de.corelogics.mediaview.service.dlna.jupnp;

import lombok.experimental.UtilityClass;

import java.net.InetAddress;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@UtilityClass
public class LocalAddressHolder {
    private static final ThreadLocal<InetAddress> LOCAL_ADDRESS = new ThreadLocal<>();

    public static NoExceptionAutoCloseable memoizeLocalAddress(InetAddress localAddress) {
        LOCAL_ADDRESS.set(localAddress);
        return LOCAL_ADDRESS::remove;
    }

    @FunctionalInterface
    public interface NoExceptionAutoCloseable extends AutoCloseable {
        void close();
    }

    public static Optional<InetAddress> getMemoizedLocalAddress() {
        return ofNullable(LOCAL_ADDRESS.get());
    }
}
