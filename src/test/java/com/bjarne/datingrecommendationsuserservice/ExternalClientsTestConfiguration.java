package com.bjarne.datingrecommendationsuserservice;

import com.bjarne.datingrecommendationsuserservice.games.FakeIgdbClient;
import com.bjarne.datingrecommendationsuserservice.games.IgdbClient;
import com.bjarne.datingrecommendationsuserservice.music.FakeSpotifyClient;
import com.bjarne.datingrecommendationsuserservice.music.SpotifyClient;
import com.bjarne.datingrecommendationsuserservice.steam.FakeSteamClient;
import com.bjarne.datingrecommendationsuserservice.steam.SteamClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces Spotify, IGDB and Steam with fakes: tests never call the real services.
 * Use with {@code @Import(ExternalClientsTestConfiguration.class)}.
 */
@TestConfiguration
public class ExternalClientsTestConfiguration {

    @Bean
    @Primary
    public FakeSpotifyClient fakeSpotifyClient() {
        return new FakeSpotifyClient();
    }

    @Bean
    @Primary
    public FakeIgdbClient fakeIgdbClient() {
        return new FakeIgdbClient();
    }

    @Bean
    @Primary
    public FakeSteamClient fakeSteamClient() {
        return new FakeSteamClient();
    }
}
