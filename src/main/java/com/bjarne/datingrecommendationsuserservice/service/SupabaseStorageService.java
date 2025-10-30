package com.bjarne.datingrecommendationsuserservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class SupabaseStorageService {
    private final RestTemplate restTemplate;
    private final String supabaseUrl;
    private final String supabaseServiceKey;
    private final String bucketName;

    public SupabaseStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-key}")String supabaseServiceKey,
            @Value("${supabase.storage.bucket}") String bucketName) {
        this.restTemplate = new RestTemplate();
        this.supabaseUrl = supabaseUrl;
        this.supabaseServiceKey = supabaseServiceKey;
        this.bucketName = bucketName;
    }

    public String upload(String bucket, String path, MultipartFile file) {
        try {
            if (bucket == null || bucket.isBlank()) {
                bucket = bucketName;
            }

            String endpoint = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucket, path);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseServiceKey);
            headers.setContentType(MediaType.parseMediaType(file.getContentType() != null ? file.getContentType() : "application/octet-stream"));
            headers.add("x-upsert", "true");

            HttpEntity<byte[]> entity = new HttpEntity<>(file.getBytes(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, bucket, path);
            } else {
                throw new RuntimeException("Upload to supabase failed: " + response.getStatusCode());
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not upload file", e);
        }
    }
}
