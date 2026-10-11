package com.bjarne.datingrecommendationsuserservice.steam;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory Steam: vanity names and libraries are set per test; a library of {@code null} is a private profile.
 */
public class FakeSteamClient implements SteamClient {

    public static final String PUBLIC_ID = "76561198000000001";
    public static final String PRIVATE_ID = "76561198000000002";

    private final Map<String, String> vanityNames = new HashMap<>();
    private final Map<String, List<SteamGame>> libraries = new HashMap<>();
    private volatile boolean down;

    public void reset() {
        vanityNames.clear();
        libraries.clear();
        down = false;
    }

    public void vanity(String name, String steamId) {
        vanityNames.put(name, steamId);
    }

    /** @param games null for a profile with private game details */
    public void library(String steamId, List<SteamGame> games) {
        libraries.put(steamId, games);
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    @Override
    public Optional<String> resolveVanity(String vanityName) {
        failIfDown();
        return Optional.ofNullable(vanityNames.get(vanityName));
    }

    @Override
    public Optional<List<SteamGame>> ownedGames(String steamId) {
        failIfDown();
        return Optional.ofNullable(libraries.get(steamId));
    }

    @Override
    public Optional<List<SteamGame>> recentlyPlayedGames(String steamId) {
        failIfDown();
        return Optional.ofNullable(libraries.get(steamId)).map(games -> games.stream().filter(game -> game.playtimeMinutes() > 0).toList());
    }

    private void failIfDown() {
        if (down) {
            throw new ExternalServiceUnavailableException("Steam is unavailable right now");
        }
    }
}
