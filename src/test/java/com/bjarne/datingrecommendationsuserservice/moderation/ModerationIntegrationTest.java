package com.bjarne.datingrecommendationsuserservice.moderation;

import com.bjarne.datingrecommendationsuserservice.entity.Role;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
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
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.admin-emails=first-admin@example.com")
@Testcontainers(disabledWithoutDocker = true)
class ModerationIntegrationTest {

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
    @Autowired
    AdminBootstrap adminBootstrap;

    record TestUser(String referenceId, String email, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    @Test
    void usersReportOthersAndCanBlockThemRightAway() throws Exception {
        TestUser anna = register("anna");
        TestUser ben = register("ben");

        report(anna, ben, "HARASSMENT", "keeps insulting me", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportedReferenceId").value(ben.referenceId()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.blocked").value(true));
        mockMvc.perform(get("/api/me/blocks").header("Authorization", anna.bearer()))
                .andExpect(jsonPath("$[*].user.referenceId", hasItem(ben.referenceId())));

        report(anna, ben, "SPAM", null, false).andExpect(status().isConflict());
        report(anna, anna, "SPAM", null, false).andExpect(status().isBadRequest());
        report(ben, anna, "OTHER", "  ", false).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/me/reports").header("Authorization", anna.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceId\":\"" + UUID.randomUUID() + "\",\"reason\":\"SPAM\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsMayModerate() throws Exception {
        TestUser anna = register("anna");

        mockMvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/reports").header("Authorization", anna.bearer())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/users/" + anna.referenceId() + "/role").header("Authorization", anna.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockingAReportedUserClosesAllTheirReportsAndLocksThemOutImmediately() throws Exception {
        TestUser admin = admin("mod");
        TestUser anna = register("anna");
        TestUser carl = register("carl");
        TestUser troll = register("troll");

        report(anna, troll, "SCAM", "asks for money", false).andExpect(status().isCreated());
        String report = JsonPath.read(report(carl, troll, "FAKE_PROFILE", null, false).andReturn().getResponse().getContentAsString(), "$.referenceId");

        mockMvc.perform(get("/api/admin/reports").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.referenceId == '" + report + "')].reported.profile.referenceId").value(troll.referenceId()))
                .andExpect(jsonPath("$[?(@.referenceId == '" + report + "')].reported.email").value(troll.email()))
                .andExpect(jsonPath("$[?(@.referenceId == '" + report + "')].reported.openReports").value(2))
                .andExpect(jsonPath("$[?(@.referenceId == '" + report + "')].reporterReferenceId").value(carl.referenceId()));

        mockMvc.perform(post("/api/admin/reports/" + report + "/resolve").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"BLOCK_USER\",\"note\":\"scam account\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.reported.status").value("BLOCKED"))
                .andExpect(jsonPath("$.resolvedByReferenceId").value(admin.referenceId()));

        // both reports against the troll are closed
        mockMvc.perform(get("/api/admin/reports").param("status", "RESOLVED").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$[?(@.reported.profile.referenceId == '" + troll.referenceId() + "')].resolutionNote")
                        .value(org.hamcrest.Matchers.contains("scam account", "scam account")));

        // the still valid token of the troll is rejected, and a new login fails
        mockMvc.perform(get("/api/me").header("Authorization", troll.bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Account is blocked or deleted"));
        login(troll.email()).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/reports/" + report + "/resolve").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DISMISS\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void dismissedReportsLeaveTheUserAlone() throws Exception {
        TestUser admin = admin("mod");
        TestUser anna = register("anna");
        TestUser ben = register("ben");
        String report = JsonPath.read(report(anna, ben, "SPAM", null, false).andReturn().getResponse().getContentAsString(), "$.referenceId");

        mockMvc.perform(post("/api/admin/reports/" + report + "/resolve").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DISMISS\",\"note\":\"no spam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.reported.status").value("ACTIVE"));
        mockMvc.perform(get("/api/me").header("Authorization", ben.bearer())).andExpect(status().isOk());
    }

    @Test
    void adminsManageStatusAndRolesButNotTheirOwn() throws Exception {
        TestUser admin = admin("mod");
        TestUser ben = register("ben");

        mockMvc.perform(put("/api/admin/users/" + ben.referenceId() + "/status").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        login(ben.email()).andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/admin/users/" + ben.referenceId() + "/status").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        login(ben.email()).andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/users/" + ben.referenceId() + "/status").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MARKED_FOR_DELETION\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/users/" + admin.referenceId() + "/status").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/users/" + admin.referenceId() + "/role").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());

        // a new admin gets the role with the next login
        mockMvc.perform(put("/api/admin/users/" + ben.referenceId() + "/role").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + token(ben.email())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/search/reindex").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(0));
    }

    @Test
    void configuredAdminEmailsBecomeAdminsOnStartup() throws Exception {
        TestUser first = register("first-admin", "first-admin@example.com");

        adminBootstrap.run(new DefaultApplicationArguments());

        assertThat(userRepository.findByReferenceId(first.referenceId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + token(first.email())))
                .andExpect(status().isOk());
    }

    private ResultActions report(TestUser from, TestUser target, String reason, String comment, boolean block) throws Exception {
        String body = """
                {"referenceId":"%s","reason":"%s",%s"block":%s}
                """.formatted(target.referenceId(), reason, comment == null ? "" : "\"comment\":\"" + comment + "\",", block);
        return mockMvc.perform(post("/api/me/reports").header("Authorization", from.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Registers a user and makes them admin directly in the database; the role is in the token after login. */
    private TestUser admin(String name) throws Exception {
        TestUser user = register(name);
        var entity = userRepository.findByReferenceId(user.referenceId()).orElseThrow();
        entity.setRole(Role.ADMIN);
        userRepository.save(entity);
        return new TestUser(user.referenceId(), user.email(), token(user.email()));
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
        return new TestUser(JsonPath.read(response, "$.referenceId"), email, token(email));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + email + "\",\"password\":\"password123\"}"));
    }

    private String token(String email) throws Exception {
        return JsonPath.read(login(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.access_token");
    }
}
