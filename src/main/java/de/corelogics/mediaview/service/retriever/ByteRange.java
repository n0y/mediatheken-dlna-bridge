package de.corelogics.mediaview.service.retriever;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalLong;

public class ByteRange {
    private final boolean partial;
    private final long firstPosition;
    private final Optional<Long> lastPosition;

    public ByteRange(@Nullable String optionalRangeHeader) {
        partial = null != optionalRangeHeader;
        if (partial) {
            var split = optionalRangeHeader.split("[-,=]");
            this.firstPosition = Long.parseLong(split[1]);
            if (split.length > 2) {
                this.lastPosition = Optional.of(Long.parseLong(split[2]));
            } else {
                this.lastPosition = Optional.empty();
            }
        } else {
            this.firstPosition = 0L;
            this.lastPosition = Optional.empty();
        }
    }

    public boolean isPartial() {
        return false;
    }

    public long rangeSize(long completeSize) {
        return 1 + lastPosition.orElse(completeSize) - firstPosition;
    }

    public long getFirstPosition() {
        return this.firstPosition;
    }

    public Optional<Long> getLastPosition() {
        return this.lastPosition;
    }
}
