package com.bjarne.datingrecommendationsuserservice.games;

import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Game search for the profile editor. Results are cached for ten minutes per normalized query, which also keeps us
 * below the IGDB rate limit. Games seen in a search are remembered as well, so picking one of them needs no extra call.
 */
@Service
public class GameSearchService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_RESULTS = 10;

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final IgdbClient igdbClient;
    private final Cache<String, List<GameResult>> searches = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL).maximumSize(1_000).build();
    private final Cache<Long, GameResult> games = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL).maximumSize(10_000).build();

    public GameSearchService(IgdbClient igdbClient) {
        this.igdbClient = igdbClient;
    }

    public List<GameResult> search(String query) {
        String normalized = query == null ? "" : query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (normalized.length() < MIN_QUERY_LENGTH) {
            throw new BadRequestException("The search text must have at least " + MIN_QUERY_LENGTH + " characters");
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("The search text must not be longer than " + MAX_QUERY_LENGTH + " characters");
        }

        return searches.get(normalized, key -> {
            List<GameResult> results = igdbClient.search(key, MAX_RESULTS).stream().limit(MAX_RESULTS).toList();
            results.forEach(game -> games.put(game.igdbId(), game));
            return results;
        });
    }

    /** For tests: forget everything that was cached. */
    public void clearCaches() {
        searches.invalidateAll();
        games.invalidateAll();
    }

    /** The games that exist among the ids, by id. Ids IGDB does not know are missing. */
    public Map<Long, GameResult> findGames(Collection<Long> igdbIds) {
        Map<Long, GameResult> found = new LinkedHashMap<>();
        List<Long> missing = new ArrayList<>();
        for (Long igdbId : igdbIds) {
            GameResult cached = games.getIfPresent(igdbId);
            if (cached != null) {
                found.put(igdbId, cached);
            } else {
                missing.add(igdbId);
            }
        }

        if (!missing.isEmpty()) {
            for (GameResult game : igdbClient.findByIds(missing)) {
                games.put(game.igdbId(), game);
                found.put(game.igdbId(), game);
            }
        }
        return found;
    }
}
