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

package de.corelogics.mediaview.service.playback.prefetched.downloader;

import de.corelogics.mediaview.config.MainConfiguration;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClipDownloadConnectionTest {
    @Mock(answer = Answers.RETURNS_MOCKS)
    private OkHttpClient httpClient;

    @Mock
    private MainConfiguration mainConfiguration;

    @Mock
    private ClipDownloader clipDownloader;

    private String connectionId = "my-connection-id";

    private ClipDownloadConnection sut;

    @BeforeEach
    void createSut() {
        this.sut = new ClipDownloadConnection(
            clipDownloader,
            mainConfiguration,
            httpClient,
            connectionId);
    }

    @Test
    void givenNoErrors_whenRunning_thenDownloadAndForwardPackages() {
        final List<Optional<ClipChunk>> chunks = List.of(
            Optional.of(new ClipChunk(2, 20, 25)),
            Optional.of(new ClipChunk(8, 80, 85)),
            Optional.of(new ClipChunk(5, 50, 55)),
            Optional.empty());
        when(clipDownloader.nextChunk(connectionId))
            .thenReturn(chunks.get(0), chunks.get(1), chunks.get(2), chunks.get(3));
        final Call[] responses = {
            mockCallWithResponse(true, 200, "resp-1".getBytes(StandardCharsets.UTF_8)),
            mockCallWithResponse(true, 200, "resp-2".getBytes(StandardCharsets.UTF_8)),
            mockCallWithResponse(true, 200, "resp-3".getBytes(StandardCharsets.UTF_8))
        };
        when(httpClient.newCall(any())).thenReturn(responses[0], responses[1], responses[2]);
        when(clipDownloader.getUrl()).thenReturn("http://the.url.test/nowhere");

        assertThatNoException().isThrownBy(sut::run);

        val chunkCaptor = ArgumentCaptor.forClass(ClipChunk.class);
        val bytesCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(clipDownloader, times(4)).nextChunk(connectionId);
        verify(clipDownloader, times(3)).onChunkReceived(
            eq(connectionId),
            chunkCaptor.capture(),
            bytesCaptor.capture(),
            anyLong());
        verify(clipDownloader, times(1)).onConnectionTerminated(connectionId);
        verifyNoMoreInteractions(clipDownloader);
        verify(httpClient, times(3)).newCall(any());
        assertSoftly(a -> {
            a.assertThat(chunkCaptor.getAllValues()).extracting(ClipChunk::chunkNumber).containsExactly(2, 8, 5);
            a.assertThat(bytesCaptor.getAllValues()).extracting(b -> new String(b, StandardCharsets.UTF_8))
                .containsExactly("resp-1", "resp-2", "resp-3");
        });
    }

    @Test
    void givenIoExceptionWhileDownloading_whenDownloading_thenReportErrorAndStop() {
        val chunk = new ClipChunk(1, 1, 10);
        when(clipDownloader.nextChunk(connectionId)).thenReturn(
            Optional.of(chunk),
            Optional.empty());
        val resp = mockCallWithResponse(false, 404, new byte[0]);
        when(httpClient.newCall(any())).thenReturn(resp);
        when(clipDownloader.getUrl()).thenReturn("http://the.url.test/nowhere");

        assertThatNoException().isThrownBy(sut::run);

        verify(clipDownloader, times(2)).nextChunk(connectionId);
        verify(clipDownloader, times(1)).onChunkError(
            eq(connectionId),
            same(chunk),
            isA(IOException.class));
        verify(clipDownloader, times(1)).onConnectionTerminated(connectionId);
        verifyNoMoreInteractions(clipDownloader);
        verify(httpClient, times(1)).newCall(any());
    }

    @Test
    void givenStopped_whenDownloading_thenOnlyReportTerminated() {
        sut.close();
        assertThatNoException().isThrownBy(sut::run);
        verify(clipDownloader, times(1)).onConnectionTerminated(connectionId);
        verifyNoMoreInteractions(clipDownloader, httpClient);
    }

    @SneakyThrows
    private Call mockCallWithResponse(boolean success, int code, byte[] bytes) {
        val resp = mock(Response.class);
        when(resp.isSuccessful()).thenReturn(success);
        if (success) {
            val body = mock(okhttp3.ResponseBody.class);
            when(body.bytes()).thenReturn(bytes);
            when(resp.body()).thenReturn(body);
        }
        val call = mock(Call.class);
        when(call.execute()).thenReturn(resp);
        return call;
    }

}
