package com.bjarne.datingrecommendationsuserservice.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
        @NotBlank
        String username,
        @NotBlank
        String password,
        String scope,
        String audience
) {
}
