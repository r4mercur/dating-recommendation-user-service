package com.bjarne.datingrecommendationsuserservice.exception;

import java.time.Duration;

public class TooManyRequestsException extends RuntimeException {

    /** When the client may try again, sent as Retry-After header; null if unknown (e.g. daily limits). */
    private final Duration retryAfter;

    public TooManyRequestsException(String message) {
        this(message, null);
    }

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
