package com.bjarne.datingrecommendationsuserservice.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.apache.solr.common.params.CoreAdminParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Configuration
public class SolrConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SolrConfiguration.class);

    @Bean
    public SolrClient solrClient(@Value("${recommender.solr.url}") String solrUrl) {
        return new HttpJdkSolrClient.Builder(solrUrl)
                .withConnectionTimeout(10000, TimeUnit.MILLISECONDS)
                .withIdleTimeout(60000, TimeUnit.MILLISECONDS)
                .build();
    }

    @Bean
    @Order(10)
    public ApplicationRunner solrSetupRun(
            @Value("${recommender.solr.base-url}") String baseUrl,
            @Value("${recommender.solr.collection}") String collectionName,
            @Value("${recommender.solr.enabled}") boolean isSolrEnabled
    ) {
        return _ -> {
            if (!isSolrEnabled) {
                LOGGER.info("Solr disabled.");
                return;
            }

            try (SolrClient adminClient = new HttpJdkSolrClient.Builder(baseUrl)
                    .withConnectionTimeout(10000, TimeUnit.MILLISECONDS)
                    .build()) {
                ensureCoreExists(adminClient, collectionName);
            }

            try (SolrClient collectionClient = new HttpJdkSolrClient.Builder(baseUrl + "/" + collectionName)
                    .withConnectionTimeout(10000, TimeUnit.MILLISECONDS)
                    .build()) {
                ensureSchemaFields(collectionClient);
            }

            ensureCommitPolicy(baseUrl, collectionName);
            LOGGER.info("Solr setup finished with collection '{}'.", collectionName);
        };
    }

    private void ensureCoreExists(SolrClient solrClient, String collectionName) throws SolrServerException, IOException {
        CoreAdminRequest statusRequest = new CoreAdminRequest();
        statusRequest.setAction(CoreAdminParams.CoreAdminAction.STATUS);
        CoreAdminResponse statusResponse = statusRequest.process(solrClient);

        boolean coreExists = statusResponse.getCoreStatus(collectionName) != null;
        if (!coreExists) {
            CollectionAdminRequest.Create create =
                    CollectionAdminRequest.createCollection(collectionName, "_default", 1, 1);
            create.process(solrClient);

            LOGGER.info("Created new core '{}'.", collectionName);
        } else {
            LOGGER.info("Core '{}' already exists.", collectionName);
        }
    }

    private void ensureSchemaFields(SolrClient solrClient) throws SolrServerException, IOException {
        SchemaRequest.Fields fieldsRequest = new SchemaRequest.Fields();
        SchemaResponse.FieldsResponse fieldsResponse = fieldsRequest.process(solrClient);

        Set<String> existingFields = fieldsResponse.getFields().stream()
                .map(field -> field.get("name").toString())
                .collect(Collectors.toSet());

        List<Map<String, Object>> desiredFields = List.of(
                field("referenceId", "string", false),
                field("name", "string", false),
                field("age", "pint", false),
                field("gender", "string", false),
                field("interests", "string", true),
                field("hobbies", "string", true),
                field("city", "string", false),
                field("street", "string", false),
                field("country", "string", false),
                field("zipCode", "string", false)
        );

        List<SchemaRequest.Update> updateList = new ArrayList<>();
        desiredFields.forEach(
                fieldDefinition -> {
                    String fieldName = (String) fieldDefinition.get("name");
                    if (!existingFields.contains(fieldName)) {
                        updateList.add(new SchemaRequest.AddField(fieldDefinition));
                        LOGGER.info("Added field '{}' to Solr schema.", fieldName);
                    }
                }
        );

        if (!updateList.isEmpty()) {
            SchemaRequest.MultiUpdate multiUpdate = new SchemaRequest.MultiUpdate(updateList);
            multiUpdate.process(solrClient);
            LOGGER.info("Updated Solr schema.");
        } else {
            LOGGER.info("All required fields already exist in Solr schema.");
        }
    }

    private void ensureCommitPolicy(String baseUrl, String collectionName) throws IOException, InterruptedException {
        String endpoint = baseUrl + "/" + collectionName + "/config";
        String payload = """
        {
          "set-property": {
            "updateHandler.autoSoftCommit.maxTime": 5000,
            "updateHandler.autoCommit.maxTime": 60000
          }
        }
        """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300) {
            throw new IllegalStateException("Failed to set Solr commit policy: " + response.body());
        }

        LOGGER.info("Applied Solr commit policy (soft=5000ms, hard=60000ms) for '{}'.", collectionName);
    }


    private static Map<String, Object> field(String name, String type, boolean multiValued) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", name);
        attributes.put("type", type);
        attributes.put("multiValued", multiValued);
        return attributes;
    }
}
