package com.bjarne.datingrecommendationsuserservice.presence;

import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PresenceIntegrationTest {

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
    UserRepository userRepository;

    record TestUser(String referenceId, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    @Test
    void matchesShowWhetherThePartnerIsOnlineUnlessTheyHideIt() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");
        react(anna, ben);
        react(ben, anna);   // ben just made a request: online

        mockMvc.perform(get("/api/me/matches").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$[0].user.referenceId").value(ben.referenceId()))
                .andExpect(jsonPath("$[0].user.online").value(true));

        // ben has been away for ten minutes
        var benEntity = userRepository.findByReferenceId(ben.referenceId()).orElseThrow();
        benEntity.setLastActiveAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        userRepository.save(benEntity);
        mockMvc.perform(get("/api/me/matches").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$[0].user.online").value(false));

        // ben hides the status: others see "unknown"
        mockMvc.perform(put("/api/me/privacy").header("Authorization", ben.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"showOnlineStatus\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showOnlineStatus").value(false));
        mockMvc.perform(get("/api/me/matches").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$[0].user.online").value(nullValue()));
        mockMvc.perform(get("/api/me/conversations").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$[0].user.online").value(nullValue()));
    }

    @Test
    void requestsRecordActivity() throws Exception {
        TestUser anna = register("anna");
        var before = userRepository.findByReferenceId(anna.referenceId()).orElseThrow().getLastActiveAt();

        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showOnlineStatus").value(true));

        var after = userRepository.findByReferenceId(anna.referenceId()).orElseThrow().getLastActiveAt();
        org.assertj.core.api.Assertions.assertThat(before).isNull();
        org.assertj.core.api.Assertions.assertThat(after).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
    }

    private void react(TestUser from, TestUser to) throws Exception {
        mockMvc.perform(post("/api/me/reactions").header("Authorization", from.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetReferenceId\":\"" + to.referenceId() + "\",\"type\":\"LIKE\"}"))
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
