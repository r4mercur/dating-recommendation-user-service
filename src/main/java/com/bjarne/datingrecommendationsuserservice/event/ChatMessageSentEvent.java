package com.bjarne.datingrecommendationsuserservice.event;

import java.time.Instant;

/**
 * A chat message was stored. Intentionally without content: consumers load it, so message texts do not end up in Kafka.
 */
public record ChatMessageSentEvent(
        String messageReferenceId,
        String matchReferenceId,
        String senderReferenceId,
        String recipientReferenceId,
        Instant occurredAt
) {
}
