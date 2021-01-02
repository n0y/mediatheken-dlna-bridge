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

package de.corelogics.mediaview.client.mediatheklist;

import de.corelogics.mediaview.client.mediatheklist.model.MediathekListeServer;
import de.corelogics.mediaview.config.ConfigurationModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

class MediathekListClientIntegrationTest {

    @Test
    void whenRequestingServerList_thenRetrieveAtLeastOneElement() throws IOException {
        var client = new MediathekListClient(new ConfigurationModule().getMainConfiguration(), HttpClient.newBuilder().build());
        assertThat(client.getMediathekListeMetadata().getServers()).hasAtLeastOneElementOfType(MediathekListeServer.class);
        System.out.println(client.getMediathekListeMetadata());

    }
}
