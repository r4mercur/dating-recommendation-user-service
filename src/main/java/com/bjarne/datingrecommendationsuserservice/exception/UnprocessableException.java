package com.bjarne.datingrecommendationsuserservice.exception;

/**
 * The request is well-formed but cannot be fulfilled with the data behind it (e.g. a private Steam profile). Mapped to 422.
 */
public class UnprocessableException extends RuntimeException {
    public UnprocessableException(String message) {
        super(message);
    }
}
