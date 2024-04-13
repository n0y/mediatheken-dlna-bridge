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

package de.corelogics.mediaview.service.playback.prefetched;

import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.service.base.networking.WebServer;
import de.corelogics.mediaview.service.playback.prefetched.downloader.DownloadManager;
import de.corelogics.mediaview.service.repository.clip.ClipRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForwardingProxyServerTest {
    @Mock
    private ClipRepository clipRepository;

    @Mock
    private MainConfiguration mainConfiguration;

    @Mock
    private WebServer webServer;

    @Mock
    private DownloadManager downloadManager;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private PrefetchingProxy sut;

    @Test
    void whenInitializing_thenServletHandlerIsRegistered() {
        verify(webServer).addHandler(any());
    }

    @Nested
    class WhenHandlingHead {
        @Test
        void givenRealWebRequest_thenForwardToHandlerMethod() throws IOException {
            when(request.getPathInfo()).thenReturn(
                "/a/b/c/" + Base64.getEncoder().encodeToString("clipid".getBytes(StandardCharsets.UTF_8)));
            when(clipRepository.findClipById("clipid")).thenReturn(Optional.empty());
            sut.getServlet().doHead(request, response);
            verify(clipRepository).findClipById("clipid");
            verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
