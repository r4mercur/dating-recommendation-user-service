package com.bjarne.datingrecommendationsuserservice.interaction.dto;

import com.bjarne.datingrecommendationsuserservice.interaction.ReactionType;

import java.time.Instant;

/**
 * @param match the new match if this reaction completed one, otherwise null
 */
public record ReactionResponse(String targetReferenceId,
                               ReactionType type,
                               String message,
                               Instant createdAt,
                               MatchResponse match) {
}
