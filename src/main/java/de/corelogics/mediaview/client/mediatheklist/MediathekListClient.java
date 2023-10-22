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

import de.corelogics.mediaview.client.mediatheklist.model.MediathekListeMetadata;
import de.corelogics.mediaview.client.mediatheklist.model.MediathekListeServer;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.util.HttpUtils;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.tukaani.xz.XZInputStream;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@AllArgsConstructor
public class MediathekListClient {
    @NonNull
    private final MainConfiguration mainConfiguration;

    @NonNull
    private final HttpClient httpClient;

    private void downloadToTempFile(@NonNull File tempFile) throws IOException {
        try {
            val serverList = getMediathekListeMetadata();
            for (val server : serverList.getServers()) {
                val request =
                    HttpUtils.enhanceRequest(
                            mainConfiguration,
                            HttpRequest.newBuilder().uri(URI.create(server.getUrl())))
                        .build();
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    try (val output = new FileOutputStream(tempFile)) {
                        IOUtils.copyLarge(response.body(), output);
                    }
                    return;
                }
            }

        } catch (final InterruptedException e) {
            throw new IOException(e);
        }
        throw new IOException("Could not open");
    }

    public InputStream openMediathekListeFull() throws IOException {
        val tempFile = File.createTempFile("full-list", ".xml.xz");
        try {
            downloadToTempFile(tempFile);
            return new FilterInputStream(new XZInputStream(new BufferedInputStream(new FileInputStream(tempFile)))) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        tempFile.delete();
                    }
                }
            };
        } catch (IOException | RuntimeException e) {
            tempFile.delete();
            throw e;
        }
    }

    MediathekListeMetadata getMediathekListeMetadata() throws IOException {
        try {
            val docBuilder = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder();

            val request = HttpUtils.enhanceRequest(
                    mainConfiguration,
                    HttpRequest.newBuilder().uri(
                        URI.create(mainConfiguration.mediathekViewListBaseUrl()).resolve("/akt.xml")))
                .build();
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            try {
                val doc = docBuilder.parse(new InputSource(new StringReader(response.body())));
                val servers = doc.getDocumentElement().getElementsByTagName("Server");
                return new MediathekListeMetadata(IntStream.range(0, servers.getLength())
                    .mapToObj(servers::item)
                    .filter(Element.class::isInstance)
                    .map(Element.class::cast)
                    .map(l -> new MediathekListeServer(
                        l.getElementsByTagName("URL").item(0).getTextContent(),
                        Integer.parseInt(l.getElementsByTagName("Prio").item(0).getTextContent())))
                    .sorted(Comparator.comparing(MediathekListeServer::getPrio))
                    .collect(Collectors.toList()));
            } catch (final RuntimeException e) {
                throw new IOException("Didn't understand received file format.", e);
            } catch (SAXException e) {
                throw new IOException("Illegal file format from server", e);
            }
        } catch (InterruptedException | ParserConfigurationException e) {
            throw new IOException(e);
        }
    }
}
