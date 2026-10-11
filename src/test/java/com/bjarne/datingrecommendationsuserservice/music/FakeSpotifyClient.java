package com.bjarne.datingrecommendationsuserservice.music;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;
import com.bjarne.datingrecommendationsuserservice.music.dto.TrackResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory Spotify: a fixed catalog, a switch to simulate an outage or an exhausted quota, and call counters.
 */
public class FakeSpotifyClient implements SpotifyClient {

    private final Map<String, TrackResult> catalog = new LinkedHashMap<>();
    private volatile boolean down;
    public final AtomicInteger searchCalls = new AtomicInteger();
    public final AtomicInteger trackCalls = new AtomicInteger();

    public static String id(int number) {
        return String.format("%022d", number);
    }

    /** Resets the fake to a catalog of tracks 1..n named "Song n" by "Artist n". */
    public void reset() {
        catalog.clear();
        down = false;
        searchCalls.set(0);
        trackCalls.set(0);
        for (int number = 1; number <= 12; number++) {
            add(number, "Song " + number, "Artist " + number);
        }
    }

    public void add(int number, String name, String... artists) {
        String id = id(number);
        catalog.put(id, TrackResult.of(id, name, List.of(artists), "Album " + number, "https://img.example/" + id + ".jpg"));
    }

    public TrackResult track(int number) {
        return catalog.get(id(number));
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    @Override
    public List<TrackResult> search(String query, int limit) {
        searchCalls.incrementAndGet();
        failIfDown();
        String needle = query.toLowerCase(Locale.ROOT);
        return catalog.values().stream()
                .filter(track -> track.name().toLowerCase(Locale.ROOT).contains(needle)
                        || track.artists().stream().anyMatch(artist -> artist.toLowerCase(Locale.ROOT).contains(needle)))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<TrackResult> findTrack(String trackId) {
        trackCalls.incrementAndGet();
        failIfDown();
        return Optional.ofNullable(catalog.get(trackId));
    }

    private void failIfDown() {
        if (down) {
            throw new ExternalServiceUnavailableException("Music search is unavailable right now");
        }
    }
}
