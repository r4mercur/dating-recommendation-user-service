package com.bjarne.datingrecommendationsuserservice.search;

import com.bjarne.datingrecommendationsuserservice.config.e2e.E2eUsers;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recommendations against a real Solr. Base data are the E2E users (profile e2e); additional users each
 * violate exactly one filter. alice: female, 31, Berlin, seeks male/female 25-40 within 25 km.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "e2e"})
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecommendationIntegrationTest {

    // started manually: with PER_CLASS the Spring context is created before @Container fields would be started
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    static GenericContainer<?> solr = new GenericContainer<>(DockerImageName.parse("solr:10.0.0"))
            .withExposedPorts(8983)
            .waitingFor(Wait.forHttp("/solr/admin/info/system").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));

    static {
        Startables.deepStart(postgres, kafka, solr).join();
    }

    @DynamicPropertySource
    static void solrProperties(DynamicPropertyRegistry registry) {
        String baseUrl = "http://" + solr.getHost() + ":" + solr.getMappedPort(8983) + "/solr";
        registry.add("recommender.solr.enabled", () -> true);
        registry.add("recommender.solr.base-url", () -> baseUrl);
        registry.add("recommender.solr.collection", () -> "recommendations");
        registry.add("recommender.solr.url", () -> baseUrl + "/recommendations");
    }

    @MockitoBean
    ProfilePictureStorageService profilePictureStorageService;

    @Autowired
    MockMvc mockMvc;

    private String alice;
    private final Map<String, String> ids = new java.util.HashMap<>();

    @BeforeAll
    void createUsers() throws Exception {
        alice = token(E2eUsers.ALICE.email(), E2eUsers.PASSWORD);

        // excluded: further away than alice' 25 km
        register("dave", "MALE", LocalDate.of(1994, 1, 1), "50667", "Köln", "[\"science\"]", "FEMALE", 25, 40, 500);
        // excluded: older than alice' maximum of 40
        register("erin", "MALE", LocalDate.of(1970, 1, 1), "10115", "Berlin", "[]", "FEMALE", 18, 99, 50);
        // excluded: looks for men only, alice is female
        register("frank", "MALE", LocalDate.of(1994, 1, 1), "10115", "Berlin", "[]", "MALE", 18, 99, 50);
        // excluded: alice (31) is older than his maximum of 28
        register("henry", "MALE", LocalDate.of(1994, 1, 1), "10115", "Berlin", "[]", "FEMALE", 20, 28, 50);
        // excluded: alice is 18 km away, she only accepts 10 km
        register("lena", "FEMALE", LocalDate.of(1996, 1, 1), "12529", "Schönefeld", "[]", "FEMALE", 20, 40, 10);

        // included, without shared tags
        register("gina", "FEMALE", LocalDate.of(1996, 6, 1), "10115", "Berlin", "[\"music\"]", "FEMALE", 20, 40, 50);
        register("ivan", "MALE", LocalDate.of(1994, 1, 1), "10115", "Berlin", "[]", "FEMALE", 18, 99, 50);
        register("mia", "FEMALE", LocalDate.of(1995, 1, 1), "12529", "Schönefeld", "[]", "FEMALE", 20, 40, 50);

        // indexing runs asynchronously via Kafka
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(allRecommendationIds(alice, 50)).hasSize(5));
    }

    @Test
    @Order(1)
    void filtersApplyInBothDirectionsAndSharedTagsRankFirst() throws Exception {
        String page = recommendations(alice, null, 10);
        List<String> order = JsonPath.read(page, "$.items[*].referenceId");

        assertThat(order.subList(0, 2)).containsExactlyInAnyOrder(E2eUsers.BOB.referenceId(), E2eUsers.CAROL.referenceId());
        // no shared tags: closer first (gina and ivan in Berlin, mia 18 km away)
        assertThat(order.subList(2, 4)).containsExactlyInAnyOrder(ids.get("gina"), ids.get("ivan"));
        assertThat(order.get(4)).isEqualTo(ids.get("mia"));
        assertThat((String) JsonPath.read(page, "$.nextCursor")).isNull();

        assertThat(card(page, E2eUsers.BOB.referenceId(), "compatibility")).isEqualTo(75);
        assertThat(card(page, E2eUsers.BOB.referenceId(), "distanceKm")).isEqualTo(0);
        assertThat(card(page, ids.get("gina"), "compatibility")).isEqualTo(0);
        assertThat(card(page, ids.get("mia"), "distanceKm")).isEqualTo(18);

        // anthem and commonCount come from the E2E songs and games: bob shares 2 interests, 1 hobby, 2 artists and 2 games with alice
        assertThat(card(page, E2eUsers.BOB.referenceId(), "anthem.name")).isEqualTo("Somebody Told Me");
        assertThat(card(page, E2eUsers.BOB.referenceId(), "commonCount")).isEqualTo(7);
        // carol shares 1 interest and 2 hobbies, no artists or games, and has the song "Smooth Criminal"
        assertThat(card(page, E2eUsers.CAROL.referenceId(), "commonCount")).isEqualTo(3);
        assertThat(card(page, E2eUsers.CAROL.referenceId(), "anthem.spotifyTrackId")).isEqualTo("7h2ZGUJRnTrcpJsIjzEWiS");
        // users without songs have no anthem
        assertThat(card(page, ids.get("gina"), "commonCount")).isEqualTo(0);
        assertThat(card(page, ids.get("gina"), "anthem")).isNull();
        assertThat((String) JsonPath.read(page, "$.items[0].name")).isNotBlank();
        assertThat((List<?>) JsonPath.read(page, "$.items[?(@.referenceId == '" + E2eUsers.BOB.referenceId() + "')].email")).isEmpty();
    }

    @Test
    @Order(2)
    void cursorPagesThroughAllRecommendations() throws Exception {
        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String page = recommendations(alice, cursor, 2);
            seen.addAll(JsonPath.read(page, "$.items[*].referenceId"));
            cursor = JsonPath.read(page, "$.nextCursor");
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(seen).hasSize(5).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(allRecommendationIds(alice, 50));
        assertThat(pages).isEqualTo(3);
    }

    @Test
    @Order(3)
    void ratedAndBlockingUsersDisappear() throws Exception {
        mockMvc.perform(post("/api/me/reactions").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetReferenceId\":\"" + E2eUsers.BOB.referenceId() + "\",\"type\":\"PASS\"}"))
                .andExpect(status().isCreated());

        String mia = token("mia@example.com", "password123");
        mockMvc.perform(post("/api/me/blocks").header("Authorization", "Bearer " + mia)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceId\":\"" + E2eUsers.ALICE.referenceId() + "\"}"))
                .andExpect(status().isCreated());

        assertThat(allRecommendationIds(alice, 50))
                .doesNotContain(E2eUsers.BOB.referenceId(), ids.get("mia"), E2eUsers.ALICE.referenceId(), E2eUsers.BLOCKED.referenceId())
                .contains(E2eUsers.CAROL.referenceId());
    }

    @Test
    @Order(4)
    void statusAndProfileChangesReachTheIndexViaKafka() throws Exception {
        String ivan = token("ivan@example.com", "password123");
        mockMvc.perform(patch("/api/me/status").header("Authorization", "Bearer " + ivan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        String gina = token("gina@example.com", "password123");
        mockMvc.perform(patch("/api/me").header("Authorization", "Bearer " + gina)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":[\"science\",\"animals\"],\"hobbies\":[\"climbing\",\"board_games\"]}"))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            String page = recommendations(alice, null, 10);
            List<String> order = JsonPath.read(page, "$.items[*].referenceId");
            assertThat(order).doesNotContain(ids.get("ivan"));
            assertThat(order.getFirst()).isEqualTo(ids.get("gina"));
            assertThat(card(page, ids.get("gina"), "compatibility")).isEqualTo(100);
        });
    }

    @Test
    @Order(5)
    void pageSizeIsValidated() throws Exception {
        mockMvc.perform(get("/api/me/recommendations").param("size", "500").header("Authorization", "Bearer " + alice))
                .andExpect(status().isBadRequest());
    }

    private void register(String name, String gender, LocalDate birthDate, String zipCode, String city, String interests,
                          String seeking, int minAge, int maxAge, int maxDistanceKm) throws Exception {
        String body = """
                {"name":"%s","email":"%s@example.com","password":"password123","birthDate":"%s","gender":"%s",
                 "interests":%s,"hobbies":[],
                 "address":{"city":"%s","zipCode":"%s","country":"DE"},
                 "preferences":{"seekingGenders":["%s"],"minAge":%d,"maxAge":%d,"maxDistanceKm":%d}}
                """.formatted(name, name, birthDate, gender, interests, city, zipCode, seeking, minAge, maxAge, maxDistanceKm);

        String response = mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ids.put(name, JsonPath.read(response, "$.referenceId"));
    }

    private String recommendations(String token, String cursor, int size) throws Exception {
        var request = get("/api/me/recommendations").param("size", String.valueOf(size)).header("Authorization", "Bearer " + token);
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<String> allRecommendationIds(String token, int size) throws Exception {
        return JsonPath.read(recommendations(token, null, size), "$.items[*].referenceId");
    }

    private static Object card(String page, String referenceId, String field) {
        List<Object> values = JsonPath.read(page, "$.items[?(@.referenceId == '" + referenceId + "')]." + field);
        assertThat(values).hasSize(1);
        return values.getFirst();
    }

    private String token(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.access_token");
    }
}
