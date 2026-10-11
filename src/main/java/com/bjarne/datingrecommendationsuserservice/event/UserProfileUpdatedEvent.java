package com.bjarne.datingrecommendationsuserservice.event;

import java.time.Instant;

/**
 * A user was created or changed data that matters for search (profile, address, preferences).
 * Intentionally thin: consumers load the current state, so out-of-order events cannot index stale data.
 */
public record UserProfileUpdatedEvent(
        String referenceId,
        Instant occurredAt
) {
    public static UserProfileUpdatedEvent of(String referenceId) {
        return new UserProfileUpdatedEvent(referenceId, Instant.now());
    }
}
