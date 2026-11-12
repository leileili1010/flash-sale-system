package com.flashsale.ratelimit;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import java.time.Duration;

/** Rejection carrying how long the client should wait, sent as a {@code Retry-After} header. */
public class RateLimitedException extends ApiException {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfter = retryAfter;
    }

    /** Whole seconds, rounded up, as {@code Retry-After} requires. Always at least 1. */
    public long retryAfterSeconds() {
        long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
