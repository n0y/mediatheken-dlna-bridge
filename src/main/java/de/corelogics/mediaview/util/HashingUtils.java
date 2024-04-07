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

package de.corelogics.mediaview.util;

import io.whitfin.siphash.SipHasher;
import lombok.val;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class HashingUtils {
    // test vector from siphash24.c at https://www.131002.net/siphash/siphash24.c
    private static final byte[] SIP42_KEY_BYTES = ByteBuffer
        .allocate(16)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putLong(0x0706050403020100L)
        .putLong(0x0f0e0d0c0b0a0908L)
        .array();

    public static String idHash(String... idStrings) {
        val hasher = SipHasher.init(SIP42_KEY_BYTES, 2, 4);
        Arrays.stream(idStrings)
            .map(s -> s.getBytes(StandardCharsets.UTF_8))
            .forEach(hasher::update);
        final var b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        b.putLong(hasher.digest());
        b.rewind();
        return Base64.getEncoder().withoutPadding().encodeToString(b.array());
    }
}
