package com.bjarne.datingrecommendationsuserservice.security;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Low limits on purpose. Helper requests use a random client address each, so the per-address limits only
 * apply where a test uses a fixed address.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.rate-limits.login-ip=3/PT1M",
        "app.rate-limits.login-email=3/PT1M",
        "app.rate-limits.registration-ip=2/PT1M",
        "app.rate-limits.reactions=2/PT1M",
        "app.rate-limits.chat-messages=2/PT1M",
        "app.rate-limits.external-search=2/PT1M"})
@Testcontainers(disabledWithoutDocker = true)
class RateLimitIntegrationTest {

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

    record TestUser(String referenceId, String email, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    @Test
    void loginIsLimitedPerAccountEvenFromDifferentAddresses() throws Exception {
        TestUser anna = register("anna");    // 1st login of anna

        login(anna.email(), "wrong-1", randomAddress()).andExpect(status().isUnauthorized());
        login(anna.email(), "wrong-2", randomAddress()).andExpect(status().isUnauthorized());
        // even the correct password is refused now
        login(anna.email(), "password123", randomAddress())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Too many requests, please try again later"));
    }

    @Test
    void loginIsLimitedPerAddress() throws Exception {
        RequestPostProcessor address = fixedAddress("10.20.30.40");
        for (int attempt = 0; attempt < 3; attempt++) {
            login("unknown-" + UUID.randomUUID() + "@example.com", "password123", address).andExpect(status().isUnauthorized());
        }
        login("unknown-" + UUID.randomUUID() + "@example.com", "password123", address).andExpect(status().isTooManyRequests());
    }

    @Test
    void registrationIsLimitedPerAddress() throws Exception {
        RequestPostProcessor address = fixedAddress("10.20.30.41");
        registration("one", address).andExpect(status().isCreated());
        registration("two", address).andExpect(status().isCreated());
        registration("three", address).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void reactionsAndChatMessagesAreLimitedPerUser() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");
        TestUser carl = register("carl");
        TestUser dora = register("dora");

        react(anna, ben).andExpect(status().isCreated());
        react(anna, carl).andExpect(status().isCreated());
        react(anna, dora).andExpect(status().isTooManyRequests());
        String match = JsonPath.read(react(ben, anna).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.match.referenceId");

        send(ben, match).andExpect(status().isCreated());
        send(ben, match).andExpect(status().isCreated());
        send(ben, match).andExpect(status().isTooManyRequests());
        // limits are per user
        send(anna, match).andExpect(status().isCreated());
    }

    @Test
    void externalSearchesAreLimitedPerUser() throws Exception {
        TestUser anna = register("anna");

        // without Spotify credentials in tests the search answers 503; the limit applies before calling Spotify
        for (int search = 0; search < 2; search++) {
            mockMvc.perform(get("/api/music/search").param("q", "killers").header("Authorization", anna.bearer()))
                    .andExpect(status().is(not(429)));
        }
        mockMvc.perform(get("/api/games/search").param("q", "witcher").header("Authorization", anna.bearer()))
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions react(TestUser from, TestUser to) throws Exception {
        return mockMvc.perform(post("/api/me/reactions").header("Authorization", from.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetReferenceId\":\"" + to.referenceId() + "\",\"type\":\"LIKE\"}"));
    }

    private ResultActions send(TestUser from, String match) throws Exception {
        return mockMvc.perform(post("/api/me/conversations/" + match + "/messages").header("Authorization", from.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hello\"}"));
    }

    private TestUser register(String name) throws Exception {
        String email = name + "-" + UUID.randomUUID() + "@example.com";
        String response = registration(name, email, randomAddress()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(login(email, "password123", randomAddress()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.access_token");
        return new TestUser(JsonPath.read(response, "$.referenceId"), email, token);
    }

    private ResultActions registration(String name, RequestPostProcessor address) throws Exception {
        return registration(name, name + "-" + UUID.randomUUID() + "@example.com", address);
    }

    private ResultActions registration(String name, String email, RequestPostProcessor address) throws Exception {
        return mockMvc.perform(post("/api/user").with(address).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","email":"%s","password":"password123","birthDate":"1995-05-05","gender":"FEMALE",
                 "address":{"city":"Berlin","zipCode":"10115","country":"DE"}}
                """.formatted(name, email)));
    }

    private ResultActions login(String email, String password, RequestPostProcessor address) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(address).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private static RequestPostProcessor fixedAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static RequestPostProcessor randomAddress() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return fixedAddress("172.16." + random.nextInt(256) + "." + random.nextInt(1, 255));
    }
}
