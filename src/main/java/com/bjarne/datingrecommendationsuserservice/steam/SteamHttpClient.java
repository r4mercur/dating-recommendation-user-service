package com.bjarne.datingrecommendationsuserservice.steam;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;
import com.bjarne.datingrecommendationsuserservice.external.ExternalHttp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/**
 * Steam Web API. The key is a query parameter there, so exception messages (which contain the request URL) are
 * never logged or passed on.
 */
@Component
public class SteamHttpClient implements SteamClient {

    static final String UNAVAILABLE = "Steam is unavailable right now";
    private static final int VANITY_FOUND = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(SteamHttpClient.class);

    private final SteamProperties properties;
    private final RestClient restClient;

    @Autowired
    public SteamHttpClient(SteamProperties properties) {
        this(properties, ExternalHttp.builder());
    }

    SteamHttpClient(SteamProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public Optional<String> resolveVanity(String vanityName) {
        Wrapper<VanityResponse> response = get("/ISteamUser/ResolveVanityURL/v0001/?key={key}&vanityurl={vanity}",
                new ParameterizedTypeReference<>() {
                }, vanityName, "vanity");
        if (response == null || response.response() == null || response.response().success() == null
                || response.response().success() != VANITY_FOUND || response.response().steamid() == null) {
            return Optional.empty();
        }
        return Optional.of(response.response().steamid());
    }

    @Override
    public Optional<List<SteamGame>> ownedGames(String steamId) {
        return games("/IPlayerService/GetOwnedGames/v0001/?key={key}&steamid={id}&include_appinfo=1&include_played_free_games=1&format=json", steamId, "owned games");
    }

    @Override
    public Optional<List<SteamGame>> recentlyPlayedGames(String steamId) {
        return games("/IPlayerService/GetRecentlyPlayedGames/v1/?key={key}&steamid={id}&count=20&format=json", steamId, "recently played games");
    }

    private Optional<List<SteamGame>> games(String path, String steamId, String operation) {
        Wrapper<GamesResponse> wrapper = get(path, new ParameterizedTypeReference<>() {
        }, steamId, operation);
        GamesResponse response = wrapper == null ? null : wrapper.response();

        // private game details: the response is an empty object without game_count or total_count
        if (response == null || (response.gameCount() == null && response.totalCount() == null && response.games() == null)) {
            return Optional.empty();
        }
        List<SteamGame> games = response.games() == null ? List.of() : response.games().stream()
                .filter(game -> game != null && game.appid() != null)
                .map(game -> new SteamGame(game.appid(), game.name() == null ? "App " + game.appid() : game.name(),
                        game.playtimeForever() == null ? 0 : game.playtimeForever()))
                .toList();
        return Optional.of(games);
    }

    private <T> T get(String path, ParameterizedTypeReference<T> type, String variable, String operation) {
        if (!properties.isConfigured()) {
            LOGGER.warn("Steam is not configured (STEAM_API_KEY).");
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        }

        try {
            return restClient.get().uri(properties.apiBaseUrl() + path, properties.apiKey(), variable)
                    .retrieve().body(type);
        } catch (HttpStatusCodeException e) {
            LOGGER.warn("Steam {} failed with status {}.", operation, e.getStatusCode().value());
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        } catch (RestClientException e) {
            LOGGER.warn("Steam {} failed: {}", operation, e.getClass().getSimpleName());
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Wrapper<T>(T response) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VanityResponse(Integer success, String steamid) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GamesResponse(@JsonProperty("game_count") Integer gameCount, @JsonProperty("total_count") Integer totalCount, List<SteamGameDto> games) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SteamGameDto(Long appid, String name, @JsonProperty("playtime_forever") Integer playtimeForever) {
    }
}
