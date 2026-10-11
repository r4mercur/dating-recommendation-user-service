package com.bjarne.datingrecommendationsuserservice.notification;

import com.bjarne.datingrecommendationsuserservice.event.MatchCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.notification.dto.NotificationResponse;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.jayway.jsonpath.JsonPath;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class NotificationIntegrationTest {

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
    @Autowired
    NotificationService notificationService;

    record TestUser(String referenceId, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    @Test
    void likesAndMatchesBecomeNotificationsWithoutDuplicates() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");

        react(anna, ben, "SUPER_LIKE");
        String bens = awaitNotifications(ben, 1);
        assertThat((String) JsonPath.read(bens, "$.items[0].type")).isEqualTo("NEW_LIKE");
        assertThat((String) JsonPath.read(bens, "$.items[0].reactionType")).isEqualTo("SUPER_LIKE");
        assertThat((String) JsonPath.read(bens, "$.items[0].actor.referenceId")).isEqualTo(anna.referenceId());
        assertThat((Object) JsonPath.read(bens, "$.items[0].readAt")).isNull();

        // ben likes back: a match for both, but no extra "like" for anna
        String match = JsonPath.read(react(ben, anna, "LIKE").andReturn().getResponse().getContentAsString(), "$.match.referenceId");
        String annas = awaitNotifications(anna, 1);
        assertThat((String) JsonPath.read(annas, "$.items[0].type")).isEqualTo("NEW_MATCH");
        assertThat((String) JsonPath.read(annas, "$.items[0].matchReferenceId")).isEqualTo(match);
        bens = awaitNotifications(ben, 2);
        assertThat((List<String>) JsonPath.read(bens, "$.items[*].type")).containsExactly("NEW_MATCH", "NEW_LIKE");

        // the badge counts unread notifications and chat messages
        mockMvc.perform(post("/api/me/conversations/" + match + "/messages").header("Authorization", ben.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Hi!\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/me/notifications/unread-count").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.notifications").value(1))
                .andExpect(jsonPath("$.messages").value(1));

        // Kafka delivers at least once: the same event again creates nothing new
        notificationService.onMatch(new MatchCreatedEvent(match, anna.referenceId(), ben.referenceId(), Instant.now()));
        mockMvc.perform(get("/api/me/notifications").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void notificationsCanBeMarkedReadOneByOneOrAll() throws Exception {
        TestUser ben = register("ben");
        react(register("anna"), ben, "LIKE");
        react(register("carl"), ben, "COMPLIMENT", "Nice profile!");
        String page = awaitNotifications(ben, 2);
        String newest = JsonPath.read(page, "$.items[0].referenceId");

        mockMvc.perform(post("/api/me/notifications/" + newest + "/read").header("Authorization", ben.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/notifications/unread-count").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.notifications").value(1));

        // older page
        mockMvc.perform(get("/api/me/notifications").param("size", "1").param("before", newest).header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(post("/api/me/notifications/read").header("Authorization", ben.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/notifications/unread-count").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.notifications").value(0));
        mockMvc.perform(post("/api/me/notifications/" + UUID.randomUUID() + "/read").header("Authorization", ben.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void passesCreateNothingAndBlockedPeopleDisappear() throws Exception {
        TestUser dora = register("dora");
        TestUser carl = register("carl");
        TestUser eve = register("eve");

        react(eve, dora, "PASS");
        react(carl, dora, "LIKE");
        String page = awaitNotifications(dora, 1);
        assertThat((String) JsonPath.read(page, "$.items[0].actor.referenceId")).isEqualTo(carl.referenceId());

        mockMvc.perform(post("/api/me/blocks").header("Authorization", dora.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceId\":\"" + carl.referenceId() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/me/notifications").header("Authorization", dora.bearer()))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/me/notifications/unread-count").header("Authorization", dora.bearer()))
                .andExpect(jsonPath("$.notifications").value(0));
    }

    @Test
    void newNotificationsArePushedOverWebSocket() throws Exception {
        await().atMost(Duration.ofSeconds(60)).until(() -> kafkaListenerRegistry.getListenerContainer("notification-push") != null
                && !kafkaListenerRegistry.getListenerContainer("notification-push").getAssignedPartitions().isEmpty());

        TestUser ben = register("ben");
        TestUser anna = register("anna");
        BlockingQueue<NotificationResponse> pushed = new LinkedBlockingQueue<>();
        StompSession session = connect(ben);
        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override
            public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
                return NotificationResponse.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                pushed.add((NotificationResponse) payload);
            }
        });
        await().atMost(Duration.ofSeconds(10)).until(() -> !simpUserRegistry.findSubscriptions(subscription ->
                subscription.getDestination().equals("/user/queue/notifications")
                        && subscription.getSession().getUser().getName().equals(ben.referenceId())).isEmpty());

        react(anna, ben, "LIKE");

        NotificationResponse notification = pushed.poll(30, TimeUnit.SECONDS);
        assertThat(notification).isNotNull();
        assertThat(notification.type()).isEqualTo(NotificationType.NEW_LIKE);
        assertThat(notification.actor().referenceId()).isEqualTo(anna.referenceId());
        session.disconnect();
    }

    /** Notifications are written asynchronously from Kafka events. */
    private String awaitNotifications(TestUser user, int count) {
        String[] page = new String[1];
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            page[0] = mockMvc.perform(get("/api/me/notifications").header("Authorization", user.bearer()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat((List<?>) JsonPath.read(page[0], "$.items")).hasSize(count);
        });
        return page[0];
    }

    private StompSession connect(TestUser user) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", user.bearer());
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);
    }

    private ResultActions react(TestUser from, TestUser to, String type) throws Exception {
        return react(from, to, type, null);
    }

    private ResultActions react(TestUser from, TestUser to, String type, String message) throws Exception {
        String body = message == null
                ? "{\"targetReferenceId\":\"%s\",\"type\":\"%s\"}".formatted(to.referenceId(), type)
                : "{\"targetReferenceId\":\"%s\",\"type\":\"%s\",\"message\":\"%s\"}".formatted(to.referenceId(), type, message);
        return mockMvc.perform(post("/api/me/reactions").header("Authorization", from.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private TestUser register(String name) throws Exception {
        String email = name + "-" + UUID.randomUUID() + "@example.com";
        String response = mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","email":"%s","password":"password123","birthDate":"1995-05-05","gender":"FEMALE",
                         "address":{"city":"Berlin","zipCode":"10115","country":"DE"}}
                        """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new TestUser(JsonPath.read(response, "$.referenceId"), JsonPath.read(login, "$.access_token"));
    }
}
