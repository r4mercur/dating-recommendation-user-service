package com.bjarne.datingrecommendationsuserservice.config.e2e;

import com.bjarne.datingrecommendationsuserservice.dto.AddressDto;
import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Fixed users for end-to-end tests of the frontend. They are reset to exactly these values on every start
 * with the profile "e2e" (see {@link E2eDataSeedRunner}). Emails and password are also documented in
 * application-e2e.properties.
 * <p>
 * Ages are not stored: tests must derive them from {@code birthDate}.
 */
public final class E2eUsers {

    public static final String PASSWORD = "e2e-password";

    /**
     * Tags only E2E users have. The dev seeder never assigns them, so alice' recommendations are bob and carol.
     */
    public static final Set<String> RESERVED_INTERESTS = Set.of("science", "animals");
    public static final Set<String> RESERVED_HOBBIES = Set.of("climbing", "board_games");

    /**
     * City of alice, bob and carol. The dev seeder places no users here.
     */
    public static final String CITY = "Berlin";

    /**
     * Fixed songs and games are copies written straight to the database, without calling Spotify or IGDB.
     * The ids are not verified against the services and there are no images, like the users have no profile photo.
     */
    public record E2eSong(String spotifyTrackId, String name, List<String> artists, String album, boolean anthem) {
    }

    public record E2eGame(long igdbId, String name, Integer year, List<String> platforms, Long steamAppId) {
    }

    // songs: alice and bob share the track "Blinding Lights" and the artist The Killers; carol has none in common
    public static final E2eSong MR_BRIGHTSIDE = new E2eSong("003vvx7Niy0yvhvHt4a68B", "Mr. Brightside", List.of("The Killers"), "Hot Fuss", false);
    public static final E2eSong SOMEBODY_TOLD_ME = new E2eSong("0lYBSQXN6rCTvUZvg9S0lU", "Somebody Told Me", List.of("The Killers"), "Hot Fuss", false);
    public static final E2eSong BLINDING_LIGHTS = new E2eSong("0VjIjW4GlUZAMYd2vXMi3b", "Blinding Lights", List.of("The Weeknd"), "After Hours", false);
    public static final E2eSong KNIGHTS_OF_CYDONIA = new E2eSong("7ouMYWpwJ422jRcDASZB7P", "Knights of Cydonia", List.of("Muse"), "Black Holes and Revelations", false);
    public static final E2eSong SMOOTH_CRIMINAL = new E2eSong("7h2ZGUJRnTrcpJsIjzEWiS", "Smooth Criminal", List.of("Michael Jackson"), "Bad", false);

    // games: alice and bob share The Witcher 3 and Hollow Knight; carol has none in common
    public static final E2eGame WITCHER_3 = new E2eGame(1942, "The Witcher 3: Wild Hunt", 2015, List.of("PC", "PS4", "Switch", "XONE"), 292030L);
    public static final E2eGame HADES = new E2eGame(113112, "Hades", 2020, List.of("PC", "PS5", "Switch", "Series X|S"), 1145360L);
    public static final E2eGame HOLLOW_KNIGHT = new E2eGame(14593, "Hollow Knight", 2017, List.of("PC", "PS4", "Switch", "XONE"), 367520L);
    public static final E2eGame ELDEN_RING = new E2eGame(119133, "Elden Ring", 2022, List.of("PC", "PS5", "Series X|S"), 1245620L);
    public static final E2eGame STARDEW_VALLEY = new E2eGame(17000, "Stardew Valley", 2016, List.of("PC", "PS4", "Switch", "XONE"), 413150L);
    public static final E2eGame PORTAL_2 = new E2eGame(72, "Portal 2", 2011, List.of("PC", "PS3", "Mac"), 620L);

    private static E2eSong anthem(E2eSong song) {
        return new E2eSong(song.spotifyTrackId(), song.name(), song.artists(), song.album(), true);
    }

    public record E2eUser(String referenceId,
                          String email,
                          String name,
                          String bio,
                          LocalDate birthDate,
                          Gender gender,
                          UserStatus status,
                          List<String> interests,
                          List<String> hobbies,
                          AddressDto address,
                          Set<Gender> seekingGenders,
                          int minAge,
                          int maxAge,
                          int maxDistanceKm,
                          List<E2eSong> songs,
                          List<E2eGame> games) {
    }

