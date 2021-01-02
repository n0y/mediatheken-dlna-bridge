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
import org.tukaani.xz.XZInputStream;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MediathekListClient {
    private final MainConfiguration mainConfiguration;
    private final HttpClient httpClient;

    public MediathekListClient(MainConfiguration mainConfiguration, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.mainConfiguration = mainConfiguration;
    }

    public InputStream openMediathekListeFull() throws IOException {
        try {
            var serverList = getMediathekListeMetadata();
            for (var server : serverList.getServers()) {
                var request =
                        HttpUtils.enhanceRequest(
                                mainConfiguration,
                                HttpRequest.newBuilder().uri(URI.create(server.getUrl())))
                                .build();
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    return new XZInputStream(response.body());
                }
            }
        } catch (final InterruptedException e) {
            throw new IOException(e);
        }
        throw new IOException("Could not open");
    }

    MediathekListeMetadata getMediathekListeMetadata() throws IOException {
        try {
            var docBuilder = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder();

            var request = HttpUtils.enhanceRequest(
                    mainConfiguration,
                    HttpRequest.newBuilder().uri(
                            URI.create(mainConfiguration.mediathekViewListBaseUrl()).resolve("/akt.xml")))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            try {
                var doc = docBuilder.parse(new InputSource(new StringReader(response.body())));
                var servers = doc.getDocumentElement().getElementsByTagName("Server");
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
