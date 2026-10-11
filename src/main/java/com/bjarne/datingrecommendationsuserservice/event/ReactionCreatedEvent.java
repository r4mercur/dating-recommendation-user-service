package com.bjarne.datingrecommendationsuserservice.event;

import java.time.Instant;

/**
 * A user reacted to another user. The type is the name of a ReactionType; the message is not part of the event.
 */
public record ReactionCreatedEvent(
        String fromReferenceId,
        String toReferenceId,
        String type,
        boolean positive,
        Instant occurredAt
) {
}
