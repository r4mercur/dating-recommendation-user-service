package com.bjarne.datingrecommendationsuserservice.chat.dto;

/**
 * Pushed on /user/queue/errors when a STOMP command fails.
 */
public record ChatError(String message) {
}
