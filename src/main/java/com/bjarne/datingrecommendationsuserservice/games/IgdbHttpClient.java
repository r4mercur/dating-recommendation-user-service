package com.bjarne.datingrecommendationsuserservice.games;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;
import com.bjarne.datingrecommendationsuserservice.external.AccessTokenCache;
import com.bjarne.datingrecommendationsuserservice.external.ExternalHttp;
import com.bjarne.datingrecommendationsuserservice.external.RequestRateLimiter;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * IGDB v4 (APICalypse queries) with a Twitch app token (client credentials). The token lives about 60 days and is
 * cached. All requests go through a rate limiter (IGDB allows 4 per second); a request that would have to wait
 * too long is refused instead of piling up.
 */
@Component
public class IgdbHttpClient implements IgdbClient {

    static final String UNAVAILABLE = "Game search is unavailable right now";
    /** IGDB's id of the external game source "Steam". */
    static final int STEAM_SOURCE = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(IgdbHttpClient.class);
    private static final String IMAGE_URL = "https://images.igdb.com/igdb/image/upload/t_cover_big/%s.jpg";
    private static final String GAME_FIELDS = "name,cover.image_id,first_release_date,platforms.abbreviation,platforms.name,"
            + "external_games.uid,external_games.external_game_source";
    private static final String GAME_FIELDS_OF_EXTERNAL = "uid,game.name,game.cover.image_id,game.first_release_date,"
            + "game.platforms.abbreviation,game.platforms.name";
    private static final Duration TOKEN_RENEW_BEFORE_EXPIRY = Duration.ofDays(1);
    private static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofSeconds(2);

    private final IgdbProperties properties;
    private final RestClient restClient;
    private final AccessTokenCache tokenCache;
    private final RequestRateLimiter rateLimiter;

    @Autowired
    public IgdbHttpClient(IgdbProperties properties) {
        this(properties, ExternalHttp.builder(), Clock.systemUTC());
    }

    IgdbHttpClient(IgdbProperties properties, RestClient.Builder builder, Clock clock) {
        this.properties = properties;
        this.restClient = builder.build();
        this.tokenCache = new AccessTokenCache(this::requestToken, TOKEN_RENEW_BEFORE_EXPIRY, clock);
        this.rateLimiter = new RequestRateLimiter(properties.requestsPerSecond(), MAX_RATE_LIMIT_WAIT);
    }

    @Override
    public List<GameResult> search(String query, int limit) {
        return games("search \"" + escape(query) + "\"; fields " + GAME_FIELDS + "; limit " + limit + ";");
    }

    @Override
    public List<GameResult> findByIds(Collection<Long> igdbIds) {
        if (igdbIds.isEmpty()) {
            return List.of();
        }
        return games("fields " + GAME_FIELDS + "; where id = (" + igdbIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "); limit " + igdbIds.size() + ";");
    }

    @Override
    public Map<Long, GameResult> findBySteamAppIds(Collection<Long> steamAppIds) {
        if (steamAppIds.isEmpty()) {
            return Map.of();
        }

        String uids = steamAppIds.stream().map(appId -> "\"" + appId + "\"").collect(Collectors.joining(","));
        ExternalGame[] found = post("external_games", "fields " + GAME_FIELDS_OF_EXTERNAL + "; where external_game_source = " + STEAM_SOURCE
                + " & uid = (" + uids + "); limit " + Math.min(500, steamAppIds.size() * 2) + ";", ExternalGame[].class);

        Map<Long, GameResult> result = new HashMap<>();
        for (ExternalGame external : found == null ? new ExternalGame[0] : found) {
            Long appId = parseLong(external.uid());
            GameResult game = appId == null || external.game() == null ? null : toResult(external.game(), appId);
            if (game != null) {
                result.putIfAbsent(appId, game);
            }
        }
        return result;
    }

