package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.games.UserGame;
import com.bjarne.datingrecommendationsuserservice.music.UserSong;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What two users have in common, for "Das habt ihr gemeinsam" and compliment suggestions.
 * Interests and hobbies are tag slugs, artists are names (compared case-insensitively), games are IGDB ids.
 */
public record CommonResponse(List<String> interests, List<String> hobbies, List<String> artists, List<Long> games) {

    public static CommonResponse of(User viewer, List<UserSong> viewerSongs, List<UserGame> viewerGames,
                                    User other, List<UserSong> otherSongs, List<UserGame> otherGames) {
        Set<Long> otherGameIds = otherGames.stream().map(UserGame::getIgdbId).collect(Collectors.toSet());
        return new CommonResponse(
                shared(viewer.getInterests(), other.getInterests()),
                shared(viewer.getHobbies(), other.getHobbies()),
                sharedArtists(viewerSongs, otherSongs),
                viewerGames.stream().map(UserGame::getIgdbId).filter(otherGameIds::contains).toList());
    }

    /** Number of shared interests, hobbies, artists and games. */
    public int count() {
        return interests.size() + hobbies.size() + artists.size() + games.size();
    }

    private static List<String> shared(List<String> mine, List<String> theirs) {
        if (mine == null || theirs == null) {
            return List.of();
        }
        Set<String> theirTags = new HashSet<>(theirs);
        return mine.stream().filter(theirTags::contains).distinct().toList();
    }

    /** Artists of the viewer's songs that also appear on the other user's songs, in the viewer's order. */
    private static List<String> sharedArtists(List<UserSong> mine, List<UserSong> theirs) {
        Set<String> theirArtists = theirs.stream().flatMap(song -> song.getArtists().stream())
                .map(CommonResponse::key).collect(Collectors.toSet());

        Map<String, String> shared = new LinkedHashMap<>();
        mine.stream().flatMap(song -> song.getArtists().stream())
                .filter(artist -> theirArtists.contains(key(artist)))
                .forEach(artist -> shared.putIfAbsent(key(artist), artist));
        return List.copyOf(shared.values());
    }

    private static String key(String artist) {
        return artist.trim().toLowerCase(Locale.ROOT);
    }
}
