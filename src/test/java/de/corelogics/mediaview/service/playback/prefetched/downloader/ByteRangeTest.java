package de.corelogics.mediaview.service.playback.prefetched.downloader;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ByteRangeTest {
    @Test
    void givenNoHeaderPresent_thenReturnUnboundedRange() {
        val range = new ByteRange(null);
        assertSoftly(a -> {
            a.assertThat(range.isPartial()).isFalse();
            a.assertThat(range.getFirstPosition()).isEqualTo(0L);
            a.assertThat(range.getLastPosition()).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 200, 404})
    void givenOpenEndedRange_thenReturnWithoutUpper(long firstPosition) {
        val range = new ByteRange("bytes=" + firstPosition + "-");
        assertSoftly(a -> {
            a.assertThat(range.isPartial()).isTrue();
            a.assertThat(range.getFirstPosition()).isEqualTo(firstPosition);
            a.assertThat(range.getLastPosition()).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 200, 404})
    void givenOpenStartedRange_thenReturnWithZeroAsLowerLimit(long lastPosition) {
        val range = new ByteRange("bytes=-" + lastPosition);
        assertSoftly(a -> {
            a.assertThat(range.isPartial()).isTrue();
            a.assertThat(range.getFirstPosition()).isEqualTo(0);
            a.assertThat(range.getLastPosition()).isPresent().get().isEqualTo(lastPosition);
        });
    }

    @Test
    void givenMultipleRanges_thenOnlyFirstRangeIsParsed() {
        val range = new ByteRange("bytes=100-200, 400-500, 600-800");
        assertSoftly(a -> {
            a.assertThat(range.isPartial()).isTrue();
            a.assertThat(range.getFirstPosition()).isEqualTo(100L);
            a.assertThat(range.getLastPosition()).isPresent().get().isEqualTo(200L);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {120, 2000, 4040})
    void whenConstructingFromValues_thenInitializeWithPartial(long lastPosition) {
        val range = new ByteRange(lastPosition - 100, lastPosition);
        assertSoftly(a -> {
            a.assertThat(range.getFirstPosition()).isEqualTo(lastPosition - 100);
            a.assertThat(range.getLastPosition()).isPresent().get().isEqualTo(lastPosition);
            a.assertThat(range.isPartial()).isTrue();
        });
    }

    @Test
    void whenToString_thenAssertPresent() {
        assertThat(new ByteRange(1, 100).toString()).isNotNull();
    }
}
