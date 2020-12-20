package de.corelogics.mediaview.service.downloader;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.BitSet;

public class BitsetSerializerModule extends SimpleModule {
    static class BitserSerializer extends StdSerializer<BitSet> {
        BitserSerializer() {
            super(BitSet.class);
        }

        @Override
        public void serialize(BitSet bitSet, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeFieldName("size");
            jsonGenerator.writeNumber(bitSet.size());
            jsonGenerator.writeFieldName("bits");
            jsonGenerator.writeBinary(bitSet.toByteArray());
            jsonGenerator.writeEndObject();
        }
    }

    static class BitsetDeserializer extends StdDeserializer<BitSet> {
        BitsetDeserializer() {
            super(BitSet.class);
        }

        @Override
        public BitSet deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            var size = 0;
            byte[] bytes = null;
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                var name = jsonParser.currentName();
                if ("size".equals(name)) {
                    size = jsonParser.nextIntValue(0);
                } else if ("bits".equals(name)) {
                    jsonParser.nextToken();
                    bytes = jsonParser.getBinaryValue();
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

    public BitsetSerializerModule() {
        this.addSerializer(BitSet.class, new BitserSerializer());
        this.addDeserializer(BitSet.class, new BitsetDeserializer());
    }
}
