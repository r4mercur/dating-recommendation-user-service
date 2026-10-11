package com.bjarne.datingrecommendationsuserservice.dto;

import java.util.List;

/**
 * @param nextCursor pass as {@code cursor} to load the next page; null when there are no more recommendations
 */
public record RecommendationPage(List<ProfileCard> items, String nextCursor) {
}