    private List<GameResult> games(String query) {
        Game[] found = post("games", query, Game[].class);
        if (found == null) {
            return List.of();
        }
        return java.util.Arrays.stream(found).map(game -> toResult(game, steamAppId(game))).filter(Objects::nonNull).toList();
    }

    private <T> T post(String endpoint, String query, Class<T> type) {
        if (!properties.isConfigured()) {
            LOGGER.warn("IGDB is not configured (TWITCH_CLIENT_ID / TWITCH_CLIENT_SECRET).");
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        }

        return call(endpoint, () -> {
            if (!rateLimiter.tryAcquire()) {
                LOGGER.warn("IGDB rate limit reached, request to {} refused.", endpoint);
                throw new ExternalServiceUnavailableException(UNAVAILABLE);
            }
            try {
                return send(endpoint, query, type);
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() != 401) {
                    throw e;
                }
                // the app token was rejected: take a fresh one and try once more
                tokenCache.invalidate();
                if (!rateLimiter.tryAcquire()) {
                    throw new ExternalServiceUnavailableException(UNAVAILABLE);
                }
                return send(endpoint, query, type);
            }
        });
    }

    private <T> T send(String endpoint, String query, Class<T> type) {
        return restClient.post().uri(properties.apiBaseUrl() + "/" + endpoint)
                .header("Client-ID", properties.clientId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCache.get())
                .contentType(MediaType.TEXT_PLAIN)
                .body(query)
                .retrieve()
                .body(type);
    }

    private <T> T call(String endpoint, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            LOGGER.warn("IGDB request to {} failed with status {}.", endpoint, e.getStatusCode().value());
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        } catch (ExternalServiceUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            LOGGER.warn("IGDB request to {} failed: {}", endpoint, e.getClass().getSimpleName());
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        }
    }

    private AccessTokenCache.Token requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "client_credentials");
        try {
            TokenResponse response = restClient.post().uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new ExternalServiceUnavailableException(UNAVAILABLE);
            }
            return new AccessTokenCache.Token(response.accessToken(), Duration.ofSeconds(response.expiresIn() > 0 ? response.expiresIn() : 3600));
        } catch (HttpStatusCodeException e) {
            LOGGER.error("Twitch token request failed with status {}, check TWITCH_CLIENT_ID / TWITCH_CLIENT_SECRET.", e.getStatusCode().value());
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        }
    }

    private static GameResult toResult(Game game, Long steamAppId) {
        if (game == null || game.id() == null || game.name() == null) {
            return null;
        }

        String cover = game.cover() == null || game.cover().imageId() == null ? null : IMAGE_URL.formatted(game.cover().imageId());
        Integer year = game.firstReleaseDate() == null ? null
                : Instant.ofEpochSecond(game.firstReleaseDate()).atZone(ZoneOffset.UTC).getYear();
        List<String> platforms = game.platforms() == null ? List.of() : game.platforms().stream()
                .map(platform -> platform.abbreviation() != null ? platform.abbreviation() : platform.name())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return GameResult.of(game.id(), game.name(), cover, year, platforms, steamAppId);
    }

    private static Long steamAppId(Game game) {
        if (game.externalGames() == null) {
            return null;
        }
        return game.externalGames().stream()
                .filter(external -> external.source() != null && external.source() == STEAM_SOURCE)
                .map(external -> parseLong(external.uid()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Long parseLong(String text) {
        try {
            return text == null ? null : Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Quotes inside the search text would end the APICalypse string early. */
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Game(Long id,
                String name,
                Cover cover,
                @JsonProperty("first_release_date") Long firstReleaseDate,
                List<Platform> platforms,
                @JsonProperty("external_games") List<ExternalGameRef> externalGames) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Cover(@JsonProperty("image_id") String imageId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Platform(String abbreviation, String name) {
    }

    /** An entry of a game's {@code external_games}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExternalGameRef(String uid, @JsonProperty("external_game_source") Integer source) {
    }

    /** A result of the {@code external_games} endpoint with the game expanded. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExternalGame(String uid, Game game) {
    }
}
