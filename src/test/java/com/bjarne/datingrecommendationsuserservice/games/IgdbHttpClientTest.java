package com.bjarne.datingrecommendationsuserservice.games;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;
import com.bjarne.datingrecommendationsuserservice.external.MutableClock;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IgdbHttpClientTest {

    private static final String TOKEN_URL = "https://id.test/oauth2/token";
    private static final String API = "https://api.test/v4";

    private MockRestServiceServer server;
    private IgdbHttpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // a high limit, the rate limiter has its own test
        client = new IgdbHttpClient(new IgdbProperties("twitch-id", "twitch-secret", API, TOKEN_URL, 1000), builder, new MutableClock());
    }

    @Test
    void searchesWithATwitchAppTokenAndMapsCoverYearPlatformsAndSteamId() {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("client_id=twitch-id")))
                .andExpect(content().string(containsString("client_secret=twitch-secret")))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andRespond(withSuccess("{\"access_token\":\"app-token\",\"expires_in\":5587808,\"token_type\":\"bearer\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/games"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Client-ID", "twitch-id"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer app-token"))
                .andExpect(content().string(containsString("search \"witcher\";")))
                .andExpect(content().string(containsString("limit 10;")))
                .andRespond(withSuccess("""
                        [
                          {"id":1942,"name":"The Witcher 3: Wild Hunt","cover":{"id":1,"image_id":"co1wyy"},
                           "first_release_date":1431993600,
                           "platforms":[{"id":6,"abbreviation":"PC"},{"id":48,"abbreviation":"PS4"},{"id":130,"name":"Nintendo Switch"},{"id":6,"abbreviation":"PC"}],
                           "external_games":[{"id":1,"uid":"gog-1207664643","external_game_source":5},{"id":2,"uid":"292030","external_game_source":1}]},
                          {"id":77,"name":"Obscure","platforms":[]},
                          {"name":"Without id"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<GameResult> games = client.search("witcher", 10);

        assertThat(games).hasSize(2);
        assertThat(games.getFirst()).isEqualTo(new GameResult(1942, "The Witcher 3: Wild Hunt",
                "https://images.igdb.com/igdb/image/upload/t_cover_big/co1wyy.jpg", 2015, List.of("PC", "PS4", "Nintendo Switch"), 292030L));
        assertThat(games.get(1)).isEqualTo(new GameResult(77, "Obscure", null, null, List.of(), null));
        server.verify();
    }

    @Test
    void quotesInTheSearchTextCannotBreakOutOfTheQuery() {
        expectToken();
        server.expect(requestTo(API + "/games"))
                .andExpect(content().string(containsString("search \"say \\\"hi\\\" \\\\ now\";")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.search("say \"hi\" \\ now", 10)).isEmpty();
        server.verify();
    }

    @Test
    void looksUpGamesByIdInOneRequest() {
        expectToken();
        server.expect(requestTo(API + "/games"))
                .andExpect(content().string(containsString("where id = (1,2,3);")))
                .andExpect(content().string(containsString("limit 3;")))
                .andRespond(withSuccess("[{\"id\":2,\"name\":\"Two\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.findByIds(List.of(1L, 2L, 3L))).extracting(GameResult::igdbId).containsExactly(2L);
        assertThat(client.findByIds(List.of())).isEmpty();
        server.verify();
    }

    @Test
    void mapsSteamAppsToGamesThroughExternalGames() {
        expectToken();
        server.expect(requestTo(API + "/external_games"))
                .andExpect(content().string(containsString("where external_game_source = 1 & uid = (\"292030\",\"730\",\"999\");")))
                .andRespond(withSuccess("""
                        [
                          {"id":10,"uid":"292030","game":{"id":1942,"name":"The Witcher 3","cover":{"image_id":"co1wyy"},"first_release_date":1431993600,
                                                          "platforms":[{"abbreviation":"PC"}]}},
                          {"id":11,"uid":"730","game":{"id":1,"name":"Counter-Strike 2"}},
                          {"id":12,"uid":"not-a-number","game":{"id":5,"name":"Ignored"}}
                        ]
                        """, MediaType.APPLICATION_JSON));

        Map<Long, GameResult> found = client.findBySteamAppIds(List.of(292030L, 730L, 999L));

        assertThat(found).containsOnlyKeys(292030L, 730L);
        assertThat(found.get(292030L).igdbId()).isEqualTo(1942);
        assertThat(found.get(292030L).steamAppId()).isEqualTo(292030L);
        assertThat(found.get(730L).name()).isEqualTo("Counter-Strike 2");
        assertThat(client.findBySteamAppIds(List.of())).isEmpty();
        server.verify();
    }

    @Test
    void theTokenIsReusedAndReplacedWhenItIsRejected() {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URL)).andRespond(withSuccess("{\"access_token\":\"stale\",\"expires_in\":5000000}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/games")).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer stale")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/games")).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer stale")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URL)).andRespond(withSuccess("{\"access_token\":\"fresh\",\"expires_in\":5000000}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/games")).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fresh")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.search("one", 10)).isEmpty();
        assertThat(client.search("two", 10)).isEmpty();
        server.verify();
    }

    @Test
    void failuresMakeTheServiceUnavailable() {
        expectToken();
        server.expect(requestTo(API + "/games")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(API + "/games")).andRespond(withServerError());

        assertThatThrownBy(() -> client.search("limit", 10)).isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("Game search is unavailable right now");
        assertThatThrownBy(() -> client.search("error", 10)).isInstanceOf(ExternalServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void aBrokenTokenEndpointIsUnavailable() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"invalid client secret\"}"));

        assertThatThrownBy(() -> client.search("anything", 10)).isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void withoutCredentialsNothingIsSent() {
        IgdbHttpClient unconfigured = new IgdbHttpClient(new IgdbProperties(null, null, API, TOKEN_URL, null), RestClient.builder(), new MutableClock());

        assertThatThrownBy(() -> unconfigured.search("anything", 10)).isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("Game search is unavailable right now");
        assertThatThrownBy(() -> unconfigured.findByIds(List.of(1L))).isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void aBurstThatWouldHaveToWaitTooLongIsRefusedInsteadOfQueued() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer slowServer = MockRestServiceServer.bindTo(builder).build();
        // one request per second: slots at 0 s, 1.1 s, 2.2 s ... and nobody waits longer than two seconds
        IgdbHttpClient slow = new IgdbHttpClient(new IgdbProperties("id", "secret", API, TOKEN_URL, 1), builder, new MutableClock());
        slowServer.expect(ExpectedCount.manyTimes(), requestTo(TOKEN_URL)).andRespond(withSuccess("{\"access_token\":\"t\",\"expires_in\":5000000}", MediaType.APPLICATION_JSON));
        slowServer.expect(ExpectedCount.manyTimes(), requestTo(API + "/games")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        java.util.concurrent.atomic.AtomicInteger refused = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger served = new java.util.concurrent.atomic.AtomicInteger();
        try (java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(5)) {
            List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int request = 0; request < 5; request++) {
                String query = "burst " + request;
                futures.add(executor.submit(() -> {
                    try {
                        slow.search(query, 10);
                        served.incrementAndGet();
                    } catch (ExternalServiceUnavailableException e) {
                        refused.incrementAndGet();
                    }
                }));
            }
            for (java.util.concurrent.Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(served.get()).isGreaterThanOrEqualTo(1);
        assertThat(refused.get()).isGreaterThanOrEqualTo(2);
        assertThat(served.get() + refused.get()).isEqualTo(5);
    }

    private void expectToken() {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"app-token\",\"expires_in\":5587808,\"token_type\":\"bearer\"}", MediaType.APPLICATION_JSON));
    }
}
