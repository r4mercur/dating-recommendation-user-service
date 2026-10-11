package com.bjarne.datingrecommendationsuserservice.search;

import com.bjarne.datingrecommendationsuserservice.config.KafkaConfiguration;
import com.bjarne.datingrecommendationsuserservice.messaging.UserProfileUpdatedProducer;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration → user-profile-updated → SearchIndexConsumer → document in Solr.
 * Solr's idle timeout is lowered to 2 s, so the "update after the connection went idle" case (which used to fail
 * with "RST_STREAM: Protocol error" on HTTP/2 and silently drop the event) is covered in seconds instead of minutes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SearchIndexingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Container
    static GenericContainer<?> solr = new GenericContainer<>(DockerImageName.parse("solr:10.0.0"))
            .withEnv("SOLR_OPTS", "-Dsolr.jetty.http.idleTimeout=2000")
            .withExposedPorts(8983)
            .waitingFor(Wait.forHttp("/solr/admin/info/system").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));

    private static final String COLLECTION = "indexing";

    @DynamicPropertySource
    static void solrProperties(DynamicPropertyRegistry registry) {
        registry.add("recommender.solr.enabled", () -> true);
        registry.add("recommender.solr.base-url", SearchIndexingIntegrationTest::solrBaseUrl);
        registry.add("recommender.solr.collection", () -> COLLECTION);
        registry.add("recommender.solr.url", () -> solrBaseUrl() + "/" + COLLECTION);
        registry.add("management.endpoint.health.show-details", () -> "always");
    }

    @MockitoBean
    ProfilePictureStorageService profilePictureStorageService;

    @MockitoSpyBean
    SearchIndexService searchIndexService;

    @Autowired
    MockMvc mockMvc;

    /** Separate HTTP/1.1 client, so the checks do not touch the connection of the application's Solr client. */
    private final HttpClient solrCheck = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Test
    void healthReportsDatabaseSolrAndKafka() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.solr.status").value("UP"))
                .andExpect(jsonPath("$.components.kafka.status").value("UP"))
                .andExpect(jsonPath("$.components.kafka.details.brokers").value(1));
    }

    @Test
    void registeredUserIsIndexed() throws Exception {
        String referenceId = register();

        awaitIndexed(referenceId);
    }

    @Test
    void registrationAfterAnIdleSolrConnectionIsIndexed() throws Exception {
        awaitIndexed(register());

        // longer than Solr's idle timeout: the server has closed the client's connection
        Thread.sleep(5_000);

        awaitIndexed(register());
    }

    @Test
    void temporaryIndexingErrorsAreRetried() throws Exception {
        doThrow(new IllegalStateException("Solr briefly unavailable"))
                .doThrow(new IllegalStateException("Solr briefly unavailable"))
                .doCallRealMethod()
                .when(searchIndexService).index(argThat(user -> "Flaky".equals(user.getName())));

        awaitIndexed(register("Flaky"));
    }

    @Test
    void permanentlyFailingEventsGoToTheDeadLetterTopic() throws Exception {
        doThrow(new IllegalStateException("Solr down"))
                .when(searchIndexService).index(argThat(user -> "Broken".equals(user.getName())));

        String referenceId = register("Broken");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(UserProfileUpdatedProducer.TOPIC + KafkaConfiguration.DEAD_LETTER_SUFFIX));
            List<ConsumerRecord<String, String>> deadLetters = new ArrayList<>();

            // 5 retries with back-off take about 15 s before the record is moved
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(deadLetters::add);
                assertThat(deadLetters).anySatisfy(record -> {
                    assertThat(record.key()).isEqualTo(referenceId);
                    assertThat(new String(record.headers().lastHeader("kafka_dlt-exception-message").value()))
                            .contains("Solr down");
                });
            });
        }
        assertThat(numFound("id:" + referenceId)).isZero();
    }

    private void awaitIndexed(String referenceId) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(numFound("id:" + referenceId)).isEqualTo(1));
    }

    private int numFound(String query) throws Exception {
        URI uri = URI.create(solrBaseUrl() + "/" + COLLECTION + "/select?rows=0&q=" + query.replace(":", "%3A"));
        String body = solrCheck.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
        return JsonPath.read(body, "$.response.numFound");
    }

    private String register() throws Exception {
        return register("Indexed");
    }

    private String register(String name) throws Exception {
        String email = "indexed-" + UUID.randomUUID() + "@example.com";
        String response = mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","email":"%s","password":"password123","birthDate":"1995-05-05","gender":"FEMALE",
                         "interests":["music"],"address":{"city":"Berlin","zipCode":"10115","country":"DE"}}
                        """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.referenceId");
    }

    private static String solrBaseUrl() {
        return "http://" + solr.getHost() + ":" + solr.getMappedPort(8983) + "/solr";
    }
}
