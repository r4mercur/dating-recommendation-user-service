package com.bjarne.datingrecommendationsuserservice.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param refreshToken     exchange it at POST /api/auth/refresh for new tokens before access_token expires;
 *                         every refresh returns a new refresh token, the old one becomes invalid
 * @param refreshExpiresIn lifetime of the refresh token in seconds
 */
public record TokenResponseDto(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("token_type")
        String tokenType,
        @JsonProperty("expires_in")
        Long expiresIn,
        String scope,
        @JsonProperty("refresh_token")
        String refreshToken,
        @JsonProperty("refresh_expires_in")
        Long refreshExpiresIn
) {
}
