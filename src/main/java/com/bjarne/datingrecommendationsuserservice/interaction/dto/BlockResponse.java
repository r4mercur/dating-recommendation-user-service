package com.bjarne.datingrecommendationsuserservice.interaction.dto;

import com.bjarne.datingrecommendationsuserservice.dto.UserProfileResponse;

import java.time.Instant;

public record BlockResponse(UserProfileResponse user, Instant createdAt) {
}
