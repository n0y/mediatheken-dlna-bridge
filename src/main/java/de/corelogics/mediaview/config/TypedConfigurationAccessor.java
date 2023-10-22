/*
 * MIT License
 *
 * Copyright (c) 2020-2021 Mediatheken DLNA Bridge Authors.
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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

class TypedConfigurationAccessor {
    private final StringPropertySource propertiesSource;

    private final Map<String, Optional<String>> stringPropertiesCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<Integer>> intPropertiesCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<Boolean>> boolPropertiesCache = new ConcurrentHashMap<>();

    public TypedConfigurationAccessor(StringPropertySource propertiesSource) {
        this.propertiesSource = propertiesSource;
    }

    public synchronized String get(String key, String defaultValue) {
        var value = stringPropertiesCache.get(key);
        if (null == value) {
            value = this.propertiesSource.getConfigValue(key);
            stringPropertiesCache.put(key, value);
        }
        return value.orElse(defaultValue);
    }

    public int get(String key, int defaultValue) {
        var value = intPropertiesCache.get(key);
        if (null == value) {
            value = ofNullable(get(key, null)).map(Integer::parseInt);
            intPropertiesCache.put(key, value);
        }
        return value.orElse(defaultValue);
    }

    public boolean get(String key, boolean defaultValue) {
        var cached = boolPropertiesCache.get(key);
        if (null == cached) {
            cached = ofNullable(get(key, null)).map(Boolean::valueOf);
            boolPropertiesCache.put(key, cached);
        }
        return cached.orElse(defaultValue);
    }

    public Map<String, String> getStartingWith(String startingWith) {
        return propertiesSource.getConfigKeys().stream()
            .filter(s -> s.startsWith(startingWith))
            .collect(Collectors.toMap(
                Function.identity(),
                key -> this.get(key, "")));
    }
}
