package com.bjarne.datingrecommendationsuserservice.games;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory IGDB: a fixed catalog, a switch to simulate an outage, and call counters.
 */
public class FakeIgdbClient implements IgdbClient {

    private final Map<Long, GameResult> catalog = new LinkedHashMap<>();
    private volatile boolean down;
    public final AtomicInteger searchCalls = new AtomicInteger();
    public final AtomicInteger idCalls = new AtomicInteger();

    /** Resets the fake to the games 1..12 named "Game n"; game n is on Steam as app 1000 + n, except game 12. */
    public void reset() {
        catalog.clear();
        down = false;
        searchCalls.set(0);
        idCalls.set(0);
        for (long id = 1; id <= 12; id++) {
            add(id, "Game " + id, id == 12 ? null : 1000 + id);
        }
    }

    public void add(long igdbId, String name, Long steamAppId) {
        catalog.put(igdbId, GameResult.of(igdbId, name, "https://images.igdb.com/igdb/image/upload/t_cover_big/co" + igdbId + ".jpg",
                2000 + (int) igdbId, List.of("PC", "PS5"), steamAppId));
    }

    public GameResult game(long igdbId) {
        return catalog.get(igdbId);
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    @Override
    public List<GameResult> search(String query, int limit) {
        searchCalls.incrementAndGet();
        failIfDown();
        String needle = query.toLowerCase(Locale.ROOT);
        return catalog.values().stream().filter(game -> game.name().toLowerCase(Locale.ROOT).contains(needle)).limit(limit).toList();
    }

    @Override
    public List<GameResult> findByIds(Collection<Long> igdbIds) {
        idCalls.incrementAndGet();
        failIfDown();
        return igdbIds.stream().map(catalog::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public Map<Long, GameResult> findBySteamAppIds(Collection<Long> steamAppIds) {
        failIfDown();
        Map<Long, GameResult> result = new HashMap<>();
        for (GameResult game : catalog.values()) {
            if (game.steamAppId() != null && steamAppIds.contains(game.steamAppId())) {
                result.put(game.steamAppId(), game);
            }
        }
        return result;
    }

    private void failIfDown() {
        if (down) {
            throw new ExternalServiceUnavailableException("Game search is unavailable right now");
        }
    }
}
