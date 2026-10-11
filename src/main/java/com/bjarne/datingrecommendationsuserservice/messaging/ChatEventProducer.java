package com.bjarne.datingrecommendationsuserservice.messaging;

import com.bjarne.datingrecommendationsuserservice.event.ChatMessageSentEvent;
import com.bjarne.datingrecommendationsuserservice.event.ChatMessagesReadEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes chat events after commit, keyed by match so that the events of one conversation stay in order.
 */
@Component
public class ChatEventProducer {
    public static final String MESSAGE_SENT_TOPIC = "chat-message-sent";
    public static final String MESSAGES_READ_TOPIC = "chat-messages-read";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ChatEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ChatMessageSentEvent event) {
        send(MESSAGE_SENT_TOPIC, event.matchReferenceId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ChatMessagesReadEvent event) {
        send(MESSAGES_READ_TOPIC, event.matchReferenceId(), event);
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                LOGGER.error("Failed to publish {} for match {}", event.getClass().getSimpleName(), key, ex);
            }
        });
    }
}
