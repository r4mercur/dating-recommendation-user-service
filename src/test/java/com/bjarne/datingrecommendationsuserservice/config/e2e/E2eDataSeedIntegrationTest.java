package com.bjarne.datingrecommendationsuserservice.config.e2e;

import com.bjarne.datingrecommendationsuserservice.config.dev.DevDataSeedRunner;
import com.bjarne.datingrecommendationsuserservice.config.e2e.E2eUsers.E2eUser;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.games.UserGame;
import com.bjarne.datingrecommendationsuserservice.games.UserGameRepository;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import com.bjarne.datingrecommendationsuserservice.music.UserSong;
import com.bjarne.datingrecommendationsuserservice.music.UserSongRepository;
import com.bjarne.datingrecommendationsuserservice.music.dto.TrackResult;
import com.bjarne.datingrecommendationsuserservice.interaction.BlockRepository;
import com.bjarne.datingrecommendationsuserservice.interaction.MatchRepository;
import com.bjarne.datingrecommendationsuserservice.interaction.Reaction;
import com.bjarne.datingrecommendationsuserservice.interaction.ReactionRepository;
import com.bjarne.datingrecommendationsuserservice.interaction.ReactionType;
import com.bjarne.datingrecommendationsuserservice.repository.PostalCodeRepository;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.service.GeocodingService;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.bjarne.datingrecommendationsuserservice.service.TagService;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev", "e2e"})
@TestPropertySource(properties = "recommender.solr.data-seed-enabled=true")
@Testcontainers(disabledWithoutDocker = true)
class E2eDataSeedIntegrationTest {

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
    E2eDataSeedRunner e2eDataSeedRunner;
    @Autowired
    UserRepository userRepository;
    @Autowired
    ReactionRepository reactionRepository;
    @Autowired
    MatchRepository matchRepository;
    @Autowired
    BlockRepository blockRepository;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    UserSongRepository userSongRepository;
    @Autowired
    UserGameRepository userGameRepository;
    @Autowired
    UserService userService;
    @Autowired
    TagService tagService;
    @Autowired
    GeocodingService geocodingService;
    @Autowired
    PostalCodeRepository postalCodeRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void seederResetsChangedUsersAndRelationsToTheFixedValues() throws Exception {
        String token = token(E2eUsers.ALICE.email(), E2eUsers.PASSWORD);
        String bearer = "Bearer " + token;

        mockMvc.perform(patch("/api/me").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Changed","bio":"changed","birthDate":"1980-01-01","gender":"MALE",
                                 "interests":["music"],"hobbies":["cooking"],
                                 "address":{"city":"München","zipCode":"80331","country":"DE"}}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/me/preferences").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seekingGenders\":[\"DIVERSE\"],\"minAge\":50,\"maxAge\":60,\"maxDistanceKm\":5}"))
                .andExpect(status().isOk());
        // carol already likes alice, so alice' like creates the match the E2E tests rely on
        mockMvc.perform(post("/api/me/reactions").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetReferenceId\":\"" + E2eUsers.CAROL.referenceId() + "\",\"type\":\"LIKE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.match.user.referenceId").value(E2eUsers.CAROL.referenceId()));
        mockMvc.perform(post("/api/me/reactions").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetReferenceId\":\"" + E2eUsers.BOB.referenceId() + "\",\"type\":\"PASS\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/me/blocks").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceId\":\"" + E2eUsers.BOB.referenceId() + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/me/password").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + E2eUsers.PASSWORD + "\",\"newPassword\":\"another-password\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/me/status").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        // songs and games are reset as well
        Long aliceId = user(E2eUsers.ALICE).getId();
        transactionTemplate.executeWithoutResult(_ -> {
            userSongRepository.deleteAllByUserId(aliceId);
            userSongRepository.save(new UserSong(aliceId, 0, TrackResult.of("9999999999999999999999", "Stray", List.of("Nobody"), null, null), true));
            userGameRepository.deleteAllByUserId(aliceId);
            userGameRepository.save(new UserGame(aliceId, 0, GameResult.of(555, "Stray", null, null, List.of(), null)));
        });

        e2eDataSeedRunner.run(new DefaultApplicationArguments());

        E2eUsers.ALL.forEach(this::assertHasFixedValues);

        // only the fixed relation is left: carol likes alice, no match, no block
        assertThat(reactionRepository.findAll())
                .filteredOn(reaction -> isE2eUser(reaction.getFromUser()) || isE2eUser(reaction.getToUser()))
                .extracting(reaction -> reaction.getFromUser().getReferenceId(), reaction -> reaction.getToUser().getReferenceId(), Reaction::getType)
                .containsExactly(tuple(E2eUsers.CAROL.referenceId(), E2eUsers.ALICE.referenceId(), ReactionType.LIKE));
        assertThat(matchRepository.findAll()).noneMatch(match -> isE2eUser(match.getUserLow()) || isE2eUser(match.getUserHigh()));
        assertThat(blockRepository.findAll()).noneMatch(block -> isE2eUser(block.getBlocker()) || isE2eUser(block.getBlocked()));

        String bearerAfterReset = "Bearer " + token(E2eUsers.ALICE.email(), E2eUsers.PASSWORD);
        mockMvc.perform(get("/api/me/likes-received").header("Authorization", bearerAfterReset))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.referenceId").value(E2eUsers.CAROL.referenceId()));
        mockMvc.perform(get("/api/me/matches").header("Authorization", bearerAfterReset))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theFixedUsersHaveOverlappingSongsAndGamesWithoutCallingExternalServices() throws Exception {
        String bearer = "Bearer " + token(E2eUsers.ALICE.email(), E2eUsers.PASSWORD);

        mockMvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.songs.length()").value(3))
                .andExpect(jsonPath("$.songs[0].name").value("Mr. Brightside"))
                .andExpect(jsonPath("$.songs[0].anthem").value(true))
                .andExpect(jsonPath("$.songs[0].spotifyUrl").value("https://open.spotify.com/track/003vvx7Niy0yvhvHt4a68B"))
                .andExpect(jsonPath("$.games.length()").value(3))
                .andExpect(jsonPath("$.games[0].igdbId").value(1942));

        mockMvc.perform(get("/api/user").param("referenceId", E2eUsers.BOB.referenceId()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs[0].name").value("Somebody Told Me"))
                .andExpect(jsonPath("$.songs[0].anthem").value(true))
                .andExpect(jsonPath("$.common.interests.length()").value(2))
                .andExpect(jsonPath("$.common.hobbies[0]").value("climbing"))
                .andExpect(jsonPath("$.common.artists.length()").value(2))
                .andExpect(jsonPath("$.common.artists[0]").value("The Killers"))
                .andExpect(jsonPath("$.common.artists[1]").value("The Weeknd"))
                .andExpect(jsonPath("$.common.games.length()").value(2))
                .andExpect(jsonPath("$.common.games[0]").value(1942))
                .andExpect(jsonPath("$.common.games[1]").value(14593));

        // carol shares tags with alice, but no artists or games
        mockMvc.perform(get("/api/user").param("referenceId", E2eUsers.CAROL.referenceId()).header("Authorization", bearer))
                .andExpect(jsonPath("$.common.artists.length()").value(0))
                .andExpect(jsonPath("$.common.games.length()").value(0));
    }

    @Test
    void blockedUserCannotLogIn() throws Exception {
        login(E2eUsers.BLOCKED.email(), E2eUsers.PASSWORD).andExpect(status().isUnauthorized());
        login(E2eUsers.BOB.email(), E2eUsers.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void devSeedIsDeterministicAndStaysAwayFromTheE2eUsers() {
        List<User> first = new DevDataSeedRunner(userService, tagService, postalCodeRepository, true).generateUsers();
        List<User> second = new DevDataSeedRunner(userService, tagService, postalCodeRepository, true).generateUsers();

        assertThat(first).isNotEmpty();
        assertThat(first)
                .extracting(User::getName, User::getEmail, User::getBirthDate, User::getInterests, User::getHobbies,
                        user -> user.getAddress().getZipCode(), User::getGender)
                .containsExactlyElementsOf(second.stream()
                        .map(user -> tuple(user.getName(), user.getEmail(), user.getBirthDate(), user.getInterests(),
                                user.getHobbies(), user.getAddress().getZipCode(), user.getGender()))
                        .toList());

        assertThat(first).allSatisfy(user -> {
            assertThat(user.getAddress().getCity()).isNotEqualToIgnoringCase(E2eUsers.CITY);
            assertThat(user.getInterests()).doesNotContainAnyElementsOf(E2eUsers.RESERVED_INTERESTS);
            assertThat(user.getHobbies()).doesNotContainAnyElementsOf(E2eUsers.RESERVED_HOBBIES);
        });

        // the dev seeder ran on the empty database at startup, the E2E seeder added the fixed users
        assertThat(userRepository.count()).isEqualTo(first.size() + E2eUsers.ALL.size());
    }

    private void assertHasFixedValues(E2eUser spec) {
        User user = user(spec);
        GeocodingService.Coordinates coordinates = geocodingService.locate(spec.address().toEntity());

        assertThat(user.getEmail()).isEqualTo(spec.email());
        assertThat(user.getName()).isEqualTo(spec.name());
        assertThat(user.getBio()).isEqualTo(spec.bio());
        assertThat(user.getBirthDate()).isEqualTo(spec.birthDate());
        assertThat(user.getGender()).isEqualTo(spec.gender());
        assertThat(user.getStatus()).isEqualTo(spec.status());
        assertThat(user.getInterests()).isEqualTo(spec.interests());
        assertThat(user.getHobbies()).isEqualTo(spec.hobbies());
        assertThat(user.getAddress().getStreet()).isEqualTo(spec.address().street());
        assertThat(user.getAddress().getCity()).isEqualTo(spec.address().city());
        assertThat(user.getAddress().getZipCode()).isEqualTo(spec.address().zipCode());
        assertThat(user.getAddress().getCountry()).isEqualTo(spec.address().country());
        assertThat(user.getLatitude()).isEqualTo(coordinates.latitude());
        assertThat(user.getLongitude()).isEqualTo(coordinates.longitude());
        assertThat(user.getPreferences().getSeekingGenders()).isEqualTo(spec.seekingGenders());
        assertThat(user.getPreferences().getMinAge()).isEqualTo(spec.minAge());
        assertThat(user.getPreferences().getMaxAge()).isEqualTo(spec.maxAge());
        assertThat(user.getPreferences().getMaxDistanceKm()).isEqualTo(spec.maxDistanceKm());
        assertThat(user.getPhotoKey()).isNull();
        assertThat(userSongRepository.findAllByUserIdOrderByPositionAsc(user.getId()))
                .extracting(UserSong::getSpotifyTrackId, UserSong::getName, UserSong::getArtists, UserSong::getAlbum, UserSong::isAnthem)
                .containsExactlyElementsOf(spec.songs().stream()
                        .map(song -> tuple(song.spotifyTrackId(), song.name(), song.artists(), song.album(), song.anthem())).toList());
        assertThat(userGameRepository.findAllByUserIdOrderByPositionAsc(user.getId()))
                .extracting(UserGame::getIgdbId, UserGame::getName, UserGame::getYear, UserGame::getPlatforms, UserGame::getSteamAppId)
                .containsExactlyElementsOf(spec.games().stream()
                        .map(game -> tuple(game.igdbId(), game.name(), game.year(), game.platforms(), game.steamAppId())).toList());
        assertThat(passwordEncoder.matches(E2eUsers.PASSWORD, user.getPassword())).isTrue();
    }

    private static boolean isE2eUser(User user) {
        return E2eUsers.ALL.stream().anyMatch(spec -> spec.referenceId().equals(user.getReferenceId()));
    }

    private User user(E2eUser spec) {
        return userRepository.findByReferenceId(spec.referenceId()).orElseThrow();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String token(String email, String password) throws Exception {
        String response = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.access_token");
    }
}
