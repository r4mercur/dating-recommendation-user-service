package com.bjarne.datingrecommendationsuserservice.interaction.dto;

import com.bjarne.datingrecommendationsuserservice.interaction.ReactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param message only allowed (and for COMPLIMENT required) depending on the type's message policy
 */
public record ReactionRequest(@NotBlank String targetReferenceId,
                              @NotNull ReactionType type,
                              @Size(max = 500) String message) {
}
