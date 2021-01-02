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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class MainConfiguration {
    private final TypedConfigurationAccessor configAccessor;

    MainConfiguration(TypedConfigurationAccessor configAccessor) {
        this.configAccessor = configAccessor;
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

    public boolean isApplicationHeaderAdded() {
        return configAccessor.get("ADD_APPLICATION_HTTP_HEADERS", true);
    }

    public String getBuildVersion() {
        var version = configAccessor.get("BUILD_VERSION", "E-SNAPSHOT");
        if (version.equalsIgnoreCase("${project.version}")) {
            // no filtering is applied when using RUN in a IDE
            return "E-SNAPSHOT";
        }
        return version;
    }

    public List<Favourite> getFavourites() {
        return configAccessor
                .getStartingWith("FAVOURITE_")
                .entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .map(this::toFavourite)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<Favourite> toFavourite(String favConfig) {
        var showPattern = Pattern.compile("^show:([^:]+):(.+)$");
        var showMatcher = showPattern.matcher(favConfig);
        if (showMatcher.matches()) {
            return Optional.of(new FavouriteShow(showMatcher.group(1), showMatcher.group(2)));
        }
        return Optional.empty();
    }
}
