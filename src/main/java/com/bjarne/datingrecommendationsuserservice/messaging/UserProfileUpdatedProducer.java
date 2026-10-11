package com.bjarne.datingrecommendationsuserservice.messaging;

import com.bjarne.datingrecommendationsuserservice.event.UserProfileUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserProfileUpdatedProducer {
    public static final String TOPIC = "user-profile-updated";

    private static final Logger LOGGER = LoggerFactory.getLogger(UserProfileUpdatedProducer.class);

    private final KafkaTemplate<String, UserProfileUpdatedEvent> kafkaTemplate;

    public UserProfileUpdatedProducer(KafkaTemplate<String, UserProfileUpdatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserProfileUpdatedEvent event) {
        kafkaTemplate.send(TOPIC, event.referenceId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Failed to publish UserProfileUpdatedEvent for user {}", event.referenceId(), ex);
                    } else {
                        LOGGER.debug("Published UserProfileUpdatedEvent for user {} to partition {} offset {}",
                                event.referenceId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
