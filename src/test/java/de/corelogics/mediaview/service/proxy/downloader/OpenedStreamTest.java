package de.corelogics.mediaview.service.proxy.downloader;

import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class OpenedStreamTest {
    @Mock
    private InputStream inputStream;

    @AfterEach
    void assertStreamInteractions() {
        verifyNoMoreInteractions(inputStream);
    }

    @Test
    void whenUsing_thenCorrectValuesAreReturned() {
        val sut = new OpenedStream("content-type", 10_000L, inputStream);
        assertThat(sut).extracting(OpenedStream::getContentType, OpenedStream::getMaxSize, OpenedStream::getStream)
            .containsExactly(
                "content-type",
                10_000L,
                inputStream);
    }

    @Test
    void whenSwitchingStream_thenReturnNewStream() {
        val stream2 = new ByteArrayInputStream(new byte[0]);
        val sut = new OpenedStream("content-type", 10_000L, inputStream);

        sut.setStream(stream2);

        assertThat(sut).extracting(OpenedStream::getStream).isEqualTo(stream2);
    }

    @Test
    void whenClosing_thenStreamIsClosed() throws IOException {
        val sut = new OpenedStream("content-type", 10_000L, inputStream);
        assertThatNoException().isThrownBy(sut::close);
        verify(inputStream).close();
    }
}
