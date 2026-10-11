package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the storage against SeaweedFS with the image, command and credentials from compose.yaml.
 */
@Testcontainers(disabledWithoutDocker = true)
class ProfilePictureStorageIntegrationTest {

    private static final String ACCESS_KEY = "seaweed";
    private static final String SECRET_KEY = "seaweed-secret";

    @Container
    static GenericContainer<?> seaweedfs = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.48"))
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333")
            .withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
            .withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
            .withExposedPorts(8333)
            // anonymous requests are rejected with 403 once the S3 API is up
            .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCode(403).withStartupTimeout(Duration.ofMinutes(2)));

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void picturesAreUploadedServedViaSignedUrlsAndDeleted() throws Exception {
        String bucket = "pictures-" + UUID.randomUUID().toString().substring(0, 8);
        ProfilePictureStorageService storage = storage(SECRET_KEY, bucket);
        // the bucket exists now, a second start must not fail
        storage(SECRET_KEY, bucket);

        User user = new User();
        user.setReferenceId(UUID.randomUUID().toString());
        byte[] image = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

        String key = storage.upload(user, new MockMultipartFile("file", "../../evil name.png", "image/png", image));
        assertThat(key).startsWith("users/" + user.getReferenceId() + "/").endsWith(".png").doesNotContain("..", "evil");

        String url = storage.presignedUrl(key);
        HttpResponse<byte[]> download = get(url);
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(image);
        assertThat(download.headers().firstValue("Content-Type")).hasValue("image/png");

        // signatures are really checked: a tampered URL is rejected
        String tampered = url.replaceFirst("X-Amz-Signature=.", "X-Amz-Signature=" + (url.contains("X-Amz-Signature=0") ? "1" : "0"));
        assertThat(get(tampered).statusCode()).isEqualTo(403);

        storage.delete(key);
        assertThat(get(url).statusCode()).isEqualTo(404);
        assertThat(storage.presignedUrl(null)).isNull();
    }

    @Test
    void wrongCredentialsAreRejected() {
        assertThatThrownBy(() -> storage("wrong-secret", "pictures"))
                .satisfies(e -> assertThat(e.toString()).contains("code='AccessDenied'"));
    }

    private static ProfilePictureStorageService storage(String secretKey, String bucket) throws Exception {
        String endpoint = "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(8333);
        return new ProfilePictureStorageService(endpoint, ACCESS_KEY, secretKey, bucket, Duration.ofMinutes(5), "us-east-1");
    }

    private HttpResponse<byte[]> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
