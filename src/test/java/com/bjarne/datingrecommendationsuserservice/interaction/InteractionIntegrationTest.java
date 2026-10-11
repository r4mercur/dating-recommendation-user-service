package com.bjarne.datingrecommendationsuserservice.interaction;

import com.bjarne.datingrecommendationsuserservice.event.MatchCreatedEvent;
import com.bjarne.datingrecommendationsuserservice.event.MatchEndedEvent;
import com.bjarne.datingrecommendationsuserservice.interaction.dto.ReactionRequest;
import com.bjarne.datingrecommendationsuserservice.interaction.dto.ReactionResponse;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"interaction.daily-limits.SUPER_LIKE=2", "interaction.daily-limits.COMPLIMENT=5"})
@RecordApplicationEvents
@Testcontainers(disabledWithoutDocker = true)
class InteractionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @MockitoBean
    ProfilePictureStorageService profilePictureStorageService;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ReactionService reactionService;
    @Autowired
    MatchRepository matchRepository;
    @Autowired
    ApplicationEvents events;

    record TestUser(String referenceId, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    @Test
    void mutualPositiveReactionsCreateAMatchVisibleToBoth() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");

        react(anna, ben, "LIKE", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LIKE"))
                .andExpect(jsonPath("$.match").doesNotExist());

        mockMvc.perform(get("/api/me/likes-received").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$[0].user.referenceId").value(anna.referenceId()))
                .andExpect(jsonPath("$[0].reaction.type").value("LIKE"));

        react(ben, anna, "SUPER_LIKE", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.match.user.referenceId").value(anna.referenceId()))
                .andExpect(jsonPath("$.match.theirReaction.type").value("LIKE"));

        mockMvc.perform(get("/api/me/matches").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.referenceId").value(ben.referenceId()))
                .andExpect(jsonPath("$[0].user.email").doesNotExist())
                .andExpect(jsonPath("$[0].theirReaction.type").value("SUPER_LIKE"));
        mockMvc.perform(get("/api/me/matches").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$[0].user.referenceId").value(anna.referenceId()));

        // answered likes are no longer open
        mockMvc.perform(get("/api/me/likes-received").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(events.stream(MatchCreatedEvent.class)).hasSize(1);
    }

    @Test
    void passNeverMatchesAndReactionsCannotBeRepeated() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");

        react(anna, ben, "LIKE", null).andExpect(status().isCreated());
        react(ben, anna, "PASS", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.match").doesNotExist());

        react(ben, anna, "LIKE", null).andExpect(status().isConflict());
        react(anna, anna, "LIKE", null).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/me/reactions").header("Authorization", anna.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetReferenceId\":\"" + UUID.randomUUID() + "\",\"type\":\"LIKE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/me/reactions").header("Authorization", anna.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetReferenceId\":\"" + ben.referenceId() + "\",\"type\":\"WINK\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/me/matches").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void complimentsNeedAMessageAndOtherTypesMustNotHaveOne() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");

        react(anna, ben, "COMPLIMENT", "  ").andExpect(status().isBadRequest());
        react(anna, ben, "LIKE", "hello").andExpect(status().isBadRequest());
        react(anna, ben, "COMPLIMENT", "  Nice climbing pictures!  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Nice climbing pictures!"));

        mockMvc.perform(get("/api/me/likes-received").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$[0].reaction.type").value("COMPLIMENT"))
                .andExpect(jsonPath("$[0].reaction.message").value("Nice climbing pictures!"));
    }

    @Test
    void dailyLimitsAreEnforcedAndReported() throws Exception {
        TestUser anna = register("anna");
        List<TestUser> others = List.of(register("ben"), register("carl"), register("dora"));

        react(anna, others.get(0), "SUPER_LIKE", null).andExpect(status().isCreated());
        react(anna, others.get(1), "SUPER_LIKE", null).andExpect(status().isCreated());
        react(anna, others.get(2), "SUPER_LIKE", null).andExpect(status().isTooManyRequests());
        react(anna, others.get(2), "LIKE", null).andExpect(status().isCreated());

        mockMvc.perform(get("/api/me/reactions/types").header("Authorization", anna.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'SUPER_LIKE')].dailyLimit").value(2))
                .andExpect(jsonPath("$[?(@.type == 'SUPER_LIKE')].remainingToday").value(0))
                .andExpect(jsonPath("$[?(@.type == 'COMPLIMENT')].message").value("REQUIRED"))
                .andExpect(jsonPath("$[?(@.type == 'COMPLIMENT')].remainingToday").value(5))
                .andExpect(jsonPath("$[?(@.type == 'LIKE')].remainingToday").value((Object) null))
                .andExpect(jsonPath("$[?(@.type == 'PASS')].positive").value(false));
    }

    @Test
    void unmatchEndsTheMatchForBoth() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");
        react(anna, ben, "LIKE", null);
        String match = JsonPath.read(react(ben, anna, "LIKE", null).andReturn().getResponse().getContentAsString(), "$.match.referenceId");

        mockMvc.perform(delete("/api/me/matches/" + match).header("Authorization", register("eve").bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/me/matches/" + match).header("Authorization", anna.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/me/matches/" + match).header("Authorization", ben.bearer()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/me/matches").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(events.stream(MatchEndedEvent.class))
                .anyMatch(event -> event.matchReferenceId().equals(match) && event.reason() == MatchEndedEvent.Reason.UNMATCHED);
    }

    @Test
    void blocksEndMatchesAndHideUsersInBothDirections() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");
        TestUser carl = register("carl");
        react(anna, ben, "LIKE", null);
        react(ben, anna, "LIKE", null);
        react(carl, anna, "LIKE", null);

        mockMvc.perform(post("/api/me/blocks").header("Authorization", anna.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceId\":\"" + ben.referenceId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.referenceId").value(ben.referenceId()));
        mockMvc.perform(post("/api/me/blocks").header("Authorization", anna.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceId\":\"" + carl.referenceId() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/me/matches").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/me/likes-received").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
        // the blocked user can neither see nor react to the blocker
        mockMvc.perform(get("/api/user").param("referenceId", anna.referenceId()).header("Authorization", carl.bearer()))
                .andExpect(status().isNotFound());
        react(carl, anna, "SUPER_LIKE", null).andExpect(status().isNotFound());
        assertThat(events.stream(MatchEndedEvent.class)).anyMatch(event -> event.reason() == MatchEndedEvent.Reason.BLOCKED);

        mockMvc.perform(get("/api/me/blocks").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(delete("/api/me/blocks/" + carl.referenceId()).header("Authorization", anna.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/user").param("referenceId", anna.referenceId()).header("Authorization", carl.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/likes-received").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$[0].user.referenceId").value(carl.referenceId()));
    }

    @Test
    void simultaneousLikesCreateExactlyOneMatch() throws Exception {
        for (int round = 0; round < 5; round++) {
            TestUser anna = register("anna");
            TestUser ben = register("ben");
            CountDownLatch start = new CountDownLatch(1);

            try (var executor = Executors.newFixedThreadPool(2)) {
                CompletableFuture<ReactionResponse> first = CompletableFuture.supplyAsync(() -> likeWhenStarted(start, anna, ben), executor);
                CompletableFuture<ReactionResponse> second = CompletableFuture.supplyAsync(() -> likeWhenStarted(start, ben, anna), executor);
                start.countDown();

                List<ReactionResponse> responses = List.of(first.join(), second.join());
                assertThat(responses).filteredOn(response -> response.match() != null).hasSize(1);
            }
        }
    }

    private ReactionResponse likeWhenStarted(CountDownLatch start, TestUser from, TestUser to) {
        try {
            start.await();
            return reactionService.react(from.referenceId(), new ReactionRequest(to.referenceId(), ReactionType.LIKE, null));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions react(TestUser from, TestUser to, String type, String message) throws Exception {
        String body = message == null
                ? "{\"targetReferenceId\":\"%s\",\"type\":\"%s\"}".formatted(to.referenceId(), type)
                : "{\"targetReferenceId\":\"%s\",\"type\":\"%s\",\"message\":\"%s\"}".formatted(to.referenceId(), type, message);
        return mockMvc.perform(post("/api/me/reactions").header("Authorization", from.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
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
