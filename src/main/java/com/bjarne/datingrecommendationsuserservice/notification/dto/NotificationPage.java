package com.bjarne.datingrecommendationsuserservice.notification.dto;

import java.util.List;

/**
 * Newest first. @param hasMore load older ones with before = the referenceId of the last item
 */
public record NotificationPage(List<NotificationResponse> items, boolean hasMore) {
}
