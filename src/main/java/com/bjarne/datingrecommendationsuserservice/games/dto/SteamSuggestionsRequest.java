package com.bjarne.datingrecommendationsuserservice.games.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param profile Steam profile URL, vanity name or SteamID64
 */
public record SteamSuggestionsRequest(@NotBlank @Size(max = 200) String profile) {
}
