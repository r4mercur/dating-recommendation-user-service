package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import io.minio.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Profile pictures in an S3-compatible object storage (SeaweedFS locally). The MinIO Java SDK is only used
 * as generic S3 client.
 */
@Service
public class ProfilePictureStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfilePictureStorageService.class);

    private final MinioClient minioClient;
    private final String bucketName;
    private final Duration urlExpiry;

    public ProfilePictureStorageService(
            @Value("${storage.endpoint}") String endpoint,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey,
            @Value("${storage.bucket-name}") String bucketName,
            @Value("${storage.url-expiry:PT12H}") Duration urlExpiry,
            @Value("${storage.region:us-east-1}") String region
    ) throws Exception {
        this.bucketName = bucketName;
        this.urlExpiry = urlExpiry;
        // a fixed region avoids the bucket location lookup before signing URLs
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .region(region)
                .credentials(accessKey, secretKey)
                .build();

        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!bucketExists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * Stores the picture under a generated name and returns its object key.
     */
    public String upload(User user, MultipartFile file) {
        String key = "users/" + user.getReferenceId() + "/" + UUID.randomUUID() + extension(file.getContentType());

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .stream(file.getInputStream(), file.getSize(), (long) -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return key;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload profile picture", e);
        }
    }

    /**
     * Signs a short-lived download URL. Signing is done locally, no request to MinIO per call.
     */
    public String presignedUrl(String key) {
        if (key == null) {
            return null;
        }

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .method(Http.Method.GET)
                            .expiry((int) urlExpiry.toSeconds(), TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            LOGGER.warn("Failed to sign URL for profile picture {}", key, e);
            return null;
        }
    }

    public void delete(String key) {
        if (key == null) {
            return;
        }

        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(key).build());
        } catch (Exception e) {
            LOGGER.warn("Failed to delete old profile picture {}", key, e);
        }
    }

    private static String extension(String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) {
            return "";
        }

        String subtype = contentType.substring("image/".length()).toLowerCase(Locale.ROOT);
        if (subtype.equals("jpeg")) {
            return ".jpg";
        }
        return subtype.matches("[a-z0-9]{1,5}") ? "." + subtype : "";
    }
}
