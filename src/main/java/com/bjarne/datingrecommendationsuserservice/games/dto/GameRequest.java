package com.bjarne.datingrecommendationsuserservice.games.dto;

/**
 * One entry of {@code PUT /api/me/games}. The list is validated as a whole in {@code GameService}.
 */
public record GameRequest(Long igdbId) {
}
