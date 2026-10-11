package com.bjarne.datingrecommendationsuserservice.music.dto;

/**
 * One entry of {@code PUT /api/me/songs}. The list is validated as a whole in {@code SongService}.
 */
public record SongRequest(String spotifyTrackId, boolean anthem) {
}
