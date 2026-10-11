package com.bjarne.datingrecommendationsuserservice.games;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.event.UserProfileUpdatedEvent;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameRequest;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The favourite games on a profile: at most {@value #MAX_GAMES}. The list is always replaced as a whole; the order
 * of the request is the position.
 */
@Service
public class GameService {

    public static final int MAX_GAMES = 6;

    private final UserService userService;
    private final UserGameRepository userGameRepository;
    private final GameSearchService gameSearchService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public GameService(UserService userService,
                       UserGameRepository userGameRepository,
                       GameSearchService gameSearchService,
                       TransactionTemplate transactionTemplate,
                       ApplicationEventPublisher eventPublisher) {
        this.userService = userService;
        this.userGameRepository = userGameRepository;
        this.gameSearchService = gameSearchService;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    public List<GameResult> replaceGames(String referenceId, List<GameRequest> requests) {
        validate(requests);
        User user = userService.getByReferenceId(referenceId);
        List<Long> ids = requests.stream().map(GameRequest::igdbId).toList();

        // external lookups happen before the transaction starts; games already on the profile need none
        Map<Long, GameResult> games = new HashMap<>();
        userGameRepository.findAllByUserIdOrderByPositionAsc(user.getId())
                .forEach(game -> games.put(game.getIgdbId(), game.toGame()));
        List<Long> unknown = ids.stream().filter(id -> !games.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            games.putAll(gameSearchService.findGames(unknown));
        }
        ids.stream().filter(id -> !games.containsKey(id)).findFirst().ifPresent(id -> {
            throw new BadRequestException("Unknown game: " + id);
        });

        return transactionTemplate.execute(_ -> {
            userGameRepository.deleteAllByUserId(user.getId());
            List<UserGame> saved = new ArrayList<>();
            for (int position = 0; position < ids.size(); position++) {
                saved.add(new UserGame(user.getId(), position, games.get(ids.get(position))));
            }
            List<GameResult> result = userGameRepository.saveAll(saved).stream().map(UserGame::toGame).toList();
            eventPublisher.publishEvent(UserProfileUpdatedEvent.of(referenceId));
            return result;
        });
    }

    private static void validate(List<GameRequest> requests) {
        if (requests == null) {
            throw new BadRequestException("A list of games is required");
        }
        if (requests.size() > MAX_GAMES) {
            throw new BadRequestException("At most " + MAX_GAMES + " games are allowed");
        }

        Set<Long> seen = new HashSet<>();
        for (GameRequest request : requests) {
            if (request == null || request.igdbId() == null || request.igdbId() <= 0) {
                throw new BadRequestException("Every game needs a valid igdbId");
            }
            if (!seen.add(request.igdbId())) {
                throw new BadRequestException("Game " + request.igdbId() + " is in the list twice");
            }
        }
    }
}
