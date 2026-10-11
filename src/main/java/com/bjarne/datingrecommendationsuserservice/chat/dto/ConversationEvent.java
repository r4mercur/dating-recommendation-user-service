package com.bjarne.datingrecommendationsuserservice.chat.dto;

/**
 * Pushed on /user/queue/conversations when a conversation appears (new match) or disappears (unmatch, block).
 */
public record ConversationEvent(Type type, String matchReferenceId) {
    public enum Type {
        CREATED,
        ENDED
    }
}
