package com.bjarne.datingrecomandationsuserservice.dto;

import java.util.List;

public record UserSearchRequest(List<String> referenceIds) {
}
