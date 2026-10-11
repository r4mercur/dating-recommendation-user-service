package com.bjarne.datingrecommendationsuserservice.chat;

import com.bjarne.datingrecommendationsuserservice.chat.dto.*;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ChatIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @MockitoBean
    ProfilePictureStorageService profilePictureStorageService;

    @LocalServerPort
    int port;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    KafkaListenerEndpointRegistry kafkaListenerRegistry;
    @Autowired
    SimpUserRegistry simpUserRegistry;

    private final List<StompSession> sessions = new ArrayList<>();

    record TestUser(String referenceId, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    @BeforeEach
    void waitForFanoutConsumers() {
        // the fan-out listeners only read new events, so they must be assigned before anything is sent
        await().atMost(Duration.ofSeconds(60)).until(() -> kafkaListenerRegistry.getListenerContainers().stream()
                .filter(container -> container.getListenerId() != null && container.getListenerId().startsWith("chat-fanout-"))
                .allMatch(container -> container.getAssignedPartitions() != null && !container.getAssignedPartitions().isEmpty()));
    }

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @Test
    void messagesAreStoredListedPagedAndMarkedRead() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");
        String match = match(anna, ben);

        mockMvc.perform(get("/api/me/conversations").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$[0].matchReferenceId").value(match))
                .andExpect(jsonPath("$[0].user.referenceId").value(ben.referenceId()))
                .andExpect(jsonPath("$[0].lastMessage").doesNotExist())
                .andExpect(jsonPath("$[0].unreadCount").value(0));

        for (int i = 1; i <= 5; i++) {
            send(i % 2 == 1 ? anna : ben, match, "  message " + i + "  ").andExpect(status().isCreated())
                    .andExpect(jsonPath("$.content").value("message " + i));
        }

        mockMvc.perform(get("/api/me/conversations").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$[0].lastMessage.content").value("message 5"))
                .andExpect(jsonPath("$[0].unreadCount").value(3));

        String newest = mockMvc.perform(get("/api/me/conversations/" + match + "/messages").param("size", "2")
                        .header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.items[*].content").value(org.hamcrest.Matchers.contains("message 4", "message 5")))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String before = JsonPath.read(newest, "$.items[0].referenceId");
        mockMvc.perform(get("/api/me/conversations/" + match + "/messages").param("size", "10").param("before", before)
                        .header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.items[*].content").value(org.hamcrest.Matchers.contains("message 1", "message 2", "message 3")))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(post("/api/me/conversations/" + match + "/read").header("Authorization", ben.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/conversations").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$[0].unreadCount").value(0));
        mockMvc.perform(get("/api/me/conversations/" + match + "/messages").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.items[0].readAt").isNotEmpty())
                .andExpect(jsonPath("$.items[1].readAt").doesNotExist());
    }

    @Test
    void onlyMatchedUsersCanChat() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");
        TestUser eve = register("eve");
        String match = match(anna, ben);

        send(eve, match, "hi").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/conversations/" + match + "/messages").header("Authorization", eve.bearer()))
                .andExpect(status().isNotFound());
        send(anna, match, "   ").andExpect(status().isBadRequest());
        send(anna, match, "x".repeat(2001)).andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/me/matches/" + match).header("Authorization", ben.bearer()))
                .andExpect(status().isNoContent());
        send(anna, match, "still there?").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/conversations").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void messagesReadReceiptsAndMatchChangesArePushedOverWebSocket() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");

        StompSession annaSession = connect(anna);
        StompSession benSession = connect(ben);
        BlockingQueue<ConversationEvent> annaConversations = subscribe(annaSession, anna, "/user/queue/conversations", ConversationEvent.class);
        BlockingQueue<MessageResponse> annaMessages = subscribe(annaSession, anna, "/user/queue/messages", MessageResponse.class);
        BlockingQueue<ReadReceipt> annaReceipts = subscribe(annaSession, anna, "/user/queue/read-receipts", ReadReceipt.class);
        BlockingQueue<MessageResponse> benMessages = subscribe(benSession, ben, "/user/queue/messages", MessageResponse.class);
        BlockingQueue<ChatError> benErrors = subscribe(benSession, ben, "/user/queue/errors", ChatError.class);

        String match = match(anna, ben);
        ConversationEvent created = annaConversations.poll(20, TimeUnit.SECONDS);
        assertThat(created).isEqualTo(new ConversationEvent(ConversationEvent.Type.CREATED, match));

        // sent via REST, received by both (the sender for other tabs)
        send(anna, match, "Hi Ben!").andExpect(status().isCreated());
        MessageResponse received = benMessages.poll(20, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.content()).isEqualTo("Hi Ben!");
        assertThat(received.senderReferenceId()).isEqualTo(anna.referenceId());
        assertThat(annaMessages.poll(20, TimeUnit.SECONDS)).extracting(MessageResponse::content).isEqualTo("Hi Ben!");

        // sent and read via STOMP
        benSession.send("/app/chat.send", new ChatSendCommand(match, "Hi Anna :)"));
        assertThat(annaMessages.poll(20, TimeUnit.SECONDS)).extracting(MessageResponse::content).isEqualTo("Hi Anna :)");

        benSession.send("/app/chat.read", new ChatReadCommand(match));
        ReadReceipt receipt = annaReceipts.poll(20, TimeUnit.SECONDS);
        assertThat(receipt).isNotNull();
        assertThat(receipt.readerReferenceId()).isEqualTo(ben.referenceId());
        assertThat(receipt.matchReferenceId()).isEqualTo(match);

        // errors of STOMP commands go to the sender's error queue
        benSession.send("/app/chat.send", new ChatSendCommand(UUID.randomUUID().toString(), "lost"));
        assertThat(benErrors.poll(20, TimeUnit.SECONDS)).extracting(ChatError::message).asString().startsWith("Conversation not found");

        mockMvc.perform(delete("/api/me/matches/" + match).header("Authorization", ben.bearer()))
                .andExpect(status().isNoContent());
        assertThat(annaConversations.poll(20, TimeUnit.SECONDS))
                .isEqualTo(new ConversationEvent(ConversationEvent.Type.ENDED, match));
    }

    @Test
    void webSocketRequiresTokenAndOnlyOwnDestinations() throws Exception {
        assertThatThrownBy(() -> connect(new TestUser("nobody", "not-a-token")))
                .isInstanceOf(ExecutionException.class);

        TestUser anna = register("anna");
        StompSession session = connect(anna);
        session.subscribe("/queue/messages-user" + UUID.randomUUID(), new QueueingFrameHandler<>(Object.class, new LinkedBlockingQueue<>()));
        await().atMost(Duration.ofSeconds(10)).until(() -> !session.isConnected());
    }

    private StompSession connect(TestUser user) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());

        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + user.token());
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private <T> BlockingQueue<T> subscribe(StompSession session, TestUser user, String destination, Class<T> type) {
        BlockingQueue<T> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new QueueingFrameHandler<>(type, queue));
        await().atMost(Duration.ofSeconds(10)).until(() -> simpUserRegistry.findSubscriptions(
                subscription -> subscription.getDestination().equals(destination)
                        && subscription.getSession().getUser().getName().equals(user.referenceId())).size() == 1);
        return queue;
    }

    private static final class QueueingFrameHandler<T> implements StompFrameHandler {
        private final Class<T> type;
        private final BlockingQueue<T> queue;

        QueueingFrameHandler(Class<T> type, BlockingQueue<T> queue) {
            this.type = type;
            this.queue = queue;
        }

        @Override
        public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
            return type;
        }

        @Override
        public void handleFrame(@NonNull StompHeaders headers, Object payload) {
            queue.add(type.cast(payload));
        }
    }

    private String match(TestUser one, TestUser other) throws Exception {
        react(one, other).andExpect(status().isCreated());
        String response = react(other, one).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.match.referenceId");
    }

    private ResultActions react(TestUser from, TestUser to) throws Exception {
        return mockMvc.perform(post("/api/me/reactions").header("Authorization", from.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetReferenceId\":\"" + to.referenceId() + "\",\"type\":\"LIKE\"}"));
    }

    private ResultActions send(TestUser from, String match, String content) throws Exception {
        return mockMvc.perform(post("/api/me/conversations/" + match + "/messages").header("Authorization", from.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"));
    }

    private TestUser register(String name) throws Exception {
        String email = name + "-" + UUID.randomUUID() + "@example.com";
        String response = mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","email":"%s","password":"password123","birthDate":"1995-05-05","gender":"FEMALE",
                         "address":{"city":"Berlin","zipCode":"10115","country":"DE"}}
                        """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String referenceId = JsonPath.read(response, "$.referenceId");

        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new TestUser(referenceId, JsonPath.read(login, "$.access_token"));
    }
}
