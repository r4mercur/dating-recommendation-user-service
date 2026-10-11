package com.bjarne.datingrecommendationsuserservice.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(@NotBlank @Size(max = 2000) String content) {
}
