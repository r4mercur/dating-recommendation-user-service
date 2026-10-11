package com.bjarne.datingrecommendationsuserservice.music;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.event.UserProfileUpdatedEvent;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.music.dto.SongRequest;
import com.bjarne.datingrecommendationsuserservice.music.dto.SongResponse;
import com.bjarne.datingrecommendationsuserservice.music.dto.TrackResult;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The songs on a profile: at most {@value #MAX_SONGS}, at most one of them the profile song (anthem). The list is
 * always replaced as a whole; the order of the request is the position.
 */
@Service
public class SongService {

    public static final int MAX_SONGS = 4;

    private final UserService userService;
    private final UserSongRepository userSongRepository;
    private final MusicSearchService musicSearchService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public SongService(UserService userService,
                       UserSongRepository userSongRepository,
                       MusicSearchService musicSearchService,
                       TransactionTemplate transactionTemplate,
                       ApplicationEventPublisher eventPublisher) {
        this.userService = userService;
        this.userSongRepository = userSongRepository;
        this.musicSearchService = musicSearchService;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    public List<SongResponse> getSongs(String referenceId) {
        User user = userService.getByReferenceId(referenceId);
        return userSongRepository.findAllByUserIdOrderByPositionAsc(user.getId()).stream().map(SongResponse::from).toList();
    }

    public List<SongResponse> replaceSongs(String referenceId, List<SongRequest> requests) {
        validate(requests);
        User user = userService.getByReferenceId(referenceId);

        // external lookups happen before the transaction starts
        Map<String, TrackResult> known = userSongRepository.findAllByUserIdOrderByPositionAsc(user.getId()).stream()
                .map(UserSong::toTrack)
                .collect(Collectors.toMap(TrackResult::spotifyTrackId, Function.identity(), (first, second) -> first));
        Map<String, TrackResult> tracks = new LinkedHashMap<>();
        for (SongRequest request : requests) {
            String trackId = request.spotifyTrackId();
            TrackResult track = known.get(trackId);
            if (track == null) {
                track = musicSearchService.findTrack(trackId)
                        .orElseThrow(() -> new BadRequestException("Unknown Spotify track: " + trackId));
            }
            tracks.put(trackId, track);
        }

        return transactionTemplate.execute(_ -> {
            userSongRepository.deleteAllByUserId(user.getId());
            List<UserSong> songs = new ArrayList<>();
            for (int position = 0; position < requests.size(); position++) {
                SongRequest request = requests.get(position);
                songs.add(new UserSong(user.getId(), position, tracks.get(request.spotifyTrackId()), request.anthem()));
            }
            List<SongResponse> saved = userSongRepository.saveAll(songs).stream().map(SongResponse::from).toList();
            eventPublisher.publishEvent(UserProfileUpdatedEvent.of(referenceId));
            return saved;
        });
    }

    private static void validate(List<SongRequest> requests) {
        if (requests == null) {
            throw new BadRequestException("A list of songs is required");
        }
        if (requests.size() > MAX_SONGS) {
            throw new BadRequestException("At most " + MAX_SONGS + " songs are allowed");
        }

        Set<String> seen = new HashSet<>();
        int anthems = 0;
        for (SongRequest request : requests) {
            if (request == null || !MusicSearchService.isValidTrackId(request.spotifyTrackId())) {
                throw new BadRequestException("Every song needs a valid spotifyTrackId");
            }
            if (!seen.add(request.spotifyTrackId())) {
                throw new BadRequestException("Song " + request.spotifyTrackId() + " is in the list twice");
            }
            if (request.anthem()) {
                anthems++;
            }
        }
        if (anthems > 1) {
            throw new BadRequestException("At most one song can be the profile song (anthem)");
        }
    }
}
