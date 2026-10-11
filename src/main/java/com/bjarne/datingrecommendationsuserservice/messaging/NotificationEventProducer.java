package com.bjarne.datingrecommendationsuserservice.messaging;

import com.bjarne.datingrecommendationsuserservice.event.NotificationCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes stored notifications after commit, keyed by the receiving user.
 */
@Component
public class NotificationEventProducer {
    public static final String TOPIC = "notification-created";

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(NotificationCreatedEvent event) {
        kafkaTemplate.send(TOPIC, event.userReferenceId(), event).whenComplete((result, ex) -> {
            if (ex != null) {
                LOGGER.error("Failed to publish notification {}", event.notificationReferenceId(), ex);
            }
        });
    }
}
