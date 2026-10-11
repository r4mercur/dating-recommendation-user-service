package com.bjarne.datingrecommendationsuserservice.profile;

import com.bjarne.datingrecommendationsuserservice.dto.CommonResponse;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.games.UserGame;
import com.bjarne.datingrecommendationsuserservice.games.UserGameRepository;
import com.bjarne.datingrecommendationsuserservice.music.UserSong;
import com.bjarne.datingrecommendationsuserservice.music.UserSongRepository;
import com.bjarne.datingrecommendationsuserservice.music.dto.SongResponse;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Compares the viewer with other users: common interests, hobbies, artists and games. Cards are compared in
 * one batch (four queries) instead of per user.
 */
@Service
public class CommonalityService {

    /** What a recommendation card shows besides the profile itself. */
    public record CardExtras(SongResponse anthem, int commonCount) {
    }

    private final UserSongRepository userSongRepository;
    private final UserGameRepository userGameRepository;

    public CommonalityService(UserSongRepository userSongRepository, UserGameRepository userGameRepository) {
        this.userSongRepository = userSongRepository;
        this.userGameRepository = userGameRepository;
    }

    public CommonResponse between(User viewer, User other) {
        return CommonResponse.of(
                viewer, userSongRepository.findAllByUserIdOrderByPositionAsc(viewer.getId()), userGameRepository.findAllByUserIdOrderByPositionAsc(viewer.getId()),
                other, userSongRepository.findAllByUserIdOrderByPositionAsc(other.getId()), userGameRepository.findAllByUserIdOrderByPositionAsc(other.getId()));
    }

    /** Anthem and number of commonalities per candidate, by referenceId. */
    public Map<String, CardExtras> forCards(User viewer, Collection<User> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }

        List<Long> candidateIds = candidates.stream().map(User::getId).toList();
        List<UserSong> viewerSongs = userSongRepository.findAllByUserIdOrderByPositionAsc(viewer.getId());
        List<UserGame> viewerGames = userGameRepository.findAllByUserIdOrderByPositionAsc(viewer.getId());
        Map<Long, List<UserSong>> songs = userSongRepository.findAllByUserIdInOrderByPositionAsc(candidateIds).stream()
                .collect(Collectors.groupingBy(UserSong::getUserId));
        Map<Long, List<UserGame>> games = userGameRepository.findAllByUserIdInOrderByPositionAsc(candidateIds).stream()
                .collect(Collectors.groupingBy(UserGame::getUserId));

        Map<String, CardExtras> extras = new HashMap<>();
        for (User candidate : candidates) {
            List<UserSong> candidateSongs = songs.getOrDefault(candidate.getId(), List.of());
            List<UserGame> candidateGames = games.getOrDefault(candidate.getId(), List.of());
            SongResponse anthem = candidateSongs.stream().filter(UserSong::isAnthem).findFirst().map(SongResponse::from).orElse(null);
            int common = CommonResponse.of(viewer, viewerSongs, viewerGames, candidate, candidateSongs, candidateGames).count();
            extras.put(candidate.getReferenceId(), new CardExtras(anthem, common));
        }
        return extras;
    }
}
