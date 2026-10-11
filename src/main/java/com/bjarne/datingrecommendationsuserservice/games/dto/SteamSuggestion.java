package com.bjarne.datingrecommendationsuserservice.games.dto;

/**
 * A game from the user's Steam library. Only suggestions with an {@code igdbId} can be saved to the profile.
 *
 * @param igdbId        null if IGDB does not know the Steam app
 * @param playtimeHours total playtime, rounded to one decimal
 */
public record SteamSuggestion(Long igdbId,
                              String name,
                              String coverUrl,
                              long steamAppId,
                              double playtimeHours) {
}
