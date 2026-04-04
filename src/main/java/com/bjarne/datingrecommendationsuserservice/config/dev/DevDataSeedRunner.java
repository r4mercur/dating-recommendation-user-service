package com.bjarne.datingrecommendationsuserservice.config.dev;

import com.bjarne.datingrecommendationsuserservice.entity.Address;
import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import net.datafaker.Faker;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("dev")
@Order(20)
public class DevDataSeedRunner implements ApplicationRunner {

    private final UserService userService;
    private final boolean isDataSeedEnabled;
    private final Faker faker = new Faker(Locale.GERMANY);

    public DevDataSeedRunner(
            UserService userService,
            @Value("${recommender.solr.data-seed-enabled:false}") boolean isDataSeedEnabled
    ) {
        this.userService = userService;
        this.isDataSeedEnabled = isDataSeedEnabled;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!isDataSeedEnabled || userService.countUsers() > 0) return;

        for (int i = 0; i < 100; i++) {
            User user = buildFakeUser();
            userService.save(user, false);
        }
    }

    private User buildFakeUser() {
        User user = new User();
        user.setName(faker.name().fullName());
        user.setEmail(faker.internet().emailAddress());
        user.setPassword("password");
        user.setInterests(List.of(faker.hobby().activity(), faker.music().genre()));
        user.setHobbies(List.of(faker.esports().event(), faker.book().genre()));
        user.setAge(ThreadLocalRandom.current().nextInt(18, 46));
        user.setGender(randomGender());
        user.setStatus(UserStatus.ACTIVE);

        Address address = new Address();
        address.setStreet(faker.address().streetAddress());
        address.setCity(faker.address().cityName());
        address.setZipCode(faker.address().zipCode());
        address.setCountry("DE");
        user.setAddress(address);

        return user;
    }

    private Gender randomGender() {
        int n = ThreadLocalRandom.current().nextInt(3);
        return n == 0 ? Gender.MALE : n == 1 ? Gender.FEMALE : Gender.DIVERSE;
    }
}
