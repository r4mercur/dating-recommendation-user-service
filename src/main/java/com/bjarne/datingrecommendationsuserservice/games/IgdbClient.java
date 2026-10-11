package com.bjarne.datingrecommendationsuserservice.games;

import com.bjarne.datingrecommendationsuserservice.exception.ExternalServiceUnavailableException;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Access to IGDB. Only used to search, to pick games and to map Steam apps, never to show profiles.
 * Implementations throw {@link ExternalServiceUnavailableException} if IGDB cannot be used right now.
 */
public interface IgdbClient {

    /** Games matching the text, best match first, at most {@code limit}. */
    List<GameResult> search(String query, int limit);

    /** The games that exist among the ids (unknown ids are missing from the result). */
    List<GameResult> findByIds(Collection<Long> igdbIds);

    /** IGDB games for Steam app ids, by app id. Apps IGDB does not know are missing from the result. */
    Map<Long, GameResult> findBySteamAppIds(Collection<Long> steamAppIds);
}
