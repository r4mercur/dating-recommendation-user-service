package com.bjarne.datingrecommendationsuserservice.steam;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;
import com.bjarne.datingrecommendationsuserservice.steam.SteamClient.SteamGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SteamHttpClientTest {

    private static final String API = "https://steam.test";
    private static final String STEAM_ID = "76561198000000001";

    private MockRestServiceServer server;
    private SteamHttpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SteamHttpClient(new SteamProperties("secret-key", API), builder);
    }

    @Test
    void resolvesVanityNames() {
        server.expect(requestTo(API + "/ISteamUser/ResolveVanityURL/v0001/?key=secret-key&vanityurl=annagamer"))
                .andRespond(withSuccess("{\"response\":{\"steamid\":\"" + STEAM_ID + "\",\"success\":1}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/ISteamUser/ResolveVanityURL/v0001/?key=secret-key&vanityurl=nobody"))
                .andRespond(withSuccess("{\"response\":{\"success\":42,\"message\":\"No match\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.resolveVanity("annagamer")).contains(STEAM_ID);
        assertThat(client.resolveVanity("nobody")).isEmpty();
        server.verify();
    }

    @Test
    void readsTheOwnedGames() {
        server.expect(requestTo(API + "/IPlayerService/GetOwnedGames/v0001/?key=secret-key&steamid=" + STEAM_ID
                        + "&include_appinfo=1&include_played_free_games=1&format=json"))
                .andRespond(withSuccess("""
                        {"response":{"game_count":2,"games":[
                          {"appid":730,"name":"Counter-Strike 2","playtime_forever":6000,"img_icon_url":"abc"},
                          {"appid":620,"playtime_forever":0}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        Optional<List<SteamGame>> games = client.ownedGames(STEAM_ID);

        assertThat(games).isPresent();
        assertThat(games.get()).containsExactly(new SteamGame(730, "Counter-Strike 2", 6000), new SteamGame(620, "App 620", 0));
        server.verify();
    }

    @Test
    void privateGameDetailsAreAnEmptyResponseWithoutGameCount() {
        server.expect(requestTo(API + "/IPlayerService/GetOwnedGames/v0001/?key=secret-key&steamid=" + STEAM_ID
                        + "&include_appinfo=1&include_played_free_games=1&format=json"))
                .andRespond(withSuccess("{\"response\":{}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/IPlayerService/GetRecentlyPlayedGames/v1/?key=secret-key&steamid=" + STEAM_ID + "&count=20&format=json"))
                .andRespond(withSuccess("{\"response\":{\"total_count\":0}}", MediaType.APPLICATION_JSON));

        assertThat(client.ownedGames(STEAM_ID)).isEmpty();
        // a visible profile without recent games is not private
        assertThat(client.recentlyPlayedGames(STEAM_ID)).contains(List.of());
        server.verify();
    }

    @Test
    void failuresAreUnavailableAndNeverRevealTheKey() {
        server.expect(requestTo(API + "/IPlayerService/GetOwnedGames/v0001/?key=secret-key&steamid=" + STEAM_ID
                        + "&include_appinfo=1&include_played_free_games=1&format=json"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(API + "/ISteamUser/ResolveVanityURL/v0001/?key=secret-key&vanityurl=x1"))
                .andRespond(request -> {
                    throw new java.io.IOException("reset while calling " + request.getURI());
                });

        assertThatThrownBy(() -> client.ownedGames(STEAM_ID)).isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("Steam is unavailable right now").hasNoCause();
        assertThatThrownBy(() -> client.resolveVanity("x1")).isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("Steam is unavailable right now").hasNoCause();
    }

    @Test
    void withoutAKeyNothingIsSent() {
        SteamHttpClient unconfigured = new SteamHttpClient(new SteamProperties(null, API), RestClient.builder());

        assertThatThrownBy(() -> unconfigured.ownedGames(STEAM_ID)).isInstanceOf(ExternalServiceUnavailableException.class);
        assertThatThrownBy(() -> unconfigured.resolveVanity("anna")).isInstanceOf(ExternalServiceUnavailableException.class);
    }
}
