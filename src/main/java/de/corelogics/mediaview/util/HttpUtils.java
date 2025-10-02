/*
 * MIT License
 *
 * Copyright (c) 2020-2025 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.util;

import de.corelogics.mediaview.config.MainConfiguration;
import lombok.experimental.UtilityClass;
import okhttp3.Request;

import java.net.http.HttpRequest;
import java.util.function.BiConsumer;

import static java.lang.String.format;

@UtilityClass
public class HttpUtils {
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_RANGE = "Range";
    public static final String HEADER_APPLICATION = "Application";
    public static final String HEADER_APPLICATION_HOME = "Application-Home";
    public static final String HEADER_APPLICATION_NOTE = "Application-Note";
    public static final String HEADER_ACCEPT_RANGES = "Accept-Ranges";
    public static final String HEADER_CONTENT_LENGTH = "Content-Length";
    public static final String HEADER_CONTENT_RANGE = "Content-Range";

    public static Request.Builder enhanceRequest(MainConfiguration mainConfiguration, Request.Builder request) {
        addHeaders(mainConfiguration, request::header);
        return request;
    }

    public static HttpRequest.Builder enhanceRequest(MainConfiguration mainConfiguration, HttpRequest.Builder builder) {
        addHeaders(mainConfiguration, builder::header);
        return builder;
    }

    private static void addHeaders(MainConfiguration mainConfiguration, BiConsumer<String, String> headerConsumer) {
        // If this application overloads the CDN, maybe due to a defect, then make it easy for administrators to block traffic from it.
        if (mainConfiguration.isApplicationHeaderAdded()) {
            headerConsumer.accept(
                HEADER_APPLICATION,
                format("Mediatheken-DLNA-Bridge %s", mainConfiguration.getBuildVersion()));
            headerConsumer.accept(
                HEADER_APPLICATION_HOME,
                "https://github.com/n0y/mediatheken-dlna-bridge");
            headerConsumer.accept(
                HEADER_APPLICATION_NOTE,
                "If you block requests from this application, then please leave a defect ticket for us to fix problems. " +
                    "We kindly ask you to block only the affected application version if possible.");
        }
        // this is added to improve compatibility with the Akam** CDN implementation
        headerConsumer.accept("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:84.0) Gecko/20100101 Firefox/84.0");
    }
}
