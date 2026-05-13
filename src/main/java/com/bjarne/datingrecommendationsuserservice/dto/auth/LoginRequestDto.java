package com.bjarne.datingrecommendationsuserservice.dto.auth;

public record LoginRequestDto(
        String username,
        String password,
        String scope,
        String audience
) {
}
