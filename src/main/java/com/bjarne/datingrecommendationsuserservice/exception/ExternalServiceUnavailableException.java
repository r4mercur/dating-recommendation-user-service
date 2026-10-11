package com.bjarne.datingrecommendationsuserservice.exception;

/**
 * An external service (Spotify, IGDB, Steam) cannot be used right now: it is down, rate limited, out of quota
 * or not configured. Mapped to 503; the rest of the application keeps working.
 */
public class ExternalServiceUnavailableException extends RuntimeException {
    public ExternalServiceUnavailableException(String message) {
        super(message);
    }
}
