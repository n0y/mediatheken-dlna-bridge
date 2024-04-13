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

package de.corelogics.mediaview.service.playback.directfromsource;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import de.corelogics.mediaview.service.playback.directfromsource.DirectDownloadClipContentUrlGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectDownloadClipContentUrlGeneratorTest {
    private final DirectDownloadClipContentUrlGenerator sut = new DirectDownloadClipContentUrlGenerator();
    @Mock
    private ClipEntry clipEntry;

    @Test
    void givenNoAddressPresent_thenReturnBestUrl() {
        when(clipEntry.getBestUrl()).thenReturn("clips-best-url");
        assertThat(sut.createLinkTo(clipEntry, null)).isEqualTo("clips-best-url");
    }

    @Test
    void givenAddressPresent_thenStillReturnBestUrl() throws UnknownHostException {
        when(clipEntry.getBestUrl()).thenReturn("clips-best-url");
        assertThat(sut.createLinkTo(clipEntry, InetAddress.getLocalHost())).isEqualTo("clips-best-url");
    }
}
