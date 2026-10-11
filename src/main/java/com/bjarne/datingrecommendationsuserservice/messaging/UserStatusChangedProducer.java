package com.bjarne.datingrecommendationsuserservice.messaging;

import com.bjarne.datingrecommendationsuserservice.event.UserStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserStatusChangedProducer {
    public static final String TOPIC = "user-status-changed";

    private static final Logger LOGGER = LoggerFactory.getLogger(UserStatusChangedProducer.class);

    private final KafkaTemplate<String, UserStatusChangedEvent> kafkaTemplate;

    public UserStatusChangedProducer(KafkaTemplate<String, UserStatusChangedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserStatusChangedEvent event) {
        kafkaTemplate.send(TOPIC, event.referenceId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Failed to publish UserStatusChangedEvent for user {}", event.referenceId(), ex);
                    } else {
                        LOGGER.info("Published UserStatusChangedEvent for user {} ({} -> {}) to partition {} offset {}",
                                event.referenceId(), event.oldStatus(), event.newStatus(),
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
