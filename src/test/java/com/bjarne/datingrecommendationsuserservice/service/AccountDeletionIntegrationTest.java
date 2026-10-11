package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.chat.MessageRepository;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.interaction.MatchRepository;
import com.bjarne.datingrecommendationsuserservice.interaction.ReactionRepository;
import com.bjarne.datingrecommendationsuserservice.moderation.ReportRepository;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DELETE /api/me hides the account at once; the purge deletes the data. The grace period is zero here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.account-deletion.grace-period=PT0S")
@Testcontainers(disabledWithoutDocker = true)
class AccountDeletionIntegrationTest {

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
    AccountPurgeService accountPurgeService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserService userService;
    @Autowired
    ReactionRepository reactionRepository;
    @Autowired
    MatchRepository matchRepository;
    @Autowired
    MessageRepository messageRepository;
    @Autowired
    ReportRepository reportRepository;

    record TestUser(String referenceId, String email, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    @Test
    void deletionNeedsThePasswordAndHidesTheAccountImmediately() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");

        delete(anna, "wrong-password").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(status().isOk());

        delete(anna, "password123").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").header("Authorization", anna.bearer())).andExpect(status().isUnauthorized());
        login(anna.email()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user").param("referenceId", anna.referenceId()).header("Authorization", ben.bearer()))
                .andExpect(status().isNotFound());
        assertThat(userRepository.findByReferenceId(anna.referenceId()).orElseThrow().getDeletionRequestedAt()).isNotNull();
    }

    @Test
    void purgeRemovesTheAccountWithAllItsDataAndFreesTheEmail() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");
        TestUser carl = register("carl");

        var annaEntity = userRepository.findByReferenceId(anna.referenceId()).orElseThrow();
        annaEntity.setPhotoKey("users/" + anna.referenceId() + "/photo.jpg");
        userRepository.save(annaEntity);

        react(anna, ben);
        String match = JsonPath.read(react(ben, anna).andReturn().getResponse().getContentAsString(), "$.match.referenceId");
        mockMvc.perform(post("/api/me/conversations/" + match + "/messages").header("Authorization", ben.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Hi Anna\"}"))
                .andExpect(status().isCreated());
        String filedReport = JsonPath.read(mockMvc.perform(post("/api/me/reports").header("Authorization", anna.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"referenceId\":\"" + carl.referenceId() + "\",\"reason\":\"SPAM\"}"))
                .andReturn().getResponse().getContentAsString(), "$.referenceId");
        Long matchId = matchRepository.findByReferenceId(match).orElseThrow().getId();

        delete(anna, "password123").andExpect(status().isNoContent());
        assertThat(accountPurgeService.purgeExpired()).isGreaterThanOrEqualTo(1);

        assertThat(userRepository.findByReferenceId(anna.referenceId())).isEmpty();
        assertThat(matchRepository.findById(matchId)).isEmpty();
        assertThat(messageRepository.findLatest(matchId, org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
        assertThat(reactionRepository.findTargetReferenceIds(userRepository.findByReferenceId(ben.referenceId()).orElseThrow().getId()))
                .doesNotContain(anna.referenceId());
        // reports the user filed stay for moderation, without reporter
        assertThat(reportRepository.findByReferenceId(filedReport).orElseThrow().getReporter()).isNull();
        verify(profilePictureStorageService).delete("users/" + anna.referenceId() + "/photo.jpg");

        mockMvc.perform(get("/api/me/conversations").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
        register("anna-again", anna.email());
    }

    @Test
    void restoredAccountsAreNotPurged() throws Exception {
        TestUser anna = register("anna");
        delete(anna, "password123").andExpect(status().isNoContent());

        // an admin restores the account during the grace period
        userService.changeStatus(anna.referenceId(), UserStatus.ACTIVE);
        accountPurgeService.purgeExpired();

        var restored = userRepository.findByReferenceId(anna.referenceId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(restored.getDeletionRequestedAt()).isNull();
        login(anna.email()).andExpect(status().isOk());
    }

    private ResultActions delete(TestUser user, String password) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/me")
                .header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }

    private ResultActions react(TestUser from, TestUser to) throws Exception {
        return mockMvc.perform(post("/api/me/reactions").header("Authorization", from.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetReferenceId\":\"" + to.referenceId() + "\",\"type\":\"LIKE\"}"))
                .andExpect(status().isCreated());
    }

    private TestUser register(String name) throws Exception {
        return register(name, name + "-" + UUID.randomUUID() + "@example.com");
    }

    private TestUser register(String name, String email) throws Exception {
        String response = mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","email":"%s","password":"password123","birthDate":"1995-05-05","gender":"FEMALE",
                         "address":{"city":"Berlin","zipCode":"10115","country":"DE"}}
                        """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(login(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.access_token");
        return new TestUser(JsonPath.read(response, "$.referenceId"), email, token);
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + email + "\",\"password\":\"password123\"}"));
    }
}
