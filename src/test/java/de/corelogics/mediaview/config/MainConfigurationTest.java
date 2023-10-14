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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MainConfigurationTest {
    @InjectMocks
    private MainConfiguration sut;

    @Mock
    private TypedConfigurationAccessor configAccessor;

    @AfterEach
    void assertNoUnwantedInvocations() {
        verifyNoMoreInteractions(configAccessor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"value1", "value2"})
    void whenGetDisplayName_thenReturnValue(String value) {
        when(configAccessor.get("DISPLAY_NAME", "Mediatheken")).thenReturn(value);
        assertThat(sut.displayName()).isEqualTo(value);
        verify(configAccessor).get("DISPLAY_NAME", "Mediatheken");
    }

    @ParameterizedTest
    @ValueSource(strings = {"value1", "value2"})
    void whenGetMediathekViewListBaseUrl_thenReturnValue(String value) {
        when(configAccessor.get("MEDIATHEKVIEW_LIST_BASEURL", null)).thenReturn(value);
        assertThat(sut.mediathekViewListBaseUrl()).isEqualTo(value);
        verify(configAccessor).get("MEDIATHEKVIEW_LIST_BASEURL", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"value1", "value2"})
    void whenGetCacheDir_thenReturnValue(String value) {
        when(configAccessor.get("CACHE_DIRECTORY", "./cache")).thenReturn(value);
        assertThat(sut.cacheDir()).isEqualTo(value);
        verify(configAccessor).get("CACHE_DIRECTORY", "./cache");
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1000})
    void whenGetCacheSizeGb_thenReturnValue(int value) {
        when(configAccessor.get("CACHE_SIZE_GB", 10)).thenReturn(value);
        assertThat(sut.cacheSizeGb()).isEqualTo(value);
        verify(configAccessor).get("CACHE_SIZE_GB", 10);
    }

    @Nested
    class WhenGetDbLocationTest {
        @ParameterizedTest
        @ValueSource(strings = {"value1", "value2"})
        void givenLocationIsSet_thenReturnValue(String value) {
            when(configAccessor.get("DATABASE_LOCATION", null)).thenReturn(value);
            assertThat(sut.dbLocation()).isPresent().get().isEqualTo(value);
            verify(configAccessor).get("DATABASE_LOCATION", null);
        }

        @Test
        void givenLocationIsUnset_thenReturnValue() {
            when(configAccessor.get("DATABASE_LOCATION", null)).thenReturn(null);
            assertThat(sut.dbLocation()).isEmpty();
            verify(configAccessor).get("DATABASE_LOCATION", null);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1000})
    void whenGetUpdateIntervalFullHours_thenReturnValue(int value) {
        when(configAccessor.get("UPDATEINTERVAL_FULL_HOURS", 24)).thenReturn(value);
        assertThat(sut.updateIntervalFullHours()).isEqualTo(value);
        verify(configAccessor).get("UPDATEINTERVAL_FULL_HOURS", 24);
    }

    @ParameterizedTest
    @ValueSource(strings = {"value1", "value2"})
    void whenGetPublicBaseUrl_thenReturnValue(String value) {
        when(configAccessor.get("PUBLIC_BASE_URL", null)).thenReturn(value);
        assertThat(sut.publicBaseUrl()).isEqualTo(value);
        verify(configAccessor).get("PUBLIC_BASE_URL", null);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1000})
    void whenGetPublicHttpPort_thenReturnValue(int value) {
        when(configAccessor.get("PUBLIC_HTTP_PORT", 9300)).thenReturn(value);
        assertThat(sut.publicHttpPort()).isEqualTo(value);
        verify(configAccessor).get("PUBLIC_HTTP_PORT", 9300);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1000})
    void whenGetCacheMaxParallelDownloads_thenReturnValue(int value) {
        when(configAccessor.get("CACHE_MAX_PARALLEL_DOWNLOADS", 4)).thenReturn(value);
        assertThat(sut.cacheMaxParallelDownloads()).isEqualTo(value);
        verify(configAccessor).get("CACHE_MAX_PARALLEL_DOWNLOADS", 4);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1000})
    void whenGetCacheParallelDownloadsPerVideo_thenReturnValue(int value) {
        when(configAccessor.get("CACHE_DOWNLODERS_PER_VIDEO", 2)).thenReturn(value);
        assertThat(sut.cacheParallelDownloadsPerVideo()).isEqualTo(value);
        verify(configAccessor).get("CACHE_DOWNLODERS_PER_VIDEO", 2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void whenGetIsPrefetchingEnabled_thenReturnValue(boolean value) {
        when(configAccessor.get("ENABLE_PREFETCHING", false)).thenReturn(value);
        assertThat(sut.isPrefetchingEnabled()).isEqualTo(value);
        verify(configAccessor).get("ENABLE_PREFETCHING", false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void whenGetIsApplicationHeaderAdded_thenReturnValue(boolean value) {
        when(configAccessor.get("ADD_APPLICATION_HTTP_HEADERS", true)).thenReturn(value);
        assertThat(sut.isApplicationHeaderAdded()).isEqualTo(value);
        verify(configAccessor).get("ADD_APPLICATION_HTTP_HEADERS", true);
    }

    @Nested
    class WhenGetBuildVersionTest {
        @Test
        void givenIdeMode_thenReturnESnapshot() {
            // because in IDE mode, application.properties from classpath is not maven-filtered and returns the placeholder string
            when(configAccessor.get("BUILD_VERSION", "E-SNAPSHOT")).thenReturn("${project.version}");
            assertThat(sut.getBuildVersion()).isEqualTo("E-SNAPSHOT");
        }

        @ParameterizedTest
        @ValueSource(strings = {"value1", "value2"})
        void givenInProdMode_thenReturnReplacedBuildVersion(String value) {
            when(configAccessor.get("BUILD_VERSION", "E-SNAPSHOT")).thenReturn(value);
            assertThat(sut.getBuildVersion()).isEqualTo(value);
            verify(configAccessor).get("BUILD_VERSION", "E-SNAPSHOT");
        }
    }

    @Nested
    class WhenGettingFavouritesTest {
        @Test
        void givenThreeFavouriteShowsConfigured_thenReturnConfiguredShows() {
            when(configAccessor.getStartingWith("FAVOURITE_")).thenReturn(Map.of(
                    "FAVOURITE_1", "show:channel-a:ShowA",
                    "FAVOURITE_2", "show:channel-b:ShowB",
                    "FAVOURITE_3", "show:channel-c:ShowC"));
            assertThat(sut.getFavourites())
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(
                            new FavouriteShow("channel-a", "ShowA"),
                            new FavouriteShow("channel-b", "ShowB"),
                            new FavouriteShow("channel-c", "ShowC"));
        }

        @Test
        void givenFavouriteShows_whenUsingVisitor_thenCallVisit() {
            when(configAccessor.getStartingWith("FAVOURITE_")).thenReturn(Map.of(
                    "FAVOURITE_2", "show:channel-b:ShowB",
                    "FAVOURITE_3", "show:channel-c:ShowC"));
            assertThat(
                    sut.getFavourites().stream().map(f -> f.accept(
                            new FavouriteVisitor<String>() {
                                @Override
                                public String visitShow(FavouriteShow favouriteShow) {
                                    return favouriteShow.channel() + ":" + favouriteShow.title();
                                }
                            })))
                    .containsExactly("channel-b:ShowB", "channel-c:ShowC");
        }

        @Test
        void givenEntriesWithWrongFormat_thenOnlyReturnCorrectlyFormatted() {
            when(configAccessor.getStartingWith("FAVOURITE_")).thenReturn(Map.of(
                    "FAVOURITE_NO_SHOW_TITLE", "show:channel1:",
                    "FAVOURITE_WRONG_PREFIX", "clip:channel1:Title",
                    "FAVOURITE_NO_VALUE", "",
                    "FAVOURITE_CORRECT", "show:channel-a:ShowA"));
            assertThat(sut.getFavourites())
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(new FavouriteShow("channel-a", "ShowA"));
        }
    }
}
