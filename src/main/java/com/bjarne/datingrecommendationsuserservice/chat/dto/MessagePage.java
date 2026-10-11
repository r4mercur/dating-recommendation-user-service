package com.bjarne.datingrecommendationsuserservice.chat.dto;

import java.util.List;

/**
 * Messages in chronological order (oldest first).
 *
 * @param hasMore whether there are older messages; load them with before = items[0].referenceId
 */
public record MessagePage(List<MessageResponse> items, boolean hasMore) {
}
