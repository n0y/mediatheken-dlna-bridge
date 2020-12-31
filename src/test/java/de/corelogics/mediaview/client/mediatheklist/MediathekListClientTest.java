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

package de.corelogics.mediaview.client.mediatheklist;

import de.corelogics.mediaview.config.MainConfiguration;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediathekListClientTest {
    private static final String SOME_CONTENT_STRING = "This is a test string, xz-ed.";
    private static final String URI_CONTENT_FIRST = "https://first.server.test/liste.xz";
    private static final String URI_CONTENT_SECOND = "https://first.server.test/liste.xz";
    private static final String TEST_META_URI = "http://nowhere.test/akt.xml";

    @InjectMocks
    private MediathekListClient sut;

    @Mock
    private MainConfiguration mainConfiguration;

    @Mock
    private HttpClient httpClient;

    @BeforeEach
    void setupMetaUriInConfig() {
        when(mainConfiguration.mediathekViewListBaseUrl()).thenReturn(TEST_META_URI);
    }

    @AfterEach
    void verifyNoOtherCalls() {
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void givenFirstServerReturnsValidList_thenCorrectInputStreamIsReturned() throws IOException, InterruptedException {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        when(httpClient.send(any(), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);
        when(httpClient.send(any(), eq(HttpResponse.BodyHandlers.ofInputStream())))
                .thenAnswer(i -> createListeInputStream());
        when(mockResponse.body()).thenReturn(mvMetadataTwoServers());

        // test

        assertThat(IOUtils.toString(sut.openMediathekListeFull(), StandardCharsets.UTF_8))
                .isEqualTo(SOME_CONTENT_STRING);

        // verify

        var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        var bodyHandlerCaptor = ArgumentCaptor.forClass(HttpResponse.BodyHandler.class);
        verify(httpClient, times(2)).send(requestCaptor.capture(), bodyHandlerCaptor.capture());
        assertThat(requestCaptor.getAllValues()).extracting(HttpRequest::uri)
                .containsExactlyInAnyOrder(URI.create(TEST_META_URI), URI.create(URI_CONTENT_FIRST));
        assertThat(bodyHandlerCaptor.getAllValues()).containsExactly(
                HttpResponse.BodyHandlers.ofString(),
                HttpResponse.BodyHandlers.ofInputStream());
    }

    void givenFirstServerRespondsError_thenSecondServerIsTriedAndStreamIsReturned() throws IOException, InterruptedException {
        HttpResponse<String> mockMetadataResponse = mock(HttpResponse.class);
        when(mockMetadataResponse.body()).thenReturn(mvMetadataTwoServers());
        when(httpClient.send(any(), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockMetadataResponse);

        HttpResponse<InputStream> contentNotFoundResponse = mock(HttpResponse.class);
        when(contentNotFoundResponse.statusCode()).thenReturn(404);
        HttpResponse<InputStream> contentFoundResponse = mock(HttpResponse.class);
        when(contentFoundResponse.statusCode()).thenReturn(200);
        when(contentFoundResponse.body()).thenAnswer(ignored -> createListeInputStream());
        when(httpClient.send(any(), eq(HttpResponse.BodyHandlers.ofInputStream())))
                .thenAnswer(i -> {
                    var request = (HttpRequest) i.getArgument(0);
                    if (request.uri().equals(URI.create(URI_CONTENT_FIRST))) {
                        return contentNotFoundResponse;
                    }
                    return contentFoundResponse;
                });

        // test

        assertThat(IOUtils.toString(sut.openMediathekListeFull(), StandardCharsets.UTF_8))
                .isEqualTo(SOME_CONTENT_STRING);
        // verify

        var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        var bodyHandlerCaptor = ArgumentCaptor.forClass(HttpResponse.BodyHandler.class);
        verify(httpClient, times(3)).send(requestCaptor.capture(), bodyHandlerCaptor.capture());
        assertThat(requestCaptor.getAllValues()).extracting(HttpRequest::uri)
                .containsExactlyInAnyOrder(
                        URI.create(TEST_META_URI),
                        URI.create(URI_CONTENT_FIRST),
                        URI.create(URI_CONTENT_SECOND));
        assertThat(bodyHandlerCaptor.getAllValues()).containsExactly(
                HttpResponse.BodyHandlers.ofString(),
                HttpResponse.BodyHandlers.ofInputStream(),
                HttpResponse.BodyHandlers.ofInputStream());

    }

    @Test
    void givenNoServer_thenThrowException() throws IOException, InterruptedException {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(httpClient.send(any(), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);
        when(mockResponse.body()).thenReturn(mvMetadataNoServer());

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(sut::openMediathekListeFull)
                .withMessageContaining("Could not open");
    }

    @Test
    void givenMetaDataInterrupted_thenThrowException() throws IOException, InterruptedException {
        when(httpClient.send(any(), any())).thenThrow(InterruptedException.class);
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(sut::openMediathekListeFull);
    }

    @Test
    void givenContentInterrupted_thenThrowException() throws IOException, InterruptedException {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(mvMetadataTwoServers());
        when(httpClient.send(any(), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);
        when(httpClient.send(any(), eq(HttpResponse.BodyHandlers.ofInputStream())))
                .thenThrow(InterruptedException.class);

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(sut::openMediathekListeFull);
    }

    private HttpResponse<InputStream> createListeInputStream() throws IOException {
        HttpResponse<InputStream> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn(new ByteArrayInputStream(listeContentBytes()));
        return resp;
    }

    private byte[] listeContentBytes() throws IOException {
        var byteOut = new ByteArrayOutputStream();
        var out = new XZOutputStream(byteOut, new LZMA2Options(LZMA2Options.PRESET_MIN));
        out.write(SOME_CONTENT_STRING.getBytes(StandardCharsets.UTF_8));
        out.close();
        return byteOut.toByteArray();
    }

    private String mvMetadataTwoServers() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Mediathek>\n" +
                "    <Server>\n" +
                "        <URL>" + URI_CONTENT_FIRST + "</URL>\n" +
                "        <Prio>1</Prio>\n" +
                "    </Server>\n" +
                "    <Server>\n" +
                "        <URL>" + URI_CONTENT_SECOND + "</URL>\n" +
                "        <Prio>1</Prio>\n" +
                "    </Server>\n" +
                "</Mediathek>\n";
    }

    private String mvMetadataNoServer() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Mediathek>\n" +
                "</Mediathek>\n";
    }
}
