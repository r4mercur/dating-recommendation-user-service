package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull UserStatus status) {
}
