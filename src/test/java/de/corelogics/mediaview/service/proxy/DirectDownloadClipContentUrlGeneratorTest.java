package de.corelogics.mediaview.service.proxy;

import de.corelogics.mediaview.client.mediathekview.ClipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectDownloadClipContentUrlGeneratorTest {
    private final DirectDownloadClipContentUrlGenerator sut = new DirectDownloadClipContentUrlGenerator();
    @Mock
    private ClipEntry clipEntry;

    @Test
    void givenNoAddressPresent_thenReturnBestUrl() {
        when(clipEntry.getBestUrl()).thenReturn("clips-best-url");
        assertThat(sut.createLinkTo(clipEntry, null)).isEqualTo("clips-best-url");
    }

    @Test
    void givenAddressPresent_thenStillReturnBestUrl() throws UnknownHostException {
        when(clipEntry.getBestUrl()).thenReturn("clips-best-url");
        assertThat(sut.createLinkTo(clipEntry, InetAddress.getLocalHost())).isEqualTo("clips-best-url");
    }
}
