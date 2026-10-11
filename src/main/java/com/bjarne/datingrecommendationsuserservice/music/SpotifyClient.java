package com.bjarne.datingrecommendationsuserservice.music;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;
import com.bjarne.datingrecommendationsuserservice.music.dto.TrackResult;

import java.util.List;
import java.util.Optional;

/**
 * Access to the Spotify Web API. Only used to search and to pick tracks, never to show profiles.
 * Implementations throw {@link ExternalServiceUnavailableException} if Spotify cannot be used right now.
 */
public interface SpotifyClient {

    /** Tracks matching the text, best match first, at most {@code limit} (Spotify allows 10 in development mode). */
    List<TrackResult> search(String query, int limit);

    /** The track, or empty if Spotify does not know the id. */
    Optional<TrackResult> findTrack(String trackId);
}
