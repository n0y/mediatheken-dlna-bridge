/*
 * MIT License
 *
 * Copyright (c) 2020 corelogics.de
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
import okhttp3.ResponseBody;
import org.tukaani.xz.XZInputStream;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Url;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static java.util.Optional.ofNullable;

public interface MediathekListeClient {
    @GET("/akt.xml")
    Call<MediathekListeMetadata> getMediathekListeMetadata();

    @GET
    Call<ResponseBody> downloadFromUrl(@Url String fileUrl);

    default InputStream openMediathekListeFull() throws IOException {
        Response<MediathekListeMetadata> response = this.getMediathekListeMetadata().execute();
        if (response.body() == null) {
            throw new IOException("Could not load MediathekListe: " + response.code());
        }
        for (var server: response.body().getServers()) {
            var binaryResponse = this.downloadFromUrl(server.getUrl()).execute();
            if (binaryResponse.isSuccessful()) {
                return new XZInputStream(binaryResponse.body().byteStream());
            }
        }
        throw new IOException("Could not open");
    }
}
