package com.bjarne.datingrecommendationsuserservice.chat.dto;

/**
 * STOMP payload for /app/chat.send.
 */
public record ChatSendCommand(String matchReferenceId, String content) {
}
