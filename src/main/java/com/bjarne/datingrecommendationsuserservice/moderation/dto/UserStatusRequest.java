package com.bjarne.datingrecommendationsuserservice.moderation.dto;

import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull UserStatus status) {
}
