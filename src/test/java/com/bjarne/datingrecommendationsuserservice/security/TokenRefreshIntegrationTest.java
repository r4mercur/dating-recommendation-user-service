package com.bjarne.datingrecommendationsuserservice.security;

import com.bjarne.datingrecommendationsuserservice.entity.Role;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TokenRefreshIntegrationTest {

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
    UserService userService;

    record Tokens(String access, String refresh) {
    }

    @Test
    void loginReturnsARefreshTokenThatRotates() throws Exception {
        String email = register();
        login(email)
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_expires_in").value(allOf(greaterThan(29 * 24 * 3600), lessThanOrEqualTo(30 * 24 * 3600))));
        Tokens first = tokens(login(email));

        Tokens second = tokens(refresh(first.refresh()).andExpect(status().isOk()));
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + second.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        Tokens third = tokens(refresh(second.refresh()).andExpect(status().isOk()));
        refresh(third.refresh()).andExpect(status().isOk());
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
        String email = register();
        Tokens original = tokens(login(email));
        Tokens rotated = tokens(refresh(original.refresh()).andExpect(status().isOk()));

        // someone replays the old token: refused, and the legitimate new token is revoked too
        refresh(original.refresh()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid refresh token"));
        refresh(rotated.refresh()).andExpect(status().isUnauthorized());

        // other logins of the same user are not affected
        Tokens otherDevice = tokens(login(email));
        refresh(otherDevice.refresh()).andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Tokens tokens = tokens(login(register()));

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + tokens.refresh() + "\"}"))
                .andExpect(status().isNoContent());

        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeLogsOutOtherDevices() throws Exception {
        String email = register();
        Tokens laptop = tokens(login(email));
        Tokens phone = tokens(login(email));

        mockMvc.perform(put("/api/me/password").header("Authorization", "Bearer " + laptop.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"new-password-456\"}"))
                .andExpect(status().isNoContent());

        refresh(phone.refresh()).andExpect(status().isUnauthorized());
        refresh(laptop.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void blockedAccountsCannotRefresh() throws Exception {
        String email = register();
        Tokens tokens = tokens(login(email));
        String referenceId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getReferenceId();

        userService.changeStatus(referenceId, UserStatus.BLOCKED);

        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void aRefreshPicksUpRoleChanges() throws Exception {
        String email = register();
        Tokens tokens = tokens(login(email));
        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + tokens.access())).andExpect(status().isForbidden());

        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        Tokens refreshed = tokens(refresh(tokens.refresh()).andExpect(status().isOk()));
        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + refreshed.access())).andExpect(status().isOk());
    }

    @Test
    void unknownOrMissingTokensAreRejected() throws Exception {
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"" + refreshToken + "\"}"));
    }

    private static Tokens tokens(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.access_token"), JsonPath.read(body, "$.refresh_token"));
    }

    private String register() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Refresh","email":"%s","password":"password123","birthDate":"1995-05-05","gender":"FEMALE",
                         "address":{"city":"Berlin","zipCode":"10115","country":"DE"}}
                        """.formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }
}
