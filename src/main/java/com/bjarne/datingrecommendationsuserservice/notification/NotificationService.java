package com.bjarne.datingrecommendationsuserservice.notification;

import com.bjarne.datingrecommendationsuserservice.chat.MessageRepository;
import com.bjarne.datingrecommendationsuserservice.dto.ProfileMapper;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.event.MatchCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.event.NotificationCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.event.ReactionCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.exception.NotFoundException;
import com.bjarne.datingrecommendationsuserservice.interaction.BlockService;
import com.bjarne.datingrecommendationsuserservice.interaction.Match;
import com.bjarne.datingrecommendationsuserservice.interaction.ReactionRepository;
import com.bjarne.datingrecommendationsuserservice.notification.dto.NotificationPage;
import com.bjarne.datingrecommendationsuserservice.notification.dto.NotificationResponse;
import com.bjarne.datingrecommendationsuserservice.notification.dto.UnreadCounts;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stores notifications for new likes and matches (fed by Kafka, see {@link NotificationConsumer}) and serves them.
 * Nothing is created about people who are inactive or blocked in either direction, and such notifications are
 * hidden when they become so later.
 */
@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ReactionRepository reactionRepository;
    private final MessageRepository messageRepository;
    private final BlockService blockService;
    private final ProfileMapper profileMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration retention;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               UserService userService,
                               ReactionRepository reactionRepository,
                               MessageRepository messageRepository,
                               BlockService blockService,
                               ProfileMapper profileMapper,
                               ApplicationEventPublisher eventPublisher,
                               @Value("${app.notifications.retention:P90D}") Duration retention) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.reactionRepository = reactionRepository;
        this.messageRepository = messageRepository;
        this.blockService = blockService;
        this.profileMapper = profileMapper;
        this.eventPublisher = eventPublisher;
        this.retention = retention;
    }

    /**
     * A positive reaction becomes a NEW_LIKE for the receiver, unless it completed a match (then NEW_MATCH says it all).
     */
    @Transactional
    public void onReaction(ReactionCreatedEvent event) {
        if (!event.positive()) {
            return;
        }
        Optional<User> recipient = userRepository.findByReferenceId(event.toReferenceId());
        Optional<User> actor = userRepository.findByReferenceId(event.fromReferenceId());
        if (recipient.isEmpty() || actor.isEmpty()) {
            return;
        }

        boolean completesMatch = reactionRepository.findByFromUserIdAndToUserId(recipient.get().getId(), actor.get().getId())
                .filter(reaction -> reaction.getType().isPositive())
                .isPresent();
        if (!completesMatch) {
            create(Notification.like(recipient.get(), actor.get(), event.type()));
        }
    }

    @Transactional
    public void onMatch(MatchCreatedEvent event) {
        Optional<User> one = userRepository.findByReferenceId(event.userReferenceId());
        Optional<User> other = userRepository.findByReferenceId(event.otherUserReferenceId());
        if (one.isEmpty() || other.isEmpty()) {
            return;
        }
        create(Notification.match(one.get(), other.get(), event.matchReferenceId()));
        create(Notification.match(other.get(), one.get(), event.matchReferenceId()));
    }

    @Transactional(readOnly = true)
    public NotificationPage getNotifications(String referenceId, String before, int size) {
        User me = userService.getByReferenceId(referenceId);
        long beforeId = before == null ? Long.MAX_VALUE : notificationRepository.findByReferenceIdAndUserId(before, me.getId())
                .orElseThrow(() -> new NotFoundException("Notification not found: " + before)).getId();

        List<Notification> notifications = notificationRepository.findVisible(me.getId(), beforeId, UserStatus.ACTIVE,
                PageRequest.of(0, size + 1));
        boolean hasMore = notifications.size() > size;
        return new NotificationPage(notifications.stream().limit(size).map(this::toResponse).toList(), hasMore);
    }

    @Transactional(readOnly = true)
    public UnreadCounts getUnreadCounts(String referenceId) {
        User me = userService.getByReferenceId(referenceId);
        return new UnreadCounts(
                notificationRepository.countVisibleUnread(me.getId(), UserStatus.ACTIVE),
                messageRepository.countAllUnread(me.getId(), Match.MatchStatus.ACTIVE));
    }

    @Transactional
    public void markRead(String referenceId, String notificationReferenceId) {
        User me = userService.getByReferenceId(referenceId);
        notificationRepository.findByReferenceIdAndUserId(notificationReferenceId, me.getId())
                .orElseThrow(() -> new NotFoundException("Notification not found: " + notificationReferenceId))
                .markRead(Instant.now());
    }

    @Transactional
    public void markAllRead(String referenceId) {
        notificationRepository.markAllRead(userService.getByReferenceId(referenceId).getId(), Instant.now());
    }

    /** For the WebSocket push; empty if the notification is gone. */
    @Transactional(readOnly = true)
    public Optional<NotificationResponse> find(String notificationReferenceId, String userReferenceId) {
        return userRepository.findByReferenceId(userReferenceId)
                .flatMap(user -> notificationRepository.findByReferenceIdAndUserId(notificationReferenceId, user.getId()))
                .map(this::toResponse);
    }

    @Scheduled(cron = "${app.notifications.cleanup-cron:0 45 3 * * *}")
    @Transactional
    public void deleteOldReadNotifications() {
        int deleted = notificationRepository.deleteReadBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            LOGGER.info("Deleted {} read notifications older than {}", deleted, retention);
        }
    }

    /** Idempotent: Kafka may deliver an event twice. */
    private void create(Notification notification) {
        User recipient = notification.getUser();
        User actor = notification.getActor();
        if (actor.getStatus() != UserStatus.ACTIVE || blockService.isBlockedEitherWay(recipient, actor)
                || notificationRepository.existsByUserIdAndSourceKey(recipient.getId(), notification.getSourceKey())) {
            return;
        }

        notificationRepository.save(notification);
        eventPublisher.publishEvent(new NotificationCreatedEvent(notification.getReferenceId(), recipient.getReferenceId(), Instant.now()));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getReferenceId(),
                notification.getType(),
                notification.getActor() == null ? null : profileMapper.toPublicProfile(notification.getActor()),
                notification.getReactionType(),
                notification.getMatchReferenceId(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }
}
