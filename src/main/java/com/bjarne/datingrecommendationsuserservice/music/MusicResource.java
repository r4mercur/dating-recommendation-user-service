package com.bjarne.datingrecommendationsuserservice.music;

import com.bjarne.datingrecommendationsuserservice.music.dto.SongRequest;
import com.bjarne.datingrecommendationsuserservice.music.dto.SongResponse;
import com.bjarne.datingrecommendationsuserservice.music.dto.TrackResult;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import com.bjarne.datingrecommendationsuserservice.security.RateLimiter;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MusicResource {

    private final MusicSearchService musicSearchService;
    private final SongService songService;
    private final RateLimiter rateLimiter;

    public MusicResource(MusicSearchService musicSearchService, SongService songService, RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.musicSearchService = musicSearchService;
        this.songService = songService;
    }

    /** Spotify search for the profile editor; 503 if Spotify is down or the quota is used up. */
    @GetMapping("/music/search")
    public List<TrackResult> search(@CurrentUser String referenceId, @RequestParam("q") String query) {
        // the Spotify quota is shared by all users, one user must not be able to use it up
        rateLimiter.check(RateLimiter.EXTERNAL_SEARCH, referenceId);
        return musicSearchService.search(query);
    }

    /** Replaces the whole song list of the user. */
    @PutMapping("/me/songs")
    public List<SongResponse> replaceSongs(@CurrentUser String referenceId, @RequestBody List<SongRequest> songs) {
        return songService.replaceSongs(referenceId, songs);
    }
}
