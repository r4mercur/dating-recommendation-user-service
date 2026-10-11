package com.bjarne.datingrecommendationsuserservice.chat;

import com.bjarne.datingrecommendationsuserservice.chat.dto.ConversationEvent;
import com.bjarne.datingrecommendationsuserservice.chat.dto.MessageResponse;
import com.bjarne.datingrecommendationsuserservice.chat.dto.ReadReceipt;
import com.bjarne.datingrecommendationsuserservice.event.ChatMessageSentEvent;
import com.bjarne.datingrecommendationsuserservice.event.ChatMessagesReadEvent;
import com.bjarne.datingrecommendationsuserservice.event.MatchCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.event.MatchEndedEvent;
import com.bjarne.datingrecommendationsuserservice.interaction.MatchRepository;
import com.bjarne.datingrecommendationsuserservice.messaging.ChatEventProducer;
import com.bjarne.datingrecommendationsuserservice.messaging.InteractionEventProducer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pushes chat and match events to the WebSocket sessions connected to this instance.
 * Every instance needs every event, so each listener uses its own random consumer group and only reads new events.
 * Both users get the event, so other open tabs of the sender stay in sync too.
 */
@Component
public class ChatFanoutConsumer {

    static final String MESSAGES = "/queue/messages";
    static final String READ_RECEIPTS = "/queue/read-receipts";
    static final String CONVERSATIONS = "/queue/conversations";

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final MatchRepository matchRepository;

    public ChatFanoutConsumer(SimpMessagingTemplate messagingTemplate,
                              MessageRepository messageRepository,
                              MatchRepository matchRepository) {
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
        this.matchRepository = matchRepository;
    }

    @KafkaListener(id = "chat-fanout-messages", topics = ChatEventProducer.MESSAGE_SENT_TOPIC,
            groupId = "chat-fanout-${random.uuid}", properties = "auto.offset.reset=latest")
    public void onMessageSent(ChatMessageSentEvent event) {
        matchRepository.findByReferenceId(event.matchReferenceId())
                .flatMap(match -> messageRepository.findByReferenceIdAndMatchId(event.messageReferenceId(), match.getId()))
                .map(message -> MessageResponse.from(message, event.matchReferenceId()))
                .ifPresent(message -> sendToBoth(event.senderReferenceId(), event.recipientReferenceId(), MESSAGES, message));
    }

    @KafkaListener(id = "chat-fanout-read-receipts", topics = ChatEventProducer.MESSAGES_READ_TOPIC,
            groupId = "chat-fanout-${random.uuid}", properties = "auto.offset.reset=latest")
    public void onMessagesRead(ChatMessagesReadEvent event) {
        sendToBoth(event.readerReferenceId(), event.partnerReferenceId(), READ_RECEIPTS,
                new ReadReceipt(event.matchReferenceId(), event.readerReferenceId(), event.readAt()));
    }

    @KafkaListener(id = "chat-fanout-match-created", topics = InteractionEventProducer.MATCH_CREATED_TOPIC,
            groupId = "chat-fanout-${random.uuid}", properties = "auto.offset.reset=latest")
    public void onMatchCreated(MatchCreatedEvent event) {
        sendToBoth(event.userReferenceId(), event.otherUserReferenceId(), CONVERSATIONS,
                new ConversationEvent(ConversationEvent.Type.CREATED, event.matchReferenceId()));
    }

    @KafkaListener(id = "chat-fanout-match-ended", topics = InteractionEventProducer.MATCH_ENDED_TOPIC,
            groupId = "chat-fanout-${random.uuid}", properties = "auto.offset.reset=latest")
    public void onMatchEnded(MatchEndedEvent event) {
        matchRepository.findByReferenceId(event.matchReferenceId()).ifPresent(match ->
                sendToBoth(match.getUserLow().getReferenceId(), match.getUserHigh().getReferenceId(), CONVERSATIONS,
                        new ConversationEvent(ConversationEvent.Type.ENDED, event.matchReferenceId())));
    }

    private void sendToBoth(String userReferenceId, String otherReferenceId, String destination, Object payload) {
        for (String user : List.of(userReferenceId, otherReferenceId)) {
            messagingTemplate.convertAndSendToUser(user, destination, payload);
        }
    }
}
