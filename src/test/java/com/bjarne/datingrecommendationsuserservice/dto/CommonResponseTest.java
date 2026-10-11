package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.games.UserGame;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import com.bjarne.datingrecommendationsuserservice.music.UserSong;
import com.bjarne.datingrecommendationsuserservice.music.dto.TrackResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommonResponseTest {

    @Test
    void sharedTagsArtistsAndGamesAreCounted() {
        User me = user(List.of("music", "art", "travel"), List.of("reading"));
        User other = user(List.of("art", "music"), List.of("reading", "cooking"));

        CommonResponse common = CommonResponse.of(
                me, List.of(song(1, "Artist A"), song(2, "The Killers", "Guest")), List.of(game(1), game(2), game(3)),
                other, List.of(song(3, "THE KILLERS"), song(4, "Somebody Else")), List.of(game(3), game(1), game(9)));

        assertThat(common.interests()).containsExactly("music", "art");
        assertThat(common.hobbies()).containsExactly("reading");
        assertThat(common.artists()).containsExactly("The Killers");
        assertThat(common.games()).containsExactly(1L, 3L);
        assertThat(common.count()).isEqualTo(2 + 1 + 1 + 2);
    }

    @Test
    void missingDataMeansNothingInCommon() {
        CommonResponse common = CommonResponse.of(user(null, null), List.of(), List.of(), user(List.of("music"), null), List.of(), List.of());

        assertThat(common.interests()).isEmpty();
        assertThat(common.hobbies()).isEmpty();
        assertThat(common.artists()).isEmpty();
        assertThat(common.games()).isEmpty();
        assertThat(common.count()).isZero();
    }

    private static User user(List<String> interests, List<String> hobbies) {
        User user = new User();
        user.setInterests(interests);
        user.setHobbies(hobbies);
        return user;
    }

    private static UserSong song(int number, String... artists) {
        String id = String.format("%022d", number);
        return new UserSong(1L, 0, TrackResult.of(id, "Song " + number, List.of(artists), null, null), false);
    }

    private static UserGame game(long igdbId) {
        return new UserGame(1L, 0, GameResult.of(igdbId, "Game " + igdbId, null, null, List.of(), null));
    }
}
