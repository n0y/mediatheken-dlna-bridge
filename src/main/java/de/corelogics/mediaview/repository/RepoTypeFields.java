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

package de.corelogics.mediaview.repository;

import java.util.Locale;

public interface RepoTypeFields {
    String name();

    boolean isTerm();

    boolean isSort();

    default String value() {
        return this.name().toLowerCase(Locale.US);
    }

    default String value(String val) {
        return val;
    }

    default String sorted() {
        return STR."\{this.value()}$$sorted";
    }

    default String sorted(String val) {
        return val.toLowerCase(Locale.GERMANY);
    }

    default String term() {
        return STR."\{this.value()}$$term";
    }

    default String term(String val) {
        return val;
    }

    default String termLower() {
        return STR."\{this.value()}$$lowerterm";
    }

    default String termLower(String val) {
        return val.toLowerCase(Locale.GERMANY);
    }

    default String facet() {
        return STR."\{this.value()}$$facet";
    }

    default String facet(String val) {
        return val;
    }
}
