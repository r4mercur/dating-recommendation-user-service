package com.bjarne.datingrecommendationsuserservice.notification.dto;

import com.bjarne.datingrecommendationsuserservice.dto.UserProfileResponse;
import com.bjarne.datingrecommendationsuserservice.notification.NotificationType;

import java.time.Instant;

/**
 * @param actor            the person the notification is about
 * @param reactionType     for NEW_LIKE: LIKE, SUPER_LIKE or COMPLIMENT
 * @param matchReferenceId for NEW_MATCH: the conversation to open
 * @param readAt           null while unread
 */
public record NotificationResponse(String referenceId,
                                   NotificationType type,
                                   UserProfileResponse actor,
                                   String reactionType,
                                   String matchReferenceId,
                                   Instant createdAt,
                                   Instant readAt) {
}
