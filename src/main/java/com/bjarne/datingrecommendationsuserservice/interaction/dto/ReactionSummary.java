package com.bjarne.datingrecommendationsuserservice.interaction.dto;

import com.bjarne.datingrecommendationsuserservice.interaction.Reaction;
import com.bjarne.datingrecommendationsuserservice.interaction.ReactionType;

import java.time.Instant;

public record ReactionSummary(ReactionType type, String message, Instant createdAt) {

    public static ReactionSummary from(Reaction reaction) {
        return reaction == null ? null : new ReactionSummary(reaction.getType(), reaction.getMessage(), reaction.getCreatedAt());
    }
}
