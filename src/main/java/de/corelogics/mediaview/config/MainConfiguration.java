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

import java.util.Optional;

import static java.util.Optional.ofNullable;

public class MainConfiguration {
    private final ConfigurationAccesor configAccessor;

    public MainConfiguration() {
        this.configAccessor = new ConfigurationAccesor();
    }

    public String displayName() {
        return configAccessor.get("DISPLAY_NAME", "Mediatheken");
    }

    public String mediathekViewListBaseUrl() {
        return configAccessor.get("MEDIATHEKVIEW_LIST_BASEURL", null);
    }

    public String cacheDir() {
        return configAccessor.get("CACHE_DIRECTORY", "./cache");
    }

    public int cacheSizeGb() {
        return configAccessor.get("CACHE_SIZE_GB", 10);
    }

    public Optional<String> dbLocation() {
        return ofNullable(configAccessor.get("DATABASE_LOCATION", null));
    }

    public int updateIntervalFullHours() {
        return configAccessor.get("UPDATEINTERVAL_FULL_HOURS", 24);
    }

    public String publicBaseUrl() {
        return configAccessor.get("PUBLIC_BASE_URL", null);
    }

    public int publicHttpPort() {
        return configAccessor.get("PUBLIC_HTTP_PORT", 8080);
    }

    public int cacheMaxParallelDownloads() {
        return configAccessor.get("CACHE_MAX_PARALLEL_DOWNLOADS", 4);
    }

    public int cacheParallelDownloadsPerVideo() {
        return configAccessor.get("CACHE_DOWNLODERS_PER_VIDEO", 2);
    }

    public boolean isPrefetchingEnabled() {
        return configAccessor.get("ENABLE_PREFETCHING", false);
    }
}
