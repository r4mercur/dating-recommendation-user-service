package com.bjarne.datingrecommendationsuserservice.config.e2e;

import com.bjarne.datingrecommendationsuserservice.config.e2e.E2eUsers.E2eUser;
import com.bjarne.datingrecommendationsuserservice.entity.Address;
import com.bjarne.datingrecommendationsuserservice.entity.Role;
import com.bjarne.datingrecommendationsuserservice.entity.SearchPreferences;
import com.bjarne.datingrecommendationsuserservice.entity.TagCategory;
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
import com.bjarne.datingrecommendationsuserservice.moderation.ReportRepository;
import com.bjarne.datingrecommendationsuserservice.notification.Notification;
import com.bjarne.datingrecommendationsuserservice.notification.NotificationRepository;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.service.GeocodingService;
import com.bjarne.datingrecommendationsuserservice.service.TagService;
import com.bjarne.datingrecommendationsuserservice.search.SearchIndexService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Resets the fixed E2E users ({@link E2eUsers}) on every start with the profile "e2e": all fields, the password
 * and all relations they are part of. Runs after the postal code import and the dev seeder.
 * There is intentionally no API to trigger a reset; restart the application instead.
 */
@Component
@Profile("e2e")
@Order(30)
public class E2eDataSeedRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(E2eDataSeedRunner.class);
    private static final Map<String, E2eUser> DOCUMENTED_USERS = Map.of(
            "alice", E2eUsers.ALICE,
            "bob", E2eUsers.BOB,
            "carol", E2eUsers.CAROL,
            "blocked", E2eUsers.BLOCKED,
            "admin", E2eUsers.ADMIN);

    private final UserRepository userRepository;
    private final ReactionRepository reactionRepository;
    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;
    private final ReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final UserSongRepository userSongRepository;
    private final UserGameRepository userGameRepository;
    private final SearchIndexService searchIndexService;
    private final TagService tagService;
    private final GeocodingService geocodingService;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final Environment environment;

    public E2eDataSeedRunner(UserRepository userRepository,
                             ReactionRepository reactionRepository,
                             MatchRepository matchRepository,
                             BlockRepository blockRepository,
                             ReportRepository reportRepository,
                             NotificationRepository notificationRepository,
                             UserSongRepository userSongRepository,
                             UserGameRepository userGameRepository,
                             SearchIndexService searchIndexService,
                             TagService tagService,
                             GeocodingService geocodingService,
                             PasswordEncoder passwordEncoder,
                             TransactionTemplate transactionTemplate,
                             Environment environment) {
        this.userRepository = userRepository;
        this.reactionRepository = reactionRepository;
        this.matchRepository = matchRepository;
        this.blockRepository = blockRepository;
        this.reportRepository = reportRepository;
        this.notificationRepository = notificationRepository;
        this.userSongRepository = userSongRepository;
        this.userGameRepository = userGameRepository;
        this.searchIndexService = searchIndexService;
        this.tagService = tagService;
        this.geocodingService = geocodingService;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.environment = environment;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        verifyDocumentedValues();

        List<User> users = Objects.requireNonNull(transactionTemplate.execute(_ -> {
            List<User> reset = E2eUsers.ALL.stream().map(this::reset).toList();

            // messages are deleted with their matches (on delete cascade)
            List<Long> ids = reset.stream().map(User::getId).toList();
            matchRepository.deleteAllInvolving(ids);
            reactionRepository.deleteAllInvolving(ids);
            blockRepository.deleteAllInvolving(ids);
            reportRepository.deleteAllInvolving(ids);
            notificationRepository.deleteAllInvolving(ids);

            // songs and games are copies, written without calling Spotify or IGDB
            userSongRepository.deleteAllByUserIdIn(ids);
            userGameRepository.deleteAllByUserIdIn(ids);
            E2eUsers.ALL.forEach(spec -> saveMusicAndGames(find(reset, spec).getId(), spec));

            // carol already likes alice: a like from alice creates a match
            reactionRepository.save(new Reaction(find(reset, E2eUsers.CAROL), find(reset, E2eUsers.ALICE), ReactionType.LIKE, null));
            // ... and alice has the unread notification for it (written directly, the seeder publishes no events)
            notificationRepository.save(Notification.like(find(reset, E2eUsers.ALICE), find(reset, E2eUsers.CAROL), ReactionType.LIKE.name()));
            return reset;
        }));

        users.forEach(searchIndexService::index);
        LOGGER.info("Reset {} E2E users ({}).", users.size(), users.stream().map(User::getEmail).toList());
    }

    private void saveMusicAndGames(Long userId, E2eUser spec) {
        for (int position = 0; position < spec.songs().size(); position++) {
            E2eUsers.E2eSong song = spec.songs().get(position);
            userSongRepository.save(new UserSong(userId, position,
                    TrackResult.of(song.spotifyTrackId(), song.name(), song.artists(), song.album(), null), song.anthem()));
        }
        for (int position = 0; position < spec.games().size(); position++) {
            E2eUsers.E2eGame game = spec.games().get(position);
            userGameRepository.save(new UserGame(userId, position,
                    GameResult.of(game.igdbId(), game.name(), null, game.year(), game.platforms(), game.steamAppId())));
        }
    }

    private User reset(E2eUser spec) {
        User user = userRepository.findByReferenceId(spec.referenceId()).orElseGet(() -> {
            User created = new User();
            created.setReferenceId(spec.referenceId());
            return created;
        });

        userRepository.findByEmailIgnoreCase(spec.email())
                .filter(other -> !other.getReferenceId().equals(spec.referenceId()))
                .ifPresent(other -> {
                    throw new IllegalStateException("Email " + spec.email() + " of E2E user is used by another user "
                            + other.getReferenceId() + ", recreate the database (compose down -v)");
                });

        Address address = spec.address().toEntity();
        address.setCountry(geocodingService.normalizeCountry(spec.address().country()));
        GeocodingService.Coordinates coordinates = geocodingService.locate(address);

        user.setName(spec.name());
        user.setEmail(spec.email());
        user.setPassword(passwordEncoder.encode(E2eUsers.PASSWORD));
        user.setBio(spec.bio());
        user.setBirthDate(spec.birthDate());
        user.setGender(spec.gender());
        user.setStatus(spec.status());
        user.setDeletionRequestedAt(null);
        user.setRole(E2eUsers.ADMINS.contains(spec.referenceId()) ? Role.ADMIN : Role.USER);
        user.setShowOnlineStatus(true);
        user.setInterests(tagService.normalize(TagCategory.INTEREST, spec.interests()));
        user.setHobbies(tagService.normalize(TagCategory.HOBBY, spec.hobbies()));
        user.setAddress(address);
        user.setLatitude(coordinates.latitude());
        user.setLongitude(coordinates.longitude());
        user.setPhotoKey(null);

        if (user.getPreferences() == null) {
            user.setPreferences(SearchPreferences.defaults());
        }
        SearchPreferences preferences = user.getPreferences();
        preferences.setSeekingGenders(new HashSet<>(spec.seekingGenders()));
        preferences.setMinAge(spec.minAge());
        preferences.setMaxAge(spec.maxAge());
        preferences.setMaxDistanceKm(spec.maxDistanceKm());

        return userRepository.save(user);
    }

    /**
     * application-e2e.properties documents the credentials for the frontend; fail fast if it drifts from the code.
     */
    private void verifyDocumentedValues() {
        requireDocumented("e2e.seed.password", E2eUsers.PASSWORD);
        DOCUMENTED_USERS.forEach((key, user) -> {
            requireDocumented("e2e.seed.users." + key + ".email", user.email());
            requireDocumented("e2e.seed.users." + key + ".reference-id", user.referenceId());
        });
    }

    private void requireDocumented(String property, String expected) {
        String documented = environment.getProperty(property);
        if (!expected.equals(documented)) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "%s is '%s' but E2eUsers defines '%s', keep application-e2e.properties in sync", property, documented, expected));
        }
    }

    private static User find(List<User> users, E2eUser spec) {
        return users.stream()
                .filter(user -> user.getReferenceId().equals(spec.referenceId()))
                .findFirst()
                .orElseThrow();
    }
}
