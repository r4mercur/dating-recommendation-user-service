package com.bjarne.datingrecommendationsuserservice.music;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;
import com.bjarne.datingrecommendationsuserservice.external.MutableClock;
import com.bjarne.datingrecommendationsuserservice.music.dto.TrackResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SpotifyHttpClientTest {

    private static final String TOKEN_URL = "https://accounts.test/api/token";
    private static final String API = "https://api.test/v1";
    private static final String TRACK_ID = "4uLU6hMCjMI75M1A2tKUQC";

    private final MutableClock clock = new MutableClock();
    private MockRestServiceServer server;
    private SpotifyHttpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SpotifyHttpClient(new SpotifyProperties("my-id", "my-secret", API, TOKEN_URL), builder, clock);
    }

    @Test
    void searchesWithAnAppTokenThatIsFetchedOnceAndMapsTheTracks() {
        expectToken("token-1", 3600);
        server.expect(requestTo(API + "/search?q=never%20gonna&type=track&limit=10"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withSuccess("""
                        {"tracks":{"items":[
                          {"id":"%s","name":"Never Gonna Give You Up","artists":[{"name":"Rick Astley"},{"name":"Guest"}],
                           "album":{"name":"Whenever You Need Somebody",
                                    "images":[{"url":"https://img/640.jpg"},{"url":"https://img/300.jpg"},{"url":"https://img/64.jpg"}]},
                           "external_urls":{"spotify":"https://open.spotify.com/track/%s"}},
                          {"id":"%s","name":"No Album","artists":[],"album":{"name":"Single","images":[{"url":"https://img/only.jpg"}]}}
                        ]}}
                        """.formatted(TRACK_ID, TRACK_ID, "0VjIjW4GlUZAMYd2vXMi3b"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/search?q=other&type=track&limit=10"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withSuccess("{\"tracks\":{\"items\":[]}}", MediaType.APPLICATION_JSON));

        List<TrackResult> tracks = client.search("never gonna", 10);

        assertThat(tracks).hasSize(2);
        assertThat(tracks.getFirst()).isEqualTo(new TrackResult(TRACK_ID, "Never Gonna Give You Up", List.of("Rick Astley", "Guest"),
                "Whenever You Need Somebody", "https://img/300.jpg", "https://open.spotify.com/track/" + TRACK_ID));
        assertThat(tracks.get(1).imageUrl()).isEqualTo("https://img/only.jpg");
        assertThat(client.search("other", 10)).isEmpty();
        server.verify();
    }

    @Test
    void theTokenRequestUsesTheClientCredentialsFlow() {
        String basic = Base64.getEncoder().encodeToString("my-id:my-secret".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + basic))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andRespond(withSuccess("{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/search"))).andRespond(withSuccess("{\"tracks\":{\"items\":[]}}", MediaType.APPLICATION_JSON));

        client.search("anything", 10);
        server.verify();
    }

    @Test
    void theTokenIsRenewedBeforeItExpires() {
        // expectations cannot be added once requests were made: the whole sequence is declared up front
        expectToken("token-1", 3600);
        expectSearch("token-1");
        expectSearch("token-1");
        expectToken("token-2", 3600);
        expectSearch("token-2");

        client.search("one", 10);

        clock.advance(Duration.ofMinutes(54));
        client.search("two", 10);

        // 5 minutes before the end of the hour a new token is requested
        clock.advance(Duration.ofMinutes(1));
        client.search("three", 10);

        server.verify();
    }

    @Test
    void aRejectedTokenIsReplacedOnce() {
        expectToken("stale", 3600);
        server.expect(requestTo(containsString("/search"))).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer stale"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("fresh", 3600);
        expectSearch("fresh");

        assertThat(client.search("again", 10)).isEmpty();
        server.verify();
    }

    @Test
    void anExhaustedQuotaMakesTheServiceUnavailableUntilRetryAfterHasPassed() {
        expectToken("token-1", 3600);
        server.expect(requestTo(containsString("/search")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "120").contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"status\":429,\"message\":\"API rate limit exceeded\",\"reason\":\"QUOTA_EXCEEDED\"}}"));

        assertThatThrownBy(() -> client.search("quota", 10))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("Music search is unavailable right now");

        // no further requests during the pause: the server expects none
        clock.advance(Duration.ofSeconds(119));
        assertThatThrownBy(() -> client.search("quota", 10)).isInstanceOf(ExternalServiceUnavailableException.class);
        assertThatThrownBy(() -> client.findTrack(TRACK_ID)).isInstanceOf(ExternalServiceUnavailableException.class);
        server.verify();

        clock.advance(Duration.ofSeconds(2));
        server.reset();
        expectSearch("token-1");
        assertThat(client.search("quota", 10)).isEmpty();
        server.verify();
    }

    @Test
    void unknownTracksAreEmptyAndOtherFailuresAreUnavailable() {
        expectToken("token-1", 3600);
        server.expect(requestTo(API + "/tracks/" + TRACK_ID)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(API + "/tracks/0000000000000000000000")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo(API + "/tracks/1111111111111111111111")).andRespond(withServerError());
        server.expect(requestTo(API + "/tracks/2222222222222222222222")).andRespond(withSuccess("""
                {"id":"2222222222222222222222","name":"Found","artists":[{"name":"A"}],"album":{"name":"B","images":[]}}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.findTrack(TRACK_ID)).isEmpty();
        assertThat(client.findTrack("0000000000000000000000")).isEmpty();
        assertThatThrownBy(() -> client.findTrack("1111111111111111111111")).isInstanceOf(ExternalServiceUnavailableException.class);
        Optional<TrackResult> found = client.findTrack("2222222222222222222222");
        assertThat(found).isPresent();
        assertThat(found.get().imageUrl()).isNull();
        assertThat(found.get().spotifyUrl()).isEqualTo("https://open.spotify.com/track/2222222222222222222222");
        server.verify();
    }

    @Test
    void aBrokenTokenEndpointAndNetworkErrorsAreUnavailable() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_client\"}"));
        assertThatThrownBy(() -> client.search("anything", 10)).isInstanceOf(ExternalServiceUnavailableException.class);

        server.reset();
        server.expect(requestTo(TOKEN_URL)).andRespond(request -> {
            throw new java.io.IOException("connection reset");
        });
        assertThatThrownBy(() -> client.search("anything", 10)).isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void withoutCredentialsNothingIsSent() {
        SpotifyHttpClient unconfigured = new SpotifyHttpClient(new SpotifyProperties("", "", API, TOKEN_URL), RestClient.builder(), clock);

        assertThatThrownBy(() -> unconfigured.search("anything", 10))
                .isInstanceOf(ExternalServiceUnavailableException.class).hasMessage("Music search is unavailable right now");
        assertThatThrownBy(() -> unconfigured.findTrack(TRACK_ID)).isInstanceOf(ExternalServiceUnavailableException.class);
    }

    private void expectToken(String token, int expiresIn) {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"%s\",\"token_type\":\"Bearer\",\"expires_in\":%d}".formatted(token, expiresIn), MediaType.APPLICATION_JSON));
    }

    private void expectSearch(String token) {
        server.expect(requestTo(containsString("/search"))).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andRespond(withSuccess("{\"tracks\":{\"items\":[]}}", MediaType.APPLICATION_JSON));
    }
}
