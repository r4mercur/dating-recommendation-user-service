package com.bjarne.datingrecommendationsuserservice.config.dev;

import com.bjarne.datingrecommendationsuserservice.config.e2e.E2eUsers;
import com.bjarne.datingrecommendationsuserservice.entity.*;
import com.bjarne.datingrecommendationsuserservice.repository.PostalCodeRepository;
import com.bjarne.datingrecommendationsuserservice.service.TagService;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import net.datafaker.Faker;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Seeds 100 fake users into an empty database. All random values come from one seeded {@link Random},
 * so every freshly created database contains the same users. Tags and the city reserved for the E2E users
 * are never used, so the fake users cannot push bob and carol out of alice' recommendations.
 * <p>
 * All fake users can see each other: they seek every gender, from 18 to 99 years, within {@value #MAX_DISTANCE_KM} km
 * (more than the distance across Germany). Random preferences made almost nobody visible to anybody,
 * because the filters apply in both directions.
 */
@Component
@Profile("dev")
@Order(20)
public class DevDataSeedRunner implements ApplicationRunner {

    private static final int USER_COUNT = 100;
    private static final long SEED = 42;
    /** Larger than any distance between two places in Germany, so the distance filter never excludes a fake user. */
    private static final int MAX_DISTANCE_KM = 1000;
    /** Fixed reference date instead of "today", so birth dates do not depend on the day of seeding. */
    private static final LocalDate OLDEST_BIRTH_DATE = LocalDate.of(1980, 1, 1);

    private final UserService userService;
    private final TagService tagService;
    private final PostalCodeRepository postalCodeRepository;
    private final boolean isDataSeedEnabled;
    private final Random random = new Random(SEED);
    private final Faker faker = new Faker(Locale.GERMANY, random);

    public DevDataSeedRunner(
            UserService userService,
            TagService tagService,
            PostalCodeRepository postalCodeRepository,
            @Value("${recommender.solr.data-seed-enabled:false}") boolean isDataSeedEnabled
    ) {
        this.userService = userService;
        this.tagService = tagService;
        this.postalCodeRepository = postalCodeRepository;
        this.isDataSeedEnabled = isDataSeedEnabled;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!isDataSeedEnabled || userService.countUsers() > 0) return;

        generateUsers().forEach(userService::createUser);
    }

    /**
     * Builds the fake users without saving them. Same result for every new instance.
     */
    public List<User> generateUsers() {
        List<String> interests = slugs(TagCategory.INTEREST, E2eUsers.RESERVED_INTERESTS);
        List<String> hobbies = slugs(TagCategory.HOBBY, E2eUsers.RESERVED_HOBBIES);

        List<PostalCode> places = new ArrayList<>(postalCodeRepository.findRegularPlaces("DE").stream()
                .filter(place -> !place.getPlaceName().equalsIgnoreCase(E2eUsers.CITY))
                .toList());
        Collections.shuffle(places, random);

        return places.stream()
                .limit(USER_COUNT)
                .map(place -> buildFakeUser(place, interests, hobbies))
                .toList();
    }

    private User buildFakeUser(PostalCode place, List<String> interests, List<String> hobbies) {
        User user = new User();
        user.setName(faker.name().fullName());
        user.setEmail(faker.internet().emailAddress().toLowerCase(Locale.ROOT));
        user.setPassword("password");
        user.setBio(faker.lorem().sentence(12));
        user.setBirthDate(OLDEST_BIRTH_DATE.plusDays(random.nextInt(0, 365 * 27)));
        user.setInterests(pick(interests, random.nextInt(2, 5)));
        user.setHobbies(pick(hobbies, random.nextInt(2, 5)));
        user.setGender(randomGender());
        user.setStatus(UserStatus.ACTIVE);

        Address address = new Address();
        address.setStreet(faker.address().streetAddress());
        address.setCity(place.getPlaceName());
        address.setZipCode(place.getPostalCode());
        address.setCountry(place.getCountryCode());
        user.setAddress(address);
        user.setLatitude(place.getLatitude());
        user.setLongitude(place.getLongitude());

        // defaults: all genders, 18-99 years; only the distance is widened
        SearchPreferences preferences = SearchPreferences.defaults();
        preferences.setMaxDistanceKm(MAX_DISTANCE_KM);
        user.setPreferences(preferences);

        return user;
    }

    private List<String> slugs(TagCategory category, Set<String> excluded) {
        return tagService.getTags(category).stream()
                .map(Tag::getSlug)
                .filter(slug -> !excluded.contains(slug))
                .toList();
    }

    private List<String> pick(List<String> values, int count) {
        List<String> shuffled = new ArrayList<>(values);
        Collections.shuffle(shuffled, random);
        return List.copyOf(shuffled.subList(0, Math.min(count, shuffled.size())));
    }

    private Gender randomGender() {
        Gender[] genders = Gender.values();
        return genders[random.nextInt(genders.length)];
    }
}
