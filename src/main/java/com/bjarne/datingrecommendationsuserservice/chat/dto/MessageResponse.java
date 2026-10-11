package com.bjarne.datingrecommendationsuserservice.chat.dto;

import com.bjarne.datingrecommendationsuserservice.chat.Message;

import java.time.Instant;

/**
 * @param readAt when the recipient read the message, null while unread
 */
public record MessageResponse(String referenceId,
                              String matchReferenceId,
                              String senderReferenceId,
                              String content,
                              Instant createdAt,
                              Instant readAt) {

    /** The match reference is passed in, so the lazy match does not have to be loaded. */
    public static MessageResponse from(Message message, String matchReferenceId) {
        return new MessageResponse(message.getReferenceId(), matchReferenceId, message.getSender().getReferenceId(),
                message.getContent(), message.getCreatedAt(), message.getReadAt());
    }
}
