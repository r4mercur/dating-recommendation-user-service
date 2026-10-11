package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.event.UserStatusChangedEvent;
import com.bjarne.datingrecommendationsuserservice.search.SearchIndexConsumer;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bjarne.datingrecommendationsuserservice.entity.PostalCode;
import com.bjarne.datingrecommendationsuserservice.repository.PostalCodeRepository;
import com.bjarne.datingrecommendationsuserservice.service.PostalCodeImporter;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class UserApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @MockitoBean
    ProfilePictureStorageService profilePictureStorageService;

    @MockitoSpyBean
    SearchIndexConsumer searchIndexConsumer;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostalCodeRepository postalCodeRepository;

    @Autowired
    PostalCodeImporter postalCodeImporter;

    @Test
    void registrationNeverReturnsPasswordAndRejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON).content(registration(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.age").value(29))
                .andExpect(jsonPath("$.bio").value("Hello there"))
                .andExpect(jsonPath("$.interests[0]").value("music"))
                .andExpect(jsonPath("$.hobbies[0]").value("running"))
                .andExpect(jsonPath("$.address.country").value("DE"))
                .andExpect(jsonPath("$.latitude").value(closeTo(52.53, 0.01)))
                .andExpect(jsonPath("$.longitude").value(closeTo(13.38, 0.01)))
                .andExpect(jsonPath("$.preferences.seekingGenders.length()").value(3))
                .andExpect(jsonPath("$.preferences.minAge").value(18))
                .andExpect(jsonPath("$.preferences.maxAge").value(99))
                .andExpect(jsonPath("$.preferences.maxDistanceKm").value(50))
                .andExpect(jsonPath("$.preferences.ageRangeValid").doesNotExist());

        mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON).content(registration(email.toUpperCase())))
                .andExpect(status().isConflict());
    }

    @Test
    void openApiDocumentDescribesTheApiWithBearerAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Dating Recommendations – User Service"))
                .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/me/reactions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/me/recommendations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/reports'].get").exists());
    }

    @Test
    void healthIsPublicButHidesDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationRejectsUnknownTagsMinorsAndUnlocatableAddresses() throws Exception {
        mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(uniqueEmail(), LocalDate.now().minusYears(30), "[\"underwater_basket_weaving\"]", "10115", "Berlin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("underwater_basket_weaving")));

        mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(uniqueEmail(), LocalDate.now().minusYears(17), "[]", "10115", "Berlin")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(uniqueEmail(), LocalDate.now().minusYears(30), "[]", "00000", "Atlantis")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addressIsLocatedByCityWhenPostalCodeIsUnknownAndRelocatedOnUpdate() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email, LocalDate.now().minusYears(30), "[]", "00000", "Köln")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").value(closeTo(50.94, 0.1)));

        mockMvc.perform(patch("/api/me").header("Authorization", "Bearer " + login(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":{\"city\":\"München\",\"zipCode\":\"80331\",\"country\":\"DE\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.city").value("München"))
                .andExpect(jsonPath("$.latitude").value(closeTo(48.13, 0.01)))
                .andExpect(jsonPath("$.longitude").value(closeTo(11.57, 0.01)));
    }

    @Test
    void preferencesCanBeReadAndReplaced() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email);

        mockMvc.perform(put("/api/me/preferences").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seekingGenders\":[\"FEMALE\"],\"minAge\":25,\"maxAge\":35,\"maxDistanceKm\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seekingGenders[0]").value("FEMALE"))
                .andExpect(jsonPath("$.maxDistanceKm").value(30));

        mockMvc.perform(put("/api/me/preferences").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seekingGenders\":[\"FEMALE\"],\"minAge\":40,\"maxAge\":30,\"maxDistanceKm\":30}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/me/preferences").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seekingGenders\":[],\"minAge\":20,\"maxAge\":30,\"maxDistanceKm\":30}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/me/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seekingGenders.length()").value(1))
                .andExpect(jsonPath("$.minAge").value(25))
                .andExpect(jsonPath("$.maxAge").value(35));
    }

    @Test
    void postalCodesAreImportedOnStartupOnlyOnce() throws Exception {
        assertThat(postalCodeRepository.countByCountryCode("DE")).isEqualTo(27);

        postalCodeImporter.run(new DefaultApplicationArguments());

        assertThat(postalCodeRepository.countByCountryCode("DE")).isEqualTo(27);
    }

    @Test
    void placesForDevDataExcludeBulkReceiversAndHaveStableOrder() {
        List<PostalCode> places = postalCodeRepository.findRegularPlaces("DE");

        assertThat(places).hasSize(27)
                .allSatisfy(place -> assertThat(place.getPlaceName()).doesNotContain(" ", "-"))
                .extracting(PostalCode::getPostalCode)
                .isSorted();
    }

    @Test
    void tagCatalogIsPublic() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'playing_video_games')].label").value("Playing Video Games"));

        mockMvc.perform(get("/api/tags").param("category", "HOBBY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].category", everyItem(is("HOBBY"))));
    }

    @Test
    void meEndpointsRequireTokenAndUseTheTokenSubject() throws Exception {
        String email = uniqueEmail();
        String referenceId = register(email);
        String token = login(email);

        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value(referenceId))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(patch("/api/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"hobbies\":[\"climbing\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value(referenceId))
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.interests[0]").value("music"))
                .andExpect(jsonPath("$.hobbies[0]").value("climbing"));
    }

    @Test
    void publicProfileHidesPrivateFields() throws Exception {
        String viewerEmail = uniqueEmail();
        register(viewerEmail);
        String otherReferenceId = register(uniqueEmail());

        mockMvc.perform(get("/api/user").param("referenceId", otherReferenceId)
                        .header("Authorization", "Bearer " + login(viewerEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value(otherReferenceId))
                .andExpect(jsonPath("$.city").value("Berlin"))
                .andExpect(jsonPath("$.age").value(29))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.birthDate").doesNotExist())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.preferences").doesNotExist());
    }

    @Test
    void statusChangeIsPublishedToKafkaAndBlockingIsNotAllowed() throws Exception {
        String email = uniqueEmail();
        String referenceId = register(email);
        String token = login(email);

        mockMvc.perform(patch("/api/me/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/me/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(searchIndexConsumer, timeout(30_000)).onStatusChanged(argThat((UserStatusChangedEvent event) ->
                event.referenceId().equals(referenceId)
                        && event.oldStatus() == UserStatus.ACTIVE
                        && event.newStatus() == UserStatus.INACTIVE));
    }

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/user").contentType(MediaType.APPLICATION_JSON).content(registration(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.referenceId");
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.access_token");
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String registration(String email) {
        return registration(email, LocalDate.now().minusYears(29), "[\"MUSIC\"]", "10115", "Berlin");
    }

    private static String registration(String email, LocalDate birthDate, String interests, String zipCode, String city) {
        return """
                {
                  "name": "Test User",
                  "email": "%s",
                  "password": "password123",
                  "bio": "Hello there",
                  "birthDate": "%s",
                  "interests": %s,
                  "hobbies": ["Running"],
                  "address": {"street": "Test Street 1", "city": "%s", "zipCode": "%s", "country": "Germany"},
                  "gender": "DIVERSE"
                }
                """.formatted(email, birthDate, interests, city, zipCode);
    }
}
