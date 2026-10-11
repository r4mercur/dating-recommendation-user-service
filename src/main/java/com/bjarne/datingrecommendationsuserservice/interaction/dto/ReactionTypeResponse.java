package com.bjarne.datingrecommendationsuserservice.interaction.dto;

import com.bjarne.datingrecommendationsuserservice.interaction.ReactionType;

/**
 * @param dailyLimit     null if unlimited
 * @param remainingToday null if unlimited
 */
public record ReactionTypeResponse(ReactionType type,
                                   boolean positive,
                                   ReactionType.MessagePolicy message,
                                   Integer dailyLimit,
                                   Integer remainingToday) {
}
