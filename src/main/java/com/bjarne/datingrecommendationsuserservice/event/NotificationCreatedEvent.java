package com.bjarne.datingrecommendationsuserservice.event;

import java.time.Instant;

/**
 * A notification was stored; consumers push it to the connected sessions of the user.
 */
public record NotificationCreatedEvent(
        String notificationReferenceId,
        String userReferenceId,
        Instant occurredAt
) {
}
