package de.corelogics.mediaview.service.fixups;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.client.*;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.jupnp.http.Headers;
import org.jupnp.model.message.*;
import org.jupnp.model.message.header.UpnpHeader;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.AbstractStreamClient;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.util.SpecificationViolationReporter;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.eclipse.jetty.http.HttpHeader.CONNECTION;

/**
 * Implementation based on <a href="http://www.eclipse.org/jetty/">Jetty 9.2.x</a>.
 * <p>
 *
 * @author Victor Toni - initial contribution
 */
public class JettyStreamClientImplFixed extends AbstractStreamClient<StreamClientConfigurationImpl, Request> {

    private final Logger log = LogManager.getLogger(StreamClient.class);

    protected final StreamClientConfigurationImpl configuration;
    protected final HttpClient httpClient;

    public JettyStreamClientImplFixed(StreamClientConfigurationImpl configuration) throws InitializationException {
        this.configuration = configuration;

        httpClient = new HttpClient();

        // These are some safety settings, we should never run into these timeouts as we
        // do our own expiration checking
        httpClient.setConnectTimeout((getConfiguration().getTimeoutSeconds() + 5) * 1000);
        httpClient.setMaxConnectionsPerDestination(2);

        int cpus = Runtime.getRuntime().availableProcessors();
        int maxThreads = 5 * cpus;

        final QueuedThreadPool queuedThreadPool = createThreadPool("jupnp-jetty-client", 5, maxThreads, 60000);

        httpClient.setExecutor(queuedThreadPool);

        if (getConfiguration().getSocketBufferSize() != -1) {
            httpClient.setRequestBufferSize(getConfiguration().getSocketBufferSize());
            httpClient.setResponseBufferSize(getConfiguration().getSocketBufferSize());
        }

        try {
            httpClient.start();
        } catch (final Exception e) {
            log.error("Failed to instantiate HTTP client", e);
            throw new InitializationException("Failed to instantiate HTTP client", e);
        }
    }

    @Override
    public StreamClientConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    protected Request createRequest(StreamRequestMessage requestMessage) {
        final UpnpRequest upnpRequest = requestMessage.getOperation();
        URI uri = upnpRequest.getURI();

        if (uri == null) {
            log.debug("Cannot create request because URI is null.");
            return null;
        }

        log.trace("Creating HTTP request. URI: '{}' method: '{}'", upnpRequest.getURI(), upnpRequest.getMethod());
        Request request;
        switch (upnpRequest.getMethod()) {
            case GET:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
            case POST:
            case NOTIFY:
                request = httpClient.newRequest(uri).method(upnpRequest.getHttpMethodName());
                break;
            default:
                throw new RuntimeException("Unknown HTTP method: " + upnpRequest.getHttpMethodName());
        }
        switch (upnpRequest.getMethod()) {
            case POST:
            case NOTIFY:
                request.body(createContentProvider(requestMessage));
                break;
            default:
        }

        // FIXME: what about HTTP2 ?
        if (requestMessage.getOperation().getHttpMinorVersion() == 0) {
            request.version(HttpVersion.HTTP_1_0);
        } else {
            request.version(HttpVersion.HTTP_1_1);
            // This closes the http connection immediately after the call.
            //
            // Even though jetty client is able to close connections properly,
            // it still takes ~30 seconds to do so. This may cause too many
            // connections for installations with many upnp devices.
            request.headers(h -> h.add(CONNECTION, "close"));
        }

        // Add the default user agent if not already set on the message
        if (!requestMessage.getHeaders().containsKey(UpnpHeader.Type.USER_AGENT)) {
            request.agent(getConfiguration().getUserAgentValue(requestMessage.getUdaMajorVersion(),
                    requestMessage.getUdaMinorVersion()));
        }

        // Headers
        request.headers(headers -> {
            for (Map.Entry<String, List<String>> entry : requestMessage.getHeaders().entrySet()) {
                for (final String value : entry.getValue()) {
                    headers.add(entry.getKey(), value);
                }
            }
        });


        return request;
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(final StreamRequestMessage requestMessage,
                                                             final Request request) {
        return new Callable<StreamResponseMessage>() {
            @Override
            public StreamResponseMessage call() throws Exception {
                log.trace("Sending HTTP request: {}", requestMessage);
                try {
                    final ContentResponse httpResponse = request.send();

                    log.trace("Received HTTP response: {}", httpResponse.getReason());

                    // Status
                    final UpnpResponse responseOperation = new UpnpResponse(httpResponse.getStatus(),
                            httpResponse.getReason());

                    // Message
                    final StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);

                    // Headers
                    var jupnpHeaders = new Headers();
                    for (var httpField : httpResponse.getHeaders()) {
                        jupnpHeaders.add(httpField.getName(), httpField.getValue());
                    }
                    responseMessage.setHeaders(new UpnpHeaders(jupnpHeaders));


                    // Body
                    final byte[] bytes = httpResponse.getContent();
                    if (bytes == null || 0 == bytes.length) {
                        log.trace("HTTP response message has no entity");

                        return responseMessage;
                    }

                    if (responseMessage.isContentTypeMissingOrText()) {
                        log.trace("HTTP response message contains text entity");
                    } else {
                        log.trace("HTTP response message contains binary entity");
                    }

                    responseMessage.setBodyCharacters(bytes);

                    return responseMessage;
                } catch (final RuntimeException e) {
                    log.error("Request: {} failed", request, e);
                    throw e;
                }
            }
        };
    }

    @Override
    protected void abort(Request request) {
        request.abort(new Exception("Request aborted by API"));
    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        if (t instanceof IllegalStateException) {
            // TODO: Document when/why this happens and why we can ignore it, violating the
            // logging rules of the StreamClient#sendRequest() method
            log.trace("Illegal state: {}", t.getMessage());
            return true;
        } else if (t.getMessage().contains("HTTP protocol violation")) {
            SpecificationViolationReporter.report(t.getMessage());
            return true;
        }
        return false;
    }

    @Override
    public void stop() {
        log.trace("Shutting down HTTP client connection manager/pool");
        try {
            httpClient.stop();
        } catch (Exception e) {
            log.info("Shutting down of HTTP client throwed exception", e);
        }
    }

    protected <O extends UpnpOperation> Request.Content createContentProvider(final UpnpMessage<O> upnpMessage) {
        if (upnpMessage.getBodyType().equals(UpnpMessage.BodyType.STRING)) {
            log.trace("Preparing HTTP request entity as String");

            final String charset = upnpMessage.getContentTypeCharset();
            return new StringRequestContent(upnpMessage.getBodyString(), charset != null ? charset : "UTF-8");
        } else {
            log.trace("Preparing HTTP request entity as byte[]");
            return new BytesRequestContent(upnpMessage.getBodyBytes());
        }
    }

    private QueuedThreadPool createThreadPool(String consumerName, int minThreads, int maxThreads,
                                              int keepAliveTimeout) {
        QueuedThreadPool queuedThreadPool = new QueuedThreadPool(maxThreads, minThreads, keepAliveTimeout);
        queuedThreadPool.setName(consumerName);
        queuedThreadPool.setDaemon(true);
        return queuedThreadPool;
    }

}
