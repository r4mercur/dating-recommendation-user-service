package com.bjarne.datingrecommendationsuserservice.exception;

/**
 * Refresh token unknown, expired, already used, revoked or the account may no longer log in. Always 401 with the
 * same message, so the response does not tell which of these applies.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid refresh token");
    }
}
