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

package de.corelogics.mediaview.service.proxy.downloader;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.BitSet;
import java.util.StringJoiner;

class ClipMetadata {
    private String contentType;
    private long size;
    private BitSet bitSet;
    private int numberOfChunks;

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setBitSet(BitSet bitSet) {
        this.bitSet = bitSet;
    }

    public BitSet getBitSet() {
        return bitSet;
    }

    public void setNumberOfChunks(int numberOfChunks) {
        this.numberOfChunks = numberOfChunks;
    }

    public int getNumberOfChunks() {
        return numberOfChunks;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ClipMetadata.class.getSimpleName() + "[", "]")
                .add("contentType='" + contentType + "'")
                .add("size=" + size)
                .add("bitSet=" + bitSet)
                .add("numberOfChunks=" + numberOfChunks)
                .toString();
    }

    void writeTo(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        generator.writeFieldName("contentType");
        generator.writeString(contentType);
        generator.writeFieldName("size");
        generator.writeNumber(size);
        generator.writeFieldName("numberOfChunks");
        generator.writeNumber(numberOfChunks);

        generator.writeFieldName("bitSet");
        generator.writeStartObject();
        generator.writeFieldName("size");
        generator.writeNumber(bitSet.size());
        generator.writeFieldName("bits");
        generator.writeBinary(bitSet.toByteArray());
        generator.writeEndObject();

        generator.writeEndObject();
    }

    static ClipMetadata readFrom(JsonParser parser) throws IOException {
        var m = new ClipMetadata();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();
            if (null != name) {
                switch (name) {
                    case "contentType":
                        parser.nextToken();
                        m.setContentType(parser.getText());
                        break;
                    case "size":
                        parser.nextToken();
                        m.setSize(parser.getLongValue());
                        break;
                    case "numberOfChunks":
                        parser.nextToken();
                        m.setNumberOfChunks(parser.getIntValue());
                        break;
                    case "bitSet":
                        m.setBitSet(readBitsetFrom(parser));
                        break;
                }
            }
        }
        return m;
    }

    private static BitSet readBitsetFrom(JsonParser parser) throws IOException {
        var size = 0;
        byte[] bytes = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var name = parser.currentName();
            if ("size".equals(name)) {
                size = parser.nextIntValue(0);
            } else if ("bits".equals(name)) {
                parser.nextToken();
                bytes = parser.getBinaryValue();
            }
        }
        if (size == 0 || null == bytes) {
            throw new IOException("Missing some fields in bitset");
        }
        var b = new BitSet(size);
        var n = BitSet.valueOf(bytes);
        b.or(n);
        return b;
    }
}
