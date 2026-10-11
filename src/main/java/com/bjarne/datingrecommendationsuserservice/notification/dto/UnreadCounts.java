package com.bjarne.datingrecommendationsuserservice.notification.dto;

/**
 * Badge counters: unread notifications and unread chat messages in active conversations.
 */
public record UnreadCounts(long notifications, long messages) {
}
