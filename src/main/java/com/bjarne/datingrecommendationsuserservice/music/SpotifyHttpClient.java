package com.bjarne.datingrecommendationsuserservice.music;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;
import com.bjarne.datingrecommendationsuserservice.external.AccessTokenCache;
import com.bjarne.datingrecommendationsuserservice.external.ExternalHttp;
import com.bjarne.datingrecommendationsuserservice.music.dto.TrackResult;
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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Spotify Web API with the client credentials flow (app token, no user login). The token is cached and renewed
 * before it expires. As soon as Spotify answers 429 (the quota of the developer account is used up, reason
 * QUOTA_EXCEEDED), no further requests are sent until the Retry-After time has passed.
 */
@Component
public class SpotifyHttpClient implements SpotifyClient {

    static final String UNAVAILABLE = "Music search is unavailable right now";

    private static final Logger LOGGER = LoggerFactory.getLogger(SpotifyHttpClient.class);
    private static final Duration TOKEN_RENEW_BEFORE_EXPIRY = Duration.ofMinutes(5);
    private static final Duration DEFAULT_COOL_DOWN = Duration.ofSeconds(30);
    private static final Duration MAX_COOL_DOWN = Duration.ofHours(1);

    private final SpotifyProperties properties;
    private final RestClient restClient;
    private final AccessTokenCache tokenCache;
    private final Clock clock;
    private volatile Instant coolDownUntil = Instant.MIN;

    @Autowired
    public SpotifyHttpClient(SpotifyProperties properties) {
        this(properties, ExternalHttp.builder(), Clock.systemUTC());
    }

    SpotifyHttpClient(SpotifyProperties properties, RestClient.Builder builder, Clock clock) {
        this.properties = properties;
        this.restClient = builder.build();
        this.clock = clock;
        this.tokenCache = new AccessTokenCache(this::requestToken, TOKEN_RENEW_BEFORE_EXPIRY, clock);
    }

    @Override
    public List<TrackResult> search(String query, int limit) {
        SearchResponse response = call("search", () -> get(properties.apiBaseUrl() + "/search?q={q}&type=track&limit={limit}",
                SearchResponse.class, query, limit));
        if (response == null || response.tracks() == null || response.tracks().items() == null) {
            return List.of();
        }
        return response.tracks().items().stream().map(SpotifyHttpClient::toResult).filter(Objects::nonNull).toList();
    }

    @Override
    public Optional<TrackResult> findTrack(String trackId) {
        try {
            return Optional.ofNullable(call("track", () -> toResult(get(properties.apiBaseUrl() + "/tracks/{id}", Track.class, trackId))));
        } catch (UnknownTrack e) {
            return Optional.empty();
        }
    }

    private <T> T get(String url, Class<T> type, Object... uriVariables) {
        try {
            return authorized(restClient.get().uri(url, uriVariables)).retrieve().body(type);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401) {
                // the app token was rejected: take a fresh one and try once more
                tokenCache.invalidate();
                return authorized(restClient.get().uri(url, uriVariables)).retrieve().body(type);
            }
            throw e;
        }
    }

    private RestClient.RequestHeadersSpec<?> authorized(RestClient.RequestHeadersSpec<?> request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCache.get());
    }

    /** Common handling of Spotify failures: everything except "unknown id" makes the service unavailable. */
    private <T> T call(String operation, Supplier<T> request) {
        if (!properties.isConfigured()) {
            LOGGER.warn("Spotify is not configured (SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET).");
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        }
        if (clock.instant().isBefore(coolDownUntil)) {
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        }

        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if ("track".equals(operation) && (status == 400 || status == 404)) {
                throw new UnknownTrack();
            }
            if (status == 429) {
                coolDown(e);
            } else {
                LOGGER.warn("Spotify {} failed with status {}.", operation, status);
            }
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        } catch (ExternalServiceUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            LOGGER.warn("Spotify {} failed: {}", operation, e.getClass().getSimpleName());
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        }
    }

    private void coolDown(HttpStatusCodeException e) {
        Duration wait = DEFAULT_COOL_DOWN;
        String retryAfter = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                wait = Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
            } catch (NumberFormatException ignored) {
                // keep the default
            }
        }
        wait = wait.compareTo(MAX_COOL_DOWN) > 0 ? MAX_COOL_DOWN : wait;
        coolDownUntil = clock.instant().plus(wait);
        String reason = e.getResponseBodyAsString().contains("QUOTA_EXCEEDED") ? "QUOTA_EXCEEDED" : "rate limit";
        LOGGER.warn("Spotify answered 429 ({}), no requests for {} s.", reason, wait.toSeconds());
    }

    private AccessTokenCache.Token requestToken() {
        String credentials = properties.clientId() + ":" + properties.clientSecret();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        try {
            TokenResponse response = restClient.post().uri(properties.tokenUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new ExternalServiceUnavailableException(UNAVAILABLE);
            }
            return new AccessTokenCache.Token(response.accessToken(), Duration.ofSeconds(response.expiresIn() > 0 ? response.expiresIn() : 3600));
        } catch (HttpStatusCodeException e) {
            LOGGER.error("Spotify token request failed with status {}, check SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET.", e.getStatusCode().value());
            throw new ExternalServiceUnavailableException(UNAVAILABLE);
        }
    }

    private static TrackResult toResult(Track track) {
        if (track == null || track.id() == null || track.name() == null) {
            return null;
        }
        List<String> artists = track.artists() == null ? List.of() : track.artists().stream().map(Artist::name).filter(Objects::nonNull).toList();
        String album = track.album() == null ? null : track.album().name();
        return TrackResult.of(track.id(), track.name(), artists, album, pickImage(track.album()));
    }

    /** Spotify lists images widest first (640, 300, 64 px); the 300 px one is enough for cards. */
    private static String pickImage(Album album) {
        if (album == null || album.images() == null || album.images().isEmpty()) {
            return null;
        }
        return album.images().get(album.images().size() > 1 ? 1 : 0).url();
    }

    /** Internal signal that Spotify does not know a track id. */
    private static final class UnknownTrack extends RuntimeException {
        UnknownTrack() {
            super(null, null, false, false);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(Tracks tracks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tracks(List<Track> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Track(String id, String name, List<Artist> artists, Album album) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Artist(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Album(String name, List<Image> images) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Image(String url) {
    }
}
