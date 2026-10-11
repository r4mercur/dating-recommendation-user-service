package com.bjarne.datingrecommendationsuserservice.music;

import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.music.dto.TrackResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Track search for the profile editor. Results are cached for ten minutes per normalized query, so repeated or
 * parallel searches do not use up the Spotify quota. Tracks seen in a search are remembered as well, so picking
 * one of them does not need another call.
 */
@Service
public class MusicSearchService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_RESULTS = 10;

    private static final Pattern TRACK_ID = Pattern.compile("[A-Za-z0-9]{22}");
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final SpotifyClient spotifyClient;
    private final Cache<String, List<TrackResult>> searches = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL).maximumSize(1_000).build();
    private final Cache<String, TrackResult> tracks = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL).maximumSize(10_000).build();

    public MusicSearchService(SpotifyClient spotifyClient) {
        this.spotifyClient = spotifyClient;
    }

    public List<TrackResult> search(String query) {
        String normalized = normalize(query);
        if (normalized.length() < MIN_QUERY_LENGTH) {
            throw new BadRequestException("The search text must have at least " + MIN_QUERY_LENGTH + " characters");
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("The search text must not be longer than " + MAX_QUERY_LENGTH + " characters");
        }

        return searches.get(normalized, key -> {
            List<TrackResult> results = spotifyClient.search(key, MAX_RESULTS).stream().limit(MAX_RESULTS).toList();
            results.forEach(track -> tracks.put(track.spotifyTrackId(), track));
            return results;
        });
    }

    /** The track with this id, or empty if it does not exist. Malformed ids are rejected without asking Spotify. */
    public Optional<TrackResult> findTrack(String trackId) {
        if (trackId == null || !TRACK_ID.matcher(trackId).matches()) {
            return Optional.empty();
        }

        TrackResult cached = tracks.getIfPresent(trackId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<TrackResult> track = spotifyClient.findTrack(trackId);
        track.ifPresent(found -> tracks.put(trackId, found));
        return track;
    }

    /** For tests: forget everything that was cached. */
    public void clearCaches() {
        searches.invalidateAll();
        tracks.invalidateAll();
    }

    static boolean isValidTrackId(String trackId) {
        return trackId != null && TRACK_ID.matcher(trackId).matches();
    }

    private static String normalize(String query) {
        return query == null ? "" : query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
