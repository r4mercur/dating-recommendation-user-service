package com.bjarne.datingrecommendationsuserservice.music.dto;

import java.util.List;

/**
 * A Spotify track as returned by the search; also the data that is copied into the profile.
 */
public record TrackResult(String spotifyTrackId,
                          String name,
                          List<String> artists,
                          String album,
                          String imageUrl,
                          String spotifyUrl) {

    private static final String TRACK_URL = "https://open.spotify.com/track/";
    private static final int MAX_LENGTH = 255;

    /** Truncates text to what the database stores and builds the public track URL from the id. */
    public static TrackResult of(String spotifyTrackId, String name, List<String> artists, String album, String imageUrl) {
        return new TrackResult(spotifyTrackId, truncate(name), artists.stream().map(TrackResult::truncate).toList(),
                truncate(album), imageUrl, TRACK_URL + spotifyTrackId);
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
    }
}
