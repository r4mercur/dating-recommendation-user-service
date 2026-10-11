package com.bjarne.datingrecommendationsuserservice.search;

import com.bjarne.datingrecommendationsuserservice.event.UserProfileUpdatedEvent;
import com.bjarne.datingrecommendationsuserservice.event.UserStatusChangedEvent;
import com.bjarne.datingrecommendationsuserservice.messaging.UserProfileUpdatedProducer;
import com.bjarne.datingrecommendationsuserservice.messaging.UserStatusChangedProducer;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Updates the search index from user events. The current state is loaded from the database,
 * so replays and out-of-order events end up with the right document.
 */
@Component
public class SearchIndexConsumer {

    static final String GROUP_ID = "user-service-search-indexer";
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchIndexConsumer.class);

    private final UserRepository userRepository;
    private final SearchIndexService searchIndexService;

    public SearchIndexConsumer(UserRepository userRepository, SearchIndexService searchIndexService) {
        this.userRepository = userRepository;
        this.searchIndexService = searchIndexService;
    }

    @KafkaListener(topics = UserProfileUpdatedProducer.TOPIC, groupId = GROUP_ID)
    public void onProfileUpdated(UserProfileUpdatedEvent event) {
        sync(event.referenceId());
    }

    @KafkaListener(topics = UserStatusChangedProducer.TOPIC, groupId = GROUP_ID)
    public void onStatusChanged(UserStatusChangedEvent event) {
        LOGGER.info("User {} changed status from {} to {}", event.referenceId(), event.oldStatus(), event.newStatus());
        sync(event.referenceId());
    }

    private void sync(String referenceId) {
        userRepository.findByReferenceId(referenceId).ifPresentOrElse(
                searchIndexService::index,
                () -> searchIndexService.remove(referenceId));
    }
}
