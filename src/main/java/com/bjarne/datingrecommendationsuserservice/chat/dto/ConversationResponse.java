package com.bjarne.datingrecommendationsuserservice.chat.dto;

import com.bjarne.datingrecommendationsuserservice.dto.UserProfileResponse;

import java.time.Instant;

/**
 * A conversation is identified by its match.
 *
 * @param lastMessage null if nobody has written yet
 */
public record ConversationResponse(String matchReferenceId,
                                   UserProfileResponse user,
                                   MessageResponse lastMessage,
                                   long unreadCount,
                                   Instant matchedAt) {
}
