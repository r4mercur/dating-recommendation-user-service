package com.bjarne.datingrecommendationsuserservice.chat.dto;

/**
 * STOMP payload for /app/chat.read.
 */
public record ChatReadCommand(String matchReferenceId) {
}
