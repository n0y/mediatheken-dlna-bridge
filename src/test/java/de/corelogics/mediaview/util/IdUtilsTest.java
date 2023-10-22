package de.corelogics.mediaview.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.api.Assertions.*;

class IdUtilsTest {

    @ParameterizedTest
    @CsvSource({
        "defaultClipId,ZGVmYXVsdENsaXBJZA",
        "with/url:#data,d2l0aC91cmw6I2RhdGE",
        "äüö,w6TDvMO2"
    })
    void whenEncodingAndDecoding_thenCorrectValuesAreCreated(String value, String encodedValue) {
        assertSoftly(a -> {
            a.assertThat(IdUtils.encodeId(value)).isEqualTo(encodedValue);
            a.assertThat(IdUtils.decodeId(encodedValue)).isEqualTo(value);
        });

    }
}
