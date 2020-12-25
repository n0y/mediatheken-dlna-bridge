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

package de.corelogics.mediaview.client.mediathekview;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.util.Optional.ofNullable;

class ClipIterator implements Iterator<ClipEntry> {
    private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private final InputStream in;
    private FilmlisteMetaData metaData = null;
    private final JsonParser jParser;
    private final List<String> fields = new ArrayList<>();
    private Optional<ClipEntry> currentEntry = Optional.empty();
    private final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss");

    ClipIterator(InputStream in) throws IOException {
        this.in = in;
        this.jParser = JSON_FACTORY.createParser(in);

        for (var token = jParser.nextToken(); null != token && currentEntry.isEmpty(); token = jParser.nextToken()) {
            if (token == JsonToken.FIELD_NAME) {
                if ("Filmliste".equals(jParser.currentName())) {
                    if (null == metaData) {
                        metaData = parseMetaData();
                    } else if (fields.isEmpty()) {
                        fields.addAll(parseFieldList());
                    }
                } else if ("X".equals(jParser.currentName()) && !fields.isEmpty()) {
                    parseEntry(fields).map(Optional::of).ifPresent(o -> currentEntry = o);
                }
            }
        }
        if (currentEntry.isEmpty()) {
            in.close();
        }
    }

    public FilmlisteMetaData getMetaData() {
        return this.metaData;
    }

    @Override
    public boolean hasNext() {
        return currentEntry.isPresent();
    }

    @Override
    public ClipEntry next() {
        var current = currentEntry.orElseThrow(NoSuchElementException::new);
        readNextEntry();
        return current;
    }

    private void readNextEntry() {
        try {
            for (var token = jParser.nextToken(); null != token; token = jParser.nextToken()) {
                if ("X".equals(jParser.currentName())) {
                    var nextEntry = parseEntry(fields);
                    if (nextEntry.isPresent()) {
                        this.currentEntry = nextEntry;
                        return;
                    }
                }
            }
            currentEntry = Optional.empty();
            in.close();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private FilmlisteMetaData parseMetaData() throws IOException {
        var it = parseFieldList().iterator();
        return new FilmlisteMetaData(
                it.hasNext() ? it.next() : "",
                it.hasNext() ? it.next() : "",
                it.hasNext() ? it.next() : "",
                it.hasNext() ? it.next() : "",
                it.hasNext() ? it.next() : "");
    }

    private List<String> parseFieldList() throws IOException {
        var fields = new ArrayList<String>();
        for (var token = jParser.nextToken(); null != token && token != JsonToken.END_ARRAY; token = jParser.nextToken()) {
            if (token == JsonToken.VALUE_STRING) {
                fields.add(jParser.getValueAsString());
            }
        }
        return fields;
    }

    private Optional<ClipEntry> parseEntry(List<String> fieldList) throws IOException {
        var fields = new ArrayList<String>();
        for (var token = jParser.nextToken(); null != token && token != JsonToken.END_ARRAY; token = jParser.nextToken()) {
            if (token == JsonToken.VALUE_STRING) {
                var valueAsString = jParser.getValueAsString().trim();
                fields.add(valueAsString);
            }
        }
        Map<String, String> res = new HashMap<>(fieldList.size());
        for (int i = 0; i < fieldList.size(); i++) {
            var value = i < fields.size() ? fields.get(i) : "";
            if (!value.isBlank()) {
                res.put(fieldList.get(i), value);
            }
        }
        var url = res.getOrDefault("Url", "");
        try {
            var date = ofNullable(res.get("Datum"))
                    .orElseGet(() ->
                            dateFormat.format(currentEntry
                                    .map(ClipEntry::getBroadcastedAt)
                                    .orElseGet(() -> LocalDateTime.now().atZone(ZONE_BERLIN))));
            var time = ofNullable(res.get("Zeit"))
                    .orElseGet(() ->
                            timeFormat.format(currentEntry
                                    .map(ClipEntry::getBroadcastedAt)
                                    .orElseGet(() -> LocalDateTime.now().atZone(ZONE_BERLIN))));
            var broadcastTime = LocalDateTime.from(dateTimeFormat.parse(date + " " + time)).atZone(ZONE_BERLIN);
            return Optional.of(new ClipEntry(
                    ofNullable(res.get("Sender")).orElseGet(() -> currentEntry.map(ClipEntry::getChannelName).orElse("")),
                    ofNullable(res.get("Thema")).map(this::cleanString).orElseGet(() -> currentEntry.map(ClipEntry::getContainedIn).orElse("")),
                    broadcastTime,
                    ofNullable(res.get("Titel")).map(this::cleanString).orElseGet(() -> currentEntry.map(ClipEntry::getTitle).orElse("")),
                    res.getOrDefault("Dauer", ""), parseLong(res.getOrDefault("Größe [MB]", "0")) * 1024 * 1024,
                    url,
                    patchUrl(url, res.getOrDefault("Url HD", ""))
            ));
        } catch (final RuntimeException e) {
            System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage() + "\n" + res);
        }
        return Optional.empty();
    }

    private String cleanString(String in) {
        var cleaned = in.replaceAll("[\"”]", "").replaceAll("©.*", "").replaceFirst("^[^A-Za-z0-9]", "#").trim();
        return cleaned;
    }

    private long parseLong(String stringValue) {
        if (null != stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException e) {
                System.out.println("NFE: " + stringValue);
            }
        }
        return 0L;
    }

    private String patchUrl(String url, String urlPatch) {
        var pipeIndex = urlPatch.indexOf('|');
        if (pipeIndex < 1) {
            return urlPatch;
        }
        var location = Integer.parseInt(urlPatch.substring(0, pipeIndex));
        var patch = urlPatch.substring(pipeIndex + 1);

        if (location >= url.length()) {
            return url;
        }
        return url.substring(0, location) + patch;

    }
}
