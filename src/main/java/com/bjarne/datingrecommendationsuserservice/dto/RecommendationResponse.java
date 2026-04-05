package com.bjarne.datingrecommendationsuserservice.dto;

import java.util.List;

public record RecommendationResponse(
        String userId,
        List<String> recommendationUserIds
) {
}
