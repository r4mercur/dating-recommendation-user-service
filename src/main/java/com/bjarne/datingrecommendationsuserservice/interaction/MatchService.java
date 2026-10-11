package com.bjarne.datingrecommendationsuserservice.interaction;

import com.bjarne.datingrecommendationsuserservice.dto.ProfileMapper;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.event.MatchCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.event.MatchEndedEvent;
import com.bjarne.datingrecommendationsuserservice.exception.NotFoundException;
import com.bjarne.datingrecommendationsuserservice.interaction.dto.MatchResponse;
import com.bjarne.datingrecommendationsuserservice.interaction.dto.ReactionSummary;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final ReactionRepository reactionRepository;
    private final UserService userService;
    private final ProfileMapper profileMapper;
    private final ApplicationEventPublisher eventPublisher;

    public MatchService(MatchRepository matchRepository,
                        ReactionRepository reactionRepository,
                        UserService userService,
                        ProfileMapper profileMapper,
                        ApplicationEventPublisher eventPublisher) {
        this.matchRepository = matchRepository;
        this.reactionRepository = reactionRepository;
        this.userService = userService;
        this.profileMapper = profileMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Active matches with active partners, newest first.
     */
    @Transactional(readOnly = true)
    public List<MatchResponse> getMatches(String referenceId) {
        User me = userService.getByReferenceId(referenceId);
        List<Match> matches = matchRepository.findAllOfUser(me.getId(), Match.MatchStatus.ACTIVE).stream()
                .filter(match -> match.partnerOf(me).getStatus() == UserStatus.ACTIVE)
                .toList();

        List<Long> partnerIds = matches.stream().map(match -> match.partnerOf(me).getId()).toList();
        Map<Long, Reaction> theirReactions = partnerIds.isEmpty() ? Map.of()
                : reactionRepository.findAllByToUserIdAndFromUserIdIn(me.getId(), partnerIds).stream()
                .collect(Collectors.toMap(reaction -> reaction.getFromUser().getId(), Function.identity()));

        return matches.stream()
                .map(match -> toResponse(match, me, theirReactions.get(match.partnerOf(me).getId())))
                .toList();
    }

    @Transactional
    public void unmatch(String referenceId, String matchReferenceId) {
        User me = userService.getByReferenceId(referenceId);
        Match match = matchRepository.findByReferenceId(matchReferenceId)
                .filter(candidate -> candidate.involves(me) && candidate.getStatus() == Match.MatchStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Match not found: " + matchReferenceId));

        end(match, me, MatchEndedEvent.Reason.UNMATCHED);
    }

    /**
     * Creates the match of the pair, or returns the existing one. Must run in the transaction that holds the pair lock.
     */
    Match create(User one, User other) {
        Match candidate = Match.between(one, other);
        return matchRepository.findByUserLowIdAndUserHighId(candidate.getUserLow().getId(), candidate.getUserHigh().getId())
                .orElseGet(() -> {
                    Match match = matchRepository.save(candidate);
                    eventPublisher.publishEvent(new MatchCreatedEvent(match.getReferenceId(),
                            match.getUserLow().getReferenceId(), match.getUserHigh().getReferenceId(), Instant.now()));
                    return match;
                });
    }

    /**
     * Ends the active match of the pair, if there is one.
     */
    void endBetween(User one, User other, User endedBy, MatchEndedEvent.Reason reason) {
        Match candidate = Match.between(one, other);
        matchRepository.findByUserLowIdAndUserHighId(candidate.getUserLow().getId(), candidate.getUserHigh().getId())
                .filter(match -> match.getStatus() == Match.MatchStatus.ACTIVE)
                .ifPresent(match -> end(match, endedBy, reason));
    }

    MatchResponse toResponse(Match match, User me, Reaction theirReaction) {
        return new MatchResponse(match.getReferenceId(), match.getCreatedAt(),
                profileMapper.toPublicProfile(match.partnerOf(me)), ReactionSummary.from(theirReaction));
    }

    private void end(Match match, User endedBy, MatchEndedEvent.Reason reason) {
        match.end(endedBy);
        matchRepository.save(match);
        eventPublisher.publishEvent(new MatchEndedEvent(match.getReferenceId(), endedBy.getReferenceId(), reason, Instant.now()));
    }
}
