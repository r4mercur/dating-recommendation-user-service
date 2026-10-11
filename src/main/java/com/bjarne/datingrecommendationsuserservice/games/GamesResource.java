package com.bjarne.datingrecommendationsuserservice.games;

import com.bjarne.datingrecommendationsuserservice.games.dto.GameRequest;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import com.bjarne.datingrecommendationsuserservice.games.dto.SteamSuggestion;
import com.bjarne.datingrecommendationsuserservice.games.dto.SteamSuggestionsRequest;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import com.bjarne.datingrecommendationsuserservice.security.RateLimiter;
import com.bjarne.datingrecommendationsuserservice.steam.SteamImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GamesResource {

    private final GameSearchService gameSearchService;
    private final GameService gameService;
    private final SteamImportService steamImportService;
    private final RateLimiter rateLimiter;

    public GamesResource(GameSearchService gameSearchService, GameService gameService, SteamImportService steamImportService,
                         RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.gameSearchService = gameSearchService;
        this.gameService = gameService;
        this.steamImportService = steamImportService;
    }

    /** IGDB search for the profile editor; 503 if IGDB is down. */
    @GetMapping("/games/search")
    public List<GameResult> search(@CurrentUser String referenceId, @RequestParam("q") String query) {
        rateLimiter.check(RateLimiter.EXTERNAL_SEARCH, referenceId);
        return gameSearchService.search(query);
    }

    /** Replaces the whole game list of the user. */
    @PutMapping("/me/games")
    public List<GameResult> replaceGames(@CurrentUser String referenceId, @RequestBody List<GameRequest> games) {
        return gameService.replaceGames(referenceId, games);
    }

    /** Games from a Steam library as suggestions; nothing is saved. */
    @PostMapping("/me/games/steam-suggestions")
    public List<SteamSuggestion> steamSuggestions(@CurrentUser String referenceId, @Valid @RequestBody SteamSuggestionsRequest request) {
        rateLimiter.check(RateLimiter.EXTERNAL_SEARCH, referenceId);
        return steamImportService.suggest(request.profile());
    }
}
