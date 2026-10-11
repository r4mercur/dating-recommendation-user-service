package com.bjarne.datingrecommendationsuserservice.profile;

import com.bjarne.datingrecommendationsuserservice.ExternalClientsTestConfiguration;
import com.bjarne.datingrecommendationsuserservice.event.UserProfileUpdatedEvent;
import com.bjarne.datingrecommendationsuserservice.games.FakeIgdbClient;
import com.bjarne.datingrecommendationsuserservice.games.GameSearchService;
import com.bjarne.datingrecommendationsuserservice.music.FakeSpotifyClient;
import com.bjarne.datingrecommendationsuserservice.music.MusicSearchService;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.bjarne.datingrecommendationsuserservice.steam.FakeSteamClient;
import com.bjarne.datingrecommendationsuserservice.steam.SteamClient.SteamGame;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.bjarne.datingrecommendationsuserservice.music.FakeSpotifyClient.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Music (Spotify) and favourite games (IGDB, Steam import) in the profile. Spotify, IGDB and Steam are fakes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ExternalClientsTestConfiguration.class)
@RecordApplicationEvents
@Testcontainers(disabledWithoutDocker = true)
class MusicAndGamesIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @MockitoBean
    ProfilePictureStorageService profilePictureStorageService;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    FakeSpotifyClient spotify;
    @Autowired
    FakeIgdbClient igdb;
    @Autowired
    FakeSteamClient steam;
    @Autowired
    MusicSearchService musicSearchService;
    @Autowired
    GameSearchService gameSearchService;
    @Autowired
    ApplicationEvents events;

    record TestUser(String referenceId, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    @BeforeEach
    void resetFakes() {
        spotify.reset();
        igdb.reset();
        steam.reset();
        musicSearchService.clearCaches();
        gameSearchService.clearCaches();
    }

    // ---- music search

    @Test
    void musicSearchNeedsLoginAndAValidQuery() throws Exception {
        TestUser anna = register("anna", "[]", "[]");

        mockMvc.perform(get("/api/music/search").param("q", "song")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/music/search").header("Authorization", anna.bearer())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/music/search").param("q", "a").header("Authorization", anna.bearer())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/music/search").param("q", "x".repeat(101)).header("Authorization", anna.bearer())).andExpect(status().isBadRequest());
        assertThat(spotify.searchCalls.get()).isZero();
    }

    @Test
    void musicSearchReturnsTheContractAndCachesNormalizedQueries() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        spotify.add(20, "Hello World", "Duo One", "Duo Two");

        mockMvc.perform(get("/api/music/search").param("q", "hello").header("Authorization", anna.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].spotifyTrackId").value(id(20)))
                .andExpect(jsonPath("$[0].name").value("Hello World"))
                .andExpect(jsonPath("$[0].artists[0]").value("Duo One"))
                .andExpect(jsonPath("$[0].artists[1]").value("Duo Two"))
                .andExpect(jsonPath("$[0].album").value("Album 20"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://img.example/" + id(20) + ".jpg"))
                .andExpect(jsonPath("$[0].spotifyUrl").value("https://open.spotify.com/track/" + id(20)));

        mockMvc.perform(get("/api/music/search").param("q", "  HELLO ").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(spotify.searchCalls.get()).isEqualTo(1);
    }

    @Test
    void musicSearchReturnsAtMostTenResults() throws Exception {
        TestUser anna = register("anna", "[]", "[]");

        mockMvc.perform(get("/api/music/search").param("q", "song").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    void unavailableSpotifyAnswers503AsProblemDetailAndTheRestKeepsWorking() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        spotify.setDown(true);

        mockMvc.perform(get("/api/music/search").param("q", "song").header("Authorization", anna.bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("Music search is unavailable right now"));

        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/games/search").param("q", "game").header("Authorization", anna.bearer())).andExpect(status().isOk());
    }

    // ---- songs

    @Test
    void songsAreReplacedAsAWholeAndKeepTheOrderAndTheAnthem() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        spotify.add(20, "Hello World", "Duo One", "Duo Two");

        putSongs(anna, songs(id(3), false, id(20), true, id(1), false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].spotifyTrackId").value(id(3)))
                .andExpect(jsonPath("$[0].name").value("Song 3"))
                .andExpect(jsonPath("$[0].anthem").value(false))
                .andExpect(jsonPath("$[1].spotifyTrackId").value(id(20)))
                .andExpect(jsonPath("$[1].artists.length()").value(2))
                .andExpect(jsonPath("$[1].album").value("Album 20"))
                .andExpect(jsonPath("$[1].imageUrl").value("https://img.example/" + id(20) + ".jpg"))
                .andExpect(jsonPath("$[1].spotifyUrl").value("https://open.spotify.com/track/" + id(20)))
                .andExpect(jsonPath("$[1].anthem").value(true))
                .andExpect(jsonPath("$[2].spotifyTrackId").value(id(1)));

        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.songs.length()").value(3))
                .andExpect(jsonPath("$.songs[1].anthem").value(true));

        putSongs(anna, songs(id(5), true)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.songs.length()").value(1))
                .andExpect(jsonPath("$.songs[0].spotifyTrackId").value(id(5)));

        putSongs(anna, "[]").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(jsonPath("$.songs.length()").value(0));
    }

    @Test
    void atMostFourSongsAndOneAnthemAreAllowed() throws Exception {
        TestUser anna = register("anna", "[]", "[]");

        putSongs(anna, songs(id(1), false, id(2), false, id(3), false, id(4), true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        putSongs(anna, songs(id(1), false, id(2), false, id(3), false, id(4), false, id(5), false)).andExpect(status().isBadRequest());
        putSongs(anna, songs(id(1), true, id(2), true)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("anthem")));

        // failed requests change nothing
        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(jsonPath("$.songs.length()").value(4));
    }

    @Test
    void unknownMalformedAndDuplicateTracksAreRejected() throws Exception {
        TestUser anna = register("anna", "[]", "[]");

        putSongs(anna, songs(id(1), false, id(99), false)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString(id(99))));
        putSongs(anna, songs("not-a-track-id", false)).andExpect(status().isBadRequest());
        putSongs(anna, songs(id(1), false, id(1), false)).andExpect(status().isBadRequest());
        putSongs(anna, "[{\"anthem\":true}]").andExpect(status().isBadRequest());
        putSongs(anna, "null").andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/me/songs").contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(jsonPath("$.songs.length()").value(0));
    }

    @Test
    void songsOnTheProfileAreCopiesAndShowingProfilesNeverCallsSpotify() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        TestUser ben = register("ben", "[]", "[]");
        putSongs(anna, songs(id(2), true)).andExpect(status().isOk());

        // Spotify is gone and the catalog changed: the profile still shows the copy
        spotify.setDown(true);
        int calls = spotify.searchCalls.get() + spotify.trackCalls.get();

        mockMvc.perform(get("/api/user").param("referenceId", anna.referenceId()).header("Authorization", ben.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs[0].name").value("Song 2"))
                .andExpect(jsonPath("$.songs[0].anthem").value(true));
        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(status().isOk());
        assertThat(spotify.searchCalls.get() + spotify.trackCalls.get()).isEqualTo(calls);

        // saving songs that are already on the profile needs no Spotify call either, unknown tracks do
        musicSearchService.clearCaches();
        putSongs(anna, songs(id(2), false)).andExpect(status().isOk());
        putSongs(anna, songs(id(2), false, id(3), false)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Music search is unavailable right now"));
        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(jsonPath("$.songs.length()").value(1));
    }

    @Test
    void changingSongsAndGamesPublishesAProfileUpdate() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        long before = events.stream(UserProfileUpdatedEvent.class).count();

        putSongs(anna, songs(id(1), false)).andExpect(status().isOk());
        putGames(anna, "[{\"igdbId\":1}]").andExpect(status().isOk());
        putSongs(anna, songs(id(1), false, id(1), false)).andExpect(status().isBadRequest());

        assertThat(events.stream(UserProfileUpdatedEvent.class).count()).isEqualTo(before + 2);
    }

    // ---- games

    @Test
    void gameSearchReturnsTheContractAndHandlesOutages() throws Exception {
        TestUser anna = register("anna", "[]", "[]");

        mockMvc.perform(get("/api/games/search").param("q", "game 3").header("Authorization", anna.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].igdbId").value(3))
                .andExpect(jsonPath("$[0].name").value("Game 3"))
                .andExpect(jsonPath("$[0].coverUrl").value("https://images.igdb.com/igdb/image/upload/t_cover_big/co3.jpg"))
                .andExpect(jsonPath("$[0].year").value(2003))
                .andExpect(jsonPath("$[0].platforms[0]").value("PC"))
                .andExpect(jsonPath("$[0].steamAppId").value(1003));
        mockMvc.perform(get("/api/games/search").param("q", "game 12").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$[0].steamAppId").doesNotExist());

        mockMvc.perform(get("/api/games/search").param("q", "g").header("Authorization", anna.bearer())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/games/search").param("q", "game")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/games/search").param("q", "game 3").header("Authorization", anna.bearer()));
        assertThat(igdb.searchCalls.get()).isEqualTo(2);

        igdb.setDown(true);
        mockMvc.perform(get("/api/games/search").param("q", "game 5").header("Authorization", anna.bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Game search is unavailable right now"));
    }

    @Test
    void gamesAreReplacedAsAWholeWithAtMostSixInOrder() throws Exception {
        TestUser anna = register("anna", "[]", "[]");

        putGames(anna, "[{\"igdbId\":3},{\"igdbId\":1},{\"igdbId\":12}]").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].igdbId").value(3))
                .andExpect(jsonPath("$[0].name").value("Game 3"))
                .andExpect(jsonPath("$[0].coverUrl").value("https://images.igdb.com/igdb/image/upload/t_cover_big/co3.jpg"))
                .andExpect(jsonPath("$[0].year").value(2003))
                .andExpect(jsonPath("$[0].platforms.length()").value(2))
                .andExpect(jsonPath("$[0].steamAppId").value(1003))
                .andExpect(jsonPath("$[1].igdbId").value(1))
                .andExpect(jsonPath("$[2].igdbId").value(12))
                .andExpect(jsonPath("$[2].steamAppId").doesNotExist());

        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.games.length()").value(3))
                .andExpect(jsonPath("$.games[2].igdbId").value(12));

        putGames(anna, ids(1, 2, 3, 4, 5, 6)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        putGames(anna, ids(1, 2, 3, 4, 5, 6, 7)).andExpect(status().isBadRequest());
        putGames(anna, "[]").andExpect(status().isOk());
        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(jsonPath("$.games.length()").value(0));
    }

    @Test
    void unknownAndDuplicateGamesAreRejected() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        putGames(anna, ids(1, 2)).andExpect(status().isOk());

        putGames(anna, ids(1, 999)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("999")));
        putGames(anna, ids(1, 1)).andExpect(status().isBadRequest());
        putGames(anna, "[{}]").andExpect(status().isBadRequest());
        putGames(anna, "[{\"igdbId\":-4}]").andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/me/games").contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(jsonPath("$.games.length()").value(2));
    }

    @Test
    void gamesOnTheProfileAreCopiesAndShowingProfilesNeverCallsIgdb() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        TestUser ben = register("ben", "[]", "[]");
        putGames(anna, ids(2)).andExpect(status().isOk());

        igdb.setDown(true);
        int calls = igdb.searchCalls.get() + igdb.idCalls.get();

        mockMvc.perform(get("/api/user").param("referenceId", anna.referenceId()).header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.games[0].name").value("Game 2"));
        assertThat(igdb.searchCalls.get() + igdb.idCalls.get()).isEqualTo(calls);

        gameSearchService.clearCaches();
        putGames(anna, ids(2)).andExpect(status().isOk());
        putGames(anna, ids(2, 3)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Game search is unavailable right now"));
    }

    // ---- steam import

    @Test
    void steamSuggestionsAreSortedByPlaytimeAndMappedToIgdbGames() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        steam.vanity("annagamer", FakeSteamClient.PUBLIC_ID);
        steam.library(FakeSteamClient.PUBLIC_ID, List.of(
                new SteamGame(1002, "Game Two", 60),
                new SteamGame(555000, "Obscure Indie", 120),
                new SteamGame(1001, "Game One", 605)));

        for (String profile : List.of("annagamer", FakeSteamClient.PUBLIC_ID,
                "https://steamcommunity.com/id/annagamer/", "steamcommunity.com/id/annagamer",
                "https://steamcommunity.com/profiles/" + FakeSteamClient.PUBLIC_ID + "/")) {
            steamSuggestions(anna, profile).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].igdbId").value(1))
                    .andExpect(jsonPath("$[0].name").value("Game 1"))
                    .andExpect(jsonPath("$[0].coverUrl").value("https://images.igdb.com/igdb/image/upload/t_cover_big/co1.jpg"))
                    .andExpect(jsonPath("$[0].steamAppId").value(1001))
                    .andExpect(jsonPath("$[0].playtimeHours").value(10.1))
                    // no IGDB match: the Steam name and the Steam header image
                    .andExpect(jsonPath("$[1].igdbId").doesNotExist())
                    .andExpect(jsonPath("$[1].name").value("Obscure Indie"))
                    .andExpect(jsonPath("$[1].coverUrl").value("https://cdn.cloudflare.steamstatic.com/steam/apps/555000/header.jpg"))
                    .andExpect(jsonPath("$[1].steamAppId").value(555000))
                    .andExpect(jsonPath("$[1].playtimeHours").value(2.0))
                    .andExpect(jsonPath("$[2].igdbId").value(2))
                    .andExpect(jsonPath("$[2].playtimeHours").value(1.0));
        }

        // suggestions are not saved
        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(jsonPath("$.games.length()").value(0));
    }

    @Test
    void steamSuggestionsAreLimitedToTwentyGames() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        List<SteamGame> library = new ArrayList<>();
        IntStream.rangeClosed(1, 25).forEach(number -> library.add(new SteamGame(700000 + number, "Steam " + number, number)));
        steam.library(FakeSteamClient.PUBLIC_ID, library);

        steamSuggestions(anna, FakeSteamClient.PUBLIC_ID).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].steamAppId").value(700025))
                .andExpect(jsonPath("$[19].steamAppId").value(700006));
    }

    @Test
    void aPrivateSteamProfileAnswers422() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        steam.library(FakeSteamClient.PRIVATE_ID, null);
        steam.library("76561198000000003", List.of());

        steamSuggestions(anna, FakeSteamClient.PRIVATE_ID).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(containsString("Your Steam game details are private")));
        steamSuggestions(anna, "76561198000000003").andExpect(status().isUnprocessableContent());
    }

    @Test
    void invalidSteamInputAndUnknownProfilesAreRejected() throws Exception {
        TestUser anna = register("anna", "[]", "[]");

        steamSuggestions(anna, "no such vanity").andExpect(status().isBadRequest());
        steamSuggestions(anna, "https://example.com/id/annagamer").andExpect(status().isBadRequest());
        steamSuggestions(anna, "https://steamcommunity.com/groups/foo").andExpect(status().isBadRequest());
        steamSuggestions(anna, "https://steamcommunity.com/profiles/123").andExpect(status().isBadRequest());
        steamSuggestions(anna, "   ").andExpect(status().isBadRequest());
        steamSuggestions(anna, "missingvanity").andExpect(status().isNotFound());
        mockMvc.perform(post("/api/me/games/steam-suggestions").contentType(MediaType.APPLICATION_JSON).content("{\"profile\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void steamOrIgdbOutagesAnswer503() throws Exception {
        TestUser anna = register("anna", "[]", "[]");
        steam.library(FakeSteamClient.PUBLIC_ID, List.of(new SteamGame(1001, "Game One", 60)));

        steam.setDown(true);
        steamSuggestions(anna, FakeSteamClient.PUBLIC_ID).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Steam is unavailable right now"));

        steam.setDown(false);
        igdb.setDown(true);
        steamSuggestions(anna, FakeSteamClient.PUBLIC_ID).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Game search is unavailable right now"));
    }

    // ---- profiles: common ground

    @Test
    void theProfileShowsWhatTheViewerHasInCommon() throws Exception {
        TestUser anna = register("anna", "[\"music\",\"travel\",\"art\"]", "[\"hiking\",\"reading\"]");
        TestUser ben = register("ben", "[\"art\",\"music\"]", "[\"reading\",\"cooking\"]");
        TestUser cleo = register("cleo", "[]", "[]");

        spotify.add(20, "Common Song", "The Killers");
        spotify.add(21, "Other Killers Song", "the killers");
        putSongs(anna, songs(id(1), false, id(20), true)).andExpect(status().isOk());
        putSongs(ben, songs(id(21), true, id(1), false)).andExpect(status().isOk());
        putGames(anna, ids(1, 2, 3)).andExpect(status().isOk());
        putGames(ben, ids(3, 5, 1)).andExpect(status().isOk());

        mockMvc.perform(get("/api/user").param("referenceId", ben.referenceId()).header("Authorization", anna.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value(ben.referenceId()))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.songs.length()").value(2))
                .andExpect(jsonPath("$.games.length()").value(3))
                .andExpect(jsonPath("$.common.interests.length()").value(2))
                .andExpect(jsonPath("$.common.interests[0]").value("music"))
                .andExpect(jsonPath("$.common.interests[1]").value("art"))
                .andExpect(jsonPath("$.common.hobbies[0]").value("reading"))
                // The Killers appear on different tracks of both, artist names are compared case-insensitively
                .andExpect(jsonPath("$.common.artists.length()").value(2))
                .andExpect(jsonPath("$.common.artists[0]").value("Artist 1"))
                .andExpect(jsonPath("$.common.artists[1]").value("The Killers"))
                .andExpect(jsonPath("$.common.games.length()").value(2))
                .andExpect(jsonPath("$.common.games[0]").value(1))
                .andExpect(jsonPath("$.common.games[1]").value(3));

        // nothing in common: empty lists, never missing
        mockMvc.perform(get("/api/user").param("referenceId", cleo.referenceId()).header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.common.interests.length()").value(0))
                .andExpect(jsonPath("$.common.hobbies.length()").value(0))
                .andExpect(jsonPath("$.common.artists.length()").value(0))
                .andExpect(jsonPath("$.common.games.length()").value(0));

        // other lists show songs and games but no "common" section
        mockMvc.perform(post("/api/me/reactions").header("Authorization", anna.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetReferenceId\":\"" + ben.referenceId() + "\",\"type\":\"LIKE\"}")).andExpect(status().isCreated());
        mockMvc.perform(get("/api/me/likes-received").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$[0].user.songs.length()").value(2))
                .andExpect(jsonPath("$[0].user.games.length()").value(3))
                .andExpect(jsonPath("$[0].user.common").doesNotExist());
    }

    // ---- helpers

    private ResultActions putSongs(TestUser user, String body) throws Exception {
        return mockMvc.perform(put("/api/me/songs").header("Authorization", user.bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putGames(TestUser user, String body) throws Exception {
        return mockMvc.perform(put("/api/me/games").header("Authorization", user.bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions steamSuggestions(TestUser user, String profile) throws Exception {
        return mockMvc.perform(post("/api/me/games/steam-suggestions").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"profile\":\"" + profile + "\"}"));
    }

    /** JSON list from alternating track ids and anthem flags. */
    private static String songs(Object... idsAndAnthems) {
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < idsAndAnthems.length; index += 2) {
            entries.add("{\"spotifyTrackId\":\"%s\",\"anthem\":%s}".formatted(idsAndAnthems[index], idsAndAnthems[index + 1]));
        }
        return "[" + String.join(",", entries) + "]";
    }

    private static String ids(long... igdbIds) {
        return "[" + java.util.Arrays.stream(igdbIds).mapToObj(id -> "{\"igdbId\":" + id + "}").reduce((a, b) -> a + "," + b).orElse("") + "]";
    }

    private TestUser register(String name, String interests, String hobbies) throws Exception {
        String email = name + "-" + UUID.randomUUID() + "@example.com";
        String response = mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","email":"%s","password":"password123","birthDate":"1995-05-05","gender":"FEMALE",
                         "interests":%s,"hobbies":%s,
                         "address":{"city":"Berlin","zipCode":"10115","country":"DE"}}
                        """.formatted(name, email, interests, hobbies)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String referenceId = JsonPath.read(response, "$.referenceId");

        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new TestUser(referenceId, JsonPath.read(login, "$.access_token"));
    }
}
