package com.bjarne.datingrecommendationsuserservice.notification;

import com.bjarne.datingrecommendationsuserservice.event.MatchCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.event.NotificationCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.event.ReactionCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.messaging.InteractionEventProducer;
import com.bjarne.datingrecommendationsuserservice.messaging.NotificationEventProducer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes notifications from interaction events (one shared consumer group, so each event is stored once) and pushes
 * stored notifications to /user/queue/notifications (one random group per instance, so every instance reaches its
 * own WebSocket sessions).
 */
@Component
public class NotificationConsumer {

    static final String WRITER_GROUP = "notification-writer";
    static final String DESTINATION = "/queue/notifications";

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationConsumer(NotificationService notificationService, SimpMessagingTemplate messagingTemplate) {
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = InteractionEventProducer.REACTION_CREATED_TOPIC, groupId = WRITER_GROUP)
    public void onReaction(ReactionCreatedEvent event) {
        notificationService.onReaction(event);
    }

    @KafkaListener(topics = InteractionEventProducer.MATCH_CREATED_TOPIC, groupId = WRITER_GROUP)
    public void onMatch(MatchCreatedEvent event) {
        notificationService.onMatch(event);
    }

    @KafkaListener(id = "notification-push", topics = NotificationEventProducer.TOPIC,
            groupId = "notification-push-${random.uuid}", properties = "auto.offset.reset=latest")
    public void push(NotificationCreatedEvent event) {
        notificationService.find(event.notificationReferenceId(), event.userReferenceId())
                .ifPresent(notification -> messagingTemplate.convertAndSendToUser(event.userReferenceId(), DESTINATION, notification));
    }
}
