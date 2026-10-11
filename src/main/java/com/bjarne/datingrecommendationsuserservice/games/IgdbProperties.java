package com.bjarne.datingrecommendationsuserservice.games;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials of the Twitch app that is allowed to use IGDB (client credentials flow). They come from the
 * environment (TWITCH_CLIENT_ID / TWITCH_CLIENT_SECRET), never from the repository. Without them the game search answers 503.
 *
 * @param apiBaseUrl        base URL of the IGDB API
 * @param tokenUrl          Twitch token endpoint
 * @param requestsPerSecond IGDB allows 4 requests per second per client id
 */
@ConfigurationProperties("igdb")
public record IgdbProperties(String clientId, String clientSecret, String apiBaseUrl, String tokenUrl, Integer requestsPerSecond) {

    public IgdbProperties {
        clientId = clientId == null ? "" : clientId.trim();
        clientSecret = clientSecret == null ? "" : clientSecret.trim();
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.igdb.com/v4" : apiBaseUrl;
        tokenUrl = tokenUrl == null || tokenUrl.isBlank() ? "https://id.twitch.tv/oauth2/token" : tokenUrl;
        requestsPerSecond = requestsPerSecond == null || requestsPerSecond < 1 ? 4 : requestsPerSecond;
    }

    public boolean isConfigured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }
}
