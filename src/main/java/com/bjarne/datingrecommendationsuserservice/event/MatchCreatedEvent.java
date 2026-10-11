package com.bjarne.datingrecommendationsuserservice.event;

import java.time.Instant;

public record MatchCreatedEvent(
        String matchReferenceId,
        String userReferenceId,
        String otherUserReferenceId,
        Instant occurredAt
) {
}
