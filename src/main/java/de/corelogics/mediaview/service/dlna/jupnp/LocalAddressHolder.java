package de.corelogics.mediaview.service.dlna.jupnp;

import java.net.InetAddress;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class LocalAddressHolder {
    private static final ThreadLocal<InetAddress> LOCAL_ADDRESS = new ThreadLocal<>();

    private LocalAddressHolder() {

    }

    public static NoExceptionAutoCloseable memoizeLocalAddress(InetAddress localAddress) {
        LOCAL_ADDRESS.set(localAddress);
        return LOCAL_ADDRESS::remove;
    }

    @FunctionalInterface
    public interface NoExceptionAutoCloseable extends AutoCloseable {
        void close();
    }

    public static Optional<InetAddress> getMemoizedLocalAddress() {
        return ofNullable(LOCAL_ADDRESS.get());
    }
}
