package com.bjarne.datingrecommendationsuserservice.steam;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web API key of the application (STEAM_API_KEY), never in the repository. Without it the Steam import answers 503.
 *
 * @param apiBaseUrl base URL of the Steam Web API
 */
@ConfigurationProperties("steam")
public record SteamProperties(String apiKey, String apiBaseUrl) {

    public SteamProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.steampowered.com" : apiBaseUrl;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }
}
