/*
 * MIT License
 *
 * Copyright (c) 2020-2024 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.service.base.networking;

import de.corelogics.mediaview.service.base.lifecycle.ShutdownRegistry;
import lombok.val;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebServerTest {
    @Mock
    private Server server;

    @Spy
    private ShutdownRegistry shutdownRegistry = new ShutdownRegistry();

    @InjectMocks
    private WebServer sut;

    @Test
    void whenGettingServer_thenServerIsReturned() {
        assertThat(sut.getServer()).isSameAs(server);
    }

    @Nested
    class WhenGettingContextHandlerCollection {
        @Test
        void givenNoHandlerCollectionSet_thenCreateNewOne() {
            assertThat(sut.getHandlerCollection()).isInstanceOf(ContextHandlerCollection.class);
            verify(server).setHandler(isA(ContextHandlerCollection.class));
        }

        @Test
        void givenNoHandlerCollectionSet_thenOverwriteWithCorrectType() {
            val otherClassInstance = mock(Handler.Collection.class);
            when(server.getHandler()).thenReturn(otherClassInstance);

            assertThat(sut.getHandlerCollection())
                .isNotSameAs(otherClassInstance)
                .isInstanceOf(ContextHandlerCollection.class);
            verify(server).setHandler(isA(ContextHandlerCollection.class));

        }

        @Test
        void givenHandlerCollectionAlreadySet_thenReturnThisObject() {
            val correctInstance = mock(ContextHandlerCollection.class);
            when(server.getHandler()).thenReturn(correctInstance);
            assertThat(sut.getHandlerCollection())
                .isSameAs(correctInstance);
            verify(server).getHandler();
            verifyNoMoreInteractions(server);
        }
    }

    @Nested
    class WhenStartup {
        @Test
        void givenStartedTwice_thenOnlyStartupOnce() throws Exception {
            when(server.getConnectors()).thenReturn(new Connector[0]);
            sut.startup();
            sut.startup();
            verify(server, times(1)).start();
        }
    }

    @Nested
    class WhenStop {
        @Test
        void givenStoppedTwice_thenOnlyStopOnce() throws Exception {
            when(server.getConnectors()).thenReturn(new Connector[0]);
            sut.startup();
            shutdownRegistry.shutdown();
            shutdownRegistry.shutdown();
            verify(server, times(1)).stop();
        }
    }
}
