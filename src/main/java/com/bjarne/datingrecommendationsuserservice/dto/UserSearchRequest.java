package com.bjarne.datingrecommendationsuserservice.dto;

import java.util.List;

public record UserSearchRequest(List<String> referenceIds) {
}
