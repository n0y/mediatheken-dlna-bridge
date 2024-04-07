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

package de.corelogics.mediaview.config;

import lombok.val;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

class PropertiesConfigurationSource implements StringPropertySource {
    private final Properties properties;

    PropertiesConfigurationSource(File propertiesFile) throws IOException {
        this.properties = new Properties();
        if (propertiesFile.exists()) {
            loadFromUrl(propertiesFile.toURI().toURL());
        }
    }

    PropertiesConfigurationSource(URL url) throws IOException {
        this.properties = new Properties();
        loadFromUrl(url);
    }

    private void loadFromUrl(URL url) throws IOException {
        try (val in = url.openStream()) {
            properties.load(in);
        }
    }

    public PropertiesConfigurationSource(Properties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<String> getConfigValue(String key) {
        return Optional.ofNullable(properties.getProperty(key, null));
    }

    @Override
    public Set<String> getConfigKeys() {
        return properties.keySet().stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .collect(Collectors.toSet());
    }
}