    /** Main test user: bob and carol match her preferences and tags. */
    public static final E2eUser ALICE = new E2eUser(
            "00000000-0000-0000-0000-00000000a11c",
            "e2e-alice@test.local",
            "Alice E2E",
            "E2E test user. Loves climbing, board games and animals.",
            LocalDate.of(1995, 4, 12),
            Gender.FEMALE,
            UserStatus.ACTIVE,
            List.of("science", "animals"),
            List.of("climbing", "board_games"),
            new AddressDto("Invalidenstraße 1", CITY, "10115", "DE"),
            Set.of(Gender.MALE, Gender.FEMALE),
            25, 40, 25,
            List.of(anthem(MR_BRIGHTSIDE), BLINDING_LIGHTS, KNIGHTS_OF_CYDONIA),
            List.of(WITCHER_3, HADES, HOLLOW_KNIGHT));

    /** Same city and overlapping tags as alice. */
    public static final E2eUser BOB = new E2eUser(
            "00000000-0000-0000-0000-000000000b0b",
            "e2e-bob@test.local",
            "Bob E2E",
            "E2E test user. Climbing and science nerd.",
            LocalDate.of(1993, 8, 30),
            Gender.MALE,
            UserStatus.ACTIVE,
            List.of("science", "animals"),
            List.of("climbing"),
            new AddressDto("Invalidenstraße 2", CITY, "10115", "DE"),
            Set.of(Gender.FEMALE),
            25, 40, 25,
            List.of(anthem(SOMEBODY_TOLD_ME), BLINDING_LIGHTS),
            List.of(WITCHER_3, HOLLOW_KNIGHT, ELDEN_RING));

    /** Matches alice and has already liked her, so a like from alice creates a match. */
    public static final E2eUser CAROL = new E2eUser(
            "00000000-0000-0000-0000-00000000ca01",
            "e2e-carol@test.local",
            "Carol E2E",
            "E2E test user. Board game evenings and dogs.",
            LocalDate.of(1997, 2, 3),
            Gender.FEMALE,
            UserStatus.ACTIVE,
            List.of("animals"),
            List.of("board_games", "climbing"),
            new AddressDto("Invalidenstraße 3", CITY, "10115", "DE"),
            Set.of(Gender.FEMALE),
            25, 40, 25,
            List.of(anthem(SMOOTH_CRIMINAL)),
            List.of(STARDEW_VALLEY, PORTAL_2));

    /** Blocked account: login must fail. */
    public static final E2eUser BLOCKED = new E2eUser(
            "00000000-0000-0000-0000-00000000b10c",
            "e2e-blocked@test.local",
            "Blocked E2E",
            "E2E test user. This account is blocked.",
            LocalDate.of(1990, 1, 1),
            Gender.DIVERSE,
            UserStatus.BLOCKED,
            List.of("music"),
            List.of("reading"),
            new AddressDto(null, "München", "80331", "DE"),
            Set.of(Gender.MALE, Gender.FEMALE, Gender.DIVERSE),
            18, 99, 50,
            List.of(),
            List.of());

    /** Moderator (role ADMIN) for the report queue and the admin API. Lives in München, so alice never sees it. */
    public static final E2eUser ADMIN = new E2eUser(
            "00000000-0000-0000-0000-00000000ad01",
            "e2e-admin@test.local",
            "Admin E2E",
            "E2E moderator account.",
            LocalDate.of(1985, 6, 15),
            Gender.DIVERSE,
            UserStatus.ACTIVE,
            List.of("music"),
            List.of("reading"),
            new AddressDto(null, "München", "80331", "DE"),
            Set.of(Gender.MALE, Gender.FEMALE, Gender.DIVERSE),
            18, 99, 50,
            List.of(),
            List.of());

    public static final List<E2eUser> ALL = List.of(ALICE, BOB, CAROL, BLOCKED, ADMIN);

    /** referenceIds of the E2E users with role ADMIN; everybody else is reset to USER. */
    public static final Set<String> ADMINS = Set.of(ADMIN.referenceId());

    private E2eUsers() {
    }
}
