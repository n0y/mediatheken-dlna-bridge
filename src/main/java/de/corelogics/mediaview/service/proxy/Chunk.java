package de.corelogics.mediaview.service.proxy;

public class Chunk {
    private final long firstBytePosition;
    private final long lastBytePosition;

    public Chunk(long firstBytePosition, long lastBytePosition) {
        this.firstBytePosition = firstBytePosition;
        this.lastBytePosition = lastBytePosition;
    }

    public long getFirstBytePosition() {
        return firstBytePosition;
    }

    public long getLastBytePosition() {
        return lastBytePosition;
    }

    @Override
    public String toString() {
        return "bytes=" + firstBytePosition + "-" + lastBytePosition;
    }
}
