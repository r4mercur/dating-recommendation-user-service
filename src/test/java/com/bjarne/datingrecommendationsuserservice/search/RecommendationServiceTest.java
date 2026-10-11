package com.bjarne.datingrecommendationsuserservice.search;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceTest {

    @Test
    void compatibilityIsTheShareOfCommonTags() {
        User alice = user(List.of("science", "animals"), List.of("climbing", "board_games"));

        assertThat(RecommendationService.compatibility(alice, user(List.of("science", "animals"), List.of("climbing")))).isEqualTo(75);
        assertThat(RecommendationService.compatibility(alice, alice)).isEqualTo(100);
        assertThat(RecommendationService.compatibility(alice, user(List.of("music"), List.of()))).isEqualTo(0);
        assertThat(RecommendationService.compatibility(user(null, null), user(List.of(), List.of()))).isEqualTo(0);
    }

    @Test
    void sameSlugInDifferentCategoriesIsNotShared() {
        User sportsFan = user(List.of("sports"), List.of());
        User athlete = user(List.of(), List.of("sports"));

        assertThat(RecommendationService.compatibility(sportsFan, athlete)).isEqualTo(0);
    }

    private static User user(List<String> interests, List<String> hobbies) {
        User user = new User();
        user.setInterests(interests);
        user.setHobbies(hobbies);
        return user;
    }
}
