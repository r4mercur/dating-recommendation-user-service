package com.bjarne.datingrecommendationsuserservice.interaction;

import com.bjarne.datingrecommendationsuserservice.dto.ProfileMapper;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.event.ReactionCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.exception.ConflictException;
import com.bjarne.datingrecommendationsuserservice.exception.NotFoundException;
import com.bjarne.datingrecommendationsuserservice.exception.TooManyRequestsException;
import com.bjarne.datingrecommendationsuserservice.interaction.dto.*;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.security.RateLimiter;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final BlockService blockService;
    private final MatchService matchService;
    private final ProfileMapper profileMapper;
    private final InteractionProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter;

    public ReactionService(ReactionRepository reactionRepository,
                           UserRepository userRepository,
                           UserService userService,
                           BlockService blockService,
                           MatchService matchService,
                           ProfileMapper profileMapper,
                           InteractionProperties properties,
                           ApplicationEventPublisher eventPublisher,
                           RateLimiter rateLimiter) {
        this.reactionRepository = reactionRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.blockService = blockService;
        this.matchService = matchService;
        this.profileMapper = profileMapper;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Stores the reaction. If it is positive and the other user already reacted positively, a match is created.
     * Both users are locked for the rest of the transaction, so two simultaneous likes create exactly one match.
     */
    @Transactional
    public ReactionResponse react(String referenceId, ReactionRequest request) {
        rateLimiter.check(RateLimiter.REACTIONS, referenceId);
        if (referenceId.equals(request.targetReferenceId())) {
            throw new BadRequestException("Users cannot react to themselves");
        }

        User me = userService.getByReferenceId(referenceId);
        if (me.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Only active profiles can react to other users");
        }
        User target = userService.getVisibleProfile(request.targetReferenceId());
        if (blockService.isBlockedEitherWay(me, target)) {
            throw new NotFoundException("User not found: " + request.targetReferenceId());
        }
        String message = validMessage(request.type(), request.message());

        userRepository.lockAllById(List.of(me.getId(), target.getId()));

        if (reactionRepository.existsByFromUserIdAndToUserId(me.getId(), target.getId())) {
            throw new ConflictException("You already reacted to this user");
        }
        Integer remaining = remainingToday(me, request.type());
        if (remaining != null && remaining <= 0) {
            throw new TooManyRequestsException("Daily limit for " + request.type() + " reached");
        }

        Reaction reaction = reactionRepository.save(new Reaction(me, target, request.type(), message));
        eventPublisher.publishEvent(new ReactionCreatedEvent(me.getReferenceId(), target.getReferenceId(),
                reaction.getType().name(), reaction.getType().isPositive(), reaction.getCreatedAt()));

        MatchResponse match = null;
        if (request.type().isPositive()) {
            match = reactionRepository.findByFromUserIdAndToUserId(target.getId(), me.getId())
                    .filter(theirs -> theirs.getType().isPositive())
                    .map(theirs -> matchService.toResponse(matchService.create(me, target), me, theirs))
                    .orElse(null);
        }

        return new ReactionResponse(target.getReferenceId(), reaction.getType(), reaction.getMessage(), reaction.getCreatedAt(), match);
    }

    /**
     * All reaction types with their rules and what is left of today's limits.
     */
    @Transactional(readOnly = true)
    public List<ReactionTypeResponse> getReactionTypes(String referenceId) {
        User me = userService.getByReferenceId(referenceId);
        return Arrays.stream(ReactionType.values())
                .map(type -> new ReactionTypeResponse(type, type.isPositive(), type.getMessagePolicy(),
                        properties.dailyLimit(type), remainingToday(me, type)))
                .toList();
    }

    /**
     * Positive reactions to the user that are still unanswered, newest first.
     */
    @Transactional(readOnly = true)
    public List<ReceivedLikeResponse> getReceivedLikes(String referenceId) {
        User me = userService.getByReferenceId(referenceId);
        return reactionRepository.findOpenReceived(me.getId(), ReactionType.positiveTypes(), UserStatus.ACTIVE).stream()
                .map(reaction -> new ReceivedLikeResponse(profileMapper.toPublicProfile(reaction.getFromUser()), ReactionSummary.from(reaction)))
                .toList();
    }

    /**
     * referenceIds of everybody the user already reacted to.
     */
    public Set<String> reactedTo(User user) {
        return Set.copyOf(reactionRepository.findTargetReferenceIds(user.getId()));
    }

    private Integer remainingToday(User user, ReactionType type) {
        Integer limit = properties.dailyLimit(type);
        if (limit == null) {
            return null;
        }

        Instant startOfDay = LocalDate.now(properties.timeZone()).atStartOfDay(properties.timeZone()).toInstant();
        long used = reactionRepository.countByFromUserIdAndTypeAndCreatedAtGreaterThanEqual(user.getId(), type, startOfDay);
        return (int) Math.max(0, limit - used);
    }

    private static String validMessage(ReactionType type, String message) {
        String trimmed = message == null || message.isBlank() ? null : message.trim();
        return switch (type.getMessagePolicy()) {
            case NONE -> {
                if (trimmed != null) {
                    throw new BadRequestException(type + " cannot have a message");
                }
                yield null;
            }
            case REQUIRED -> {
                if (trimmed == null) {
                    throw new BadRequestException(type + " needs a message");
                }
                yield trimmed;
            }
            case OPTIONAL -> trimmed;
        };
    }
}
