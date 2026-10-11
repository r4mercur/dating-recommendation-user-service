package com.bjarne.datingrecommendationsuserservice.messaging;

import com.bjarne.datingrecommendationsuserservice.event.MatchCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.event.MatchEndedEvent;
import com.bjarne.datingrecommendationsuserservice.event.ReactionCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes reactions and match changes after commit. Consumers follow in Phase 4 (messaging) and 5 (notifications).
 */
@Component
public class InteractionEventProducer {
    public static final String REACTION_CREATED_TOPIC = "reaction-created";
    public static final String MATCH_CREATED_TOPIC = "match-created";
    public static final String MATCH_ENDED_TOPIC = "match-ended";

    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InteractionEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Keyed by the receiving user, so all reactions for one user stay in order. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReactionCreatedEvent event) {
        send(REACTION_CREATED_TOPIC, event.toReferenceId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchCreatedEvent event) {
        send(MATCH_CREATED_TOPIC, event.matchReferenceId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchEndedEvent event) {
        send(MATCH_ENDED_TOPIC, event.matchReferenceId(), event);
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                LOGGER.error("Failed to publish {} with key {}", event.getClass().getSimpleName(), key, ex);
            } else {
                LOGGER.debug("Published {} with key {} to {}", event.getClass().getSimpleName(), key, topic);
            }
        });
    }
}
