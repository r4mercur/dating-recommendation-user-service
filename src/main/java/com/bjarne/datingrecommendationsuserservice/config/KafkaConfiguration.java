package com.bjarne.datingrecommendationsuserservice.config;

import com.bjarne.datingrecommendationsuserservice.messaging.ChatEventProducer;
import com.bjarne.datingrecommendationsuserservice.messaging.InteractionEventProducer;
import com.bjarne.datingrecommendationsuserservice.messaging.NotificationEventProducer;
import com.bjarne.datingrecommendationsuserservice.messaging.UserProfileUpdatedProducer;
import com.bjarne.datingrecommendationsuserservice.messaging.UserStatusChangedProducer;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.List;

/**
 * Producer, consumer and KafkaTemplate are auto-configured by spring-boot-starter-kafka
 * from the spring.kafka.* properties. Topic definitions and the listener error handling live here.
 */
@Configuration
public class KafkaConfiguration {

    /** Failed records end up in "&lt;topic&gt;.dlt" with the original key, value and the exception as headers. */
    public static final String DEAD_LETTER_SUFFIX = ".dlt";

    private static final List<String> TOPICS = List.of(
            UserStatusChangedProducer.TOPIC,
            UserProfileUpdatedProducer.TOPIC,
            InteractionEventProducer.REACTION_CREATED_TOPIC,
            InteractionEventProducer.MATCH_CREATED_TOPIC,
            InteractionEventProducer.MATCH_ENDED_TOPIC,
            ChatEventProducer.MESSAGE_SENT_TOPIC,
            ChatEventProducer.MESSAGES_READ_TOPIC,
            NotificationEventProducer.TOPIC);

    /**
     * Retries a failing listener with growing pauses (0.5 s, 1 s, 2 s, 4 s, 8 s), so short outages of Solr or the
     * database do not lose events. If it still fails, the record goes to the dead-letter topic instead of being
     * dropped. Spring Boot applies this handler to all @KafkaListener containers.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public KafkaAdmin.NewTopics deadLetterTopics() {
        return new KafkaAdmin.NewTopics(TOPICS.stream()
                .map(topic -> TopicBuilder.name(topic + DEAD_LETTER_SUFFIX).partitions(1).replicas(1).build())
                .toArray(NewTopic[]::new));
    }

    @Bean
    public NewTopic userStatusChangedTopic() {
        return TopicBuilder.name(UserStatusChangedProducer.TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userProfileUpdatedTopic() {
        return TopicBuilder.name(UserProfileUpdatedProducer.TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reactionCreatedTopic() {
        return TopicBuilder.name(InteractionEventProducer.REACTION_CREATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic matchCreatedTopic() {
        return TopicBuilder.name(InteractionEventProducer.MATCH_CREATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic chatMessageSentTopic() {
        return TopicBuilder.name(ChatEventProducer.MESSAGE_SENT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic chatMessagesReadTopic() {
        return TopicBuilder.name(ChatEventProducer.MESSAGES_READ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationCreatedTopic() {
        return TopicBuilder.name(NotificationEventProducer.TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic matchEndedTopic() {
        return TopicBuilder.name(InteractionEventProducer.MATCH_ENDED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
