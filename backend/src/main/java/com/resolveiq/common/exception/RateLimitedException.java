package com.resolveiq.common.exception;

import java.util.Map;

public class RateLimitedException extends ItsmException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, "Too many attempts. Please wait " + retryAfterSeconds + " seconds and try again.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("retryAfterSeconds", retryAfterSeconds);
    }
}
