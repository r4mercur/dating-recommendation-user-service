package com.bjarne.datingrecommendationsuserservice.event;

import java.time.Instant;

/**
 * The reader has read all messages the partner sent in this match up to readAt.
 */
public record ChatMessagesReadEvent(
        String matchReferenceId,
        String readerReferenceId,
        String partnerReferenceId,
        Instant readAt
) {
}
