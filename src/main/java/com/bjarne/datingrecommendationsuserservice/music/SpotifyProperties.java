package com.bjarne.datingrecommendationsuserservice.music;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials of the Spotify app (client credentials flow, no user login). They come from the environment
 * (SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET), never from the repository. Without them the music search answers 503.
 *
 * @param apiBaseUrl base URL of the Web API
 * @param tokenUrl   token endpoint of Spotify accounts
 */
@ConfigurationProperties("spotify")
public record SpotifyProperties(String clientId, String clientSecret, String apiBaseUrl, String tokenUrl) {

    public SpotifyProperties {
        clientId = clientId == null ? "" : clientId.trim();
        clientSecret = clientSecret == null ? "" : clientSecret.trim();
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.spotify.com/v1" : apiBaseUrl;
        tokenUrl = tokenUrl == null || tokenUrl.isBlank() ? "https://accounts.spotify.com/api/token" : tokenUrl;
    }

    public boolean isConfigured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }
}
