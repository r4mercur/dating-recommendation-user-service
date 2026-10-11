package com.bjarne.datingrecommendationsuserservice.event;

import java.time.Instant;

/**
 * A match ended because one of the users unmatched or blocked the other.
 */
public record MatchEndedEvent(
        String matchReferenceId,
        String endedByReferenceId,
        Reason reason,
        Instant occurredAt
) {
    public enum Reason {
        UNMATCHED,
        BLOCKED
    }
}
