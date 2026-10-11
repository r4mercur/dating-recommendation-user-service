package com.bjarne.datingrecommendationsuserservice.interaction.dto;

import com.bjarne.datingrecommendationsuserservice.dto.UserProfileResponse;

/**
 * A positive reaction the user has not answered yet.
 */
public record ReceivedLikeResponse(UserProfileResponse user, ReactionSummary reaction) {
}
