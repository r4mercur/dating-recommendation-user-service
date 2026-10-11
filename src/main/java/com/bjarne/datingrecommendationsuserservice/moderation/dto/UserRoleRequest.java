package com.bjarne.datingrecommendationsuserservice.moderation.dto;

import com.bjarne.datingrecommendationsuserservice.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UserRoleRequest(@NotNull Role role) {
}
