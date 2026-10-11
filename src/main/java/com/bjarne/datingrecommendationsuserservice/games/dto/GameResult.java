package com.bjarne.datingrecommendationsuserservice.games.dto;

import java.util.List;

/**
 * A game as returned by the search and shown on profiles (the profile holds a copy of the IGDB data).
 *
 * @param platforms  short names like PC, PS5, Switch
 * @param steamAppId Steam app id from IGDB's external games, null if the game is not on Steam
 */
public record GameResult(long igdbId,
                         String name,
                         String coverUrl,
                         Integer year,
                         List<String> platforms,
                         Long steamAppId) {

    private static final int MAX_LENGTH = 255;

    /** Truncates text to what the database stores. */
    public static GameResult of(long igdbId, String name, String coverUrl, Integer year, List<String> platforms, Long steamAppId) {
        return new GameResult(igdbId, name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name, coverUrl, year,
                platforms.stream().map(platform -> platform.length() > MAX_LENGTH ? platform.substring(0, MAX_LENGTH) : platform).toList(),
                steamAppId);
    }
}
