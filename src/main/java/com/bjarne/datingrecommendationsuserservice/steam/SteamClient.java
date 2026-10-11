package com.bjarne.datingrecommendationsuserservice.steam;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;

import java.util.List;
import java.util.Optional;

/**
 * Access to the Steam Web API to read the game library of a public profile.
 * Implementations throw {@link ExternalServiceUnavailableException} if Steam cannot be used right now.
 */
public interface SteamClient {

    /** @param playtimeMinutes total playtime */
    record SteamGame(long appId, String name, int playtimeMinutes) {
    }

    /** SteamID64 for a vanity name, or empty if no profile has that name. */
    Optional<String> resolveVanity(String vanityName);

    /** The owned games, or empty if the profile hides its game details (private profile). */
    Optional<List<SteamGame>> ownedGames(String steamId);

    /** The games played in the last two weeks, or empty if the profile hides its game details. */
    Optional<List<SteamGame>> recentlyPlayedGames(String steamId);
}
