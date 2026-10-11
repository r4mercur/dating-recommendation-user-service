package com.bjarne.datingrecommendationsuserservice.interaction.dto;

import jakarta.validation.constraints.NotBlank;

public record BlockRequest(@NotBlank String referenceId) {
}
