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

package de.corelogics.mediaview.service.importer;

import de.corelogics.mediaview.client.mediatheklist.MediathekListClient;
import de.corelogics.mediaview.client.mediathekview.MediathekViewImporter;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.service.base.BaseServicesModule;
import de.corelogics.mediaview.service.repository.RepositoryModule;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.http.HttpClient;

@RequiredArgsConstructor
public class ImporterModule {
    private final MainConfiguration mainConfiguration;
    private final BaseServicesModule baseServicesModule;
    private final RepositoryModule repositoryModule;

    @Getter(lazy = true)
    private final ImporterService importerService =
        new ImporterService(
            mainConfiguration,
            baseServicesModule.getBaseThreading(),
            baseServicesModule.getShutdownRegistry(),
            new MediathekListClient(
                mainConfiguration,
                baseServicesModule.getShutdownRegistry(),
                HttpClient.newBuilder().build()),
            new MediathekViewImporter(),
            repositoryModule.getClipRepository());

}
