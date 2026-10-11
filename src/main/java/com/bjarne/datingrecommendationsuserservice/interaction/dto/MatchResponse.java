package com.bjarne.datingrecommendationsuserservice.interaction.dto;

import com.bjarne.datingrecommendationsuserservice.dto.UserProfileResponse;

import java.time.Instant;

/**
 * @param user          the other person
 * @param theirReaction how the other person reacted, e.g. a SUPER_LIKE or a COMPLIMENT with message
 */
public record MatchResponse(String referenceId,
                            Instant createdAt,
                            UserProfileResponse user,
                            ReactionSummary theirReaction) {
}
