/*
 * MIT License
 *
 * Copyright (c) 2020 Mediatheken DLNA Bridge Authors.
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

import de.corelogics.mediaview.Main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Optional.ofNullable;

class ConfigurationAccesor {
    private Properties classpathProperties = new Properties();
    private Properties fileSystemProperties = new Properties();
    private Map<String, Optional<String>> stringPropertiesCache = new ConcurrentHashMap<>();
    private Map<String, Optional<Integer>> intPropertiesCache = new ConcurrentHashMap<>();
    private Map<String, Optional<Boolean>> boolPropertiesCache = new ConcurrentHashMap<>();

    public ConfigurationAccesor() {
        try {
            try (var in = Main.class.getResourceAsStream("/application.properties")) {
                classpathProperties.load(in);
            }
            var fsPropertiesFile = new File("./config/application.properties");
            if (fsPropertiesFile.isFile()) {
                try (var in = new FileInputStream(fsPropertiesFile)) {
                    fileSystemProperties.load(in);
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized String get(String key, String defaultValue) {
        var cached = stringPropertiesCache.get(key);
        if (null == cached) {
            var value = System.getProperty(key);
            if (null == value) {
                value = System.getenv().get(key);
            }
            if (null == value) {
                value = fileSystemProperties.getProperty(key);
            }
            if (null == value) {
                value = classpathProperties.getProperty(key);
            }
            if (null == value) {
                value = defaultValue;
            }
            cached = ofNullable(value);
            stringPropertiesCache.put(key, cached);
        }
        return cached.orElse(null);
    }

    public int get(String key, int defaultValue) {
        var cached = intPropertiesCache.get(key);
        if (null == cached) {
            cached = ofNullable(get(key, null)).map(Integer::parseInt);
            intPropertiesCache.put(key, cached);
        }
        return cached.orElse(defaultValue);
    }

    public boolean get(String key, boolean defaultValue) {
        var cached = boolPropertiesCache.get(key);
        if (null == cached) {
            cached = ofNullable(get(key, null)).map(Boolean::valueOf);
            boolPropertiesCache.put(key, cached);
        }
        return cached.orElse(defaultValue);
    }
}
