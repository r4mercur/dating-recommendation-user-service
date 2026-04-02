package com.bjarne.datingrecommendationsuserservice.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.common.params.CoreAdminParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

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
    public ApplicationRunner solrSetupRun(
            @Value("${recommender.solr.base-url}") String baseUrl,
            @Value("${recommender.solr.collection}") String collectionName,
            @Value("${recommender.solr.enabled}") boolean isSolrEnabled
    ) {
        return args -> {
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

            LOGGER.info("Solr setup finished with collection '{}'.", collectionName);
        };
    }

    private void ensureCoreExists(SolrClient solrClient, String collectionName) throws SolrServerException, IOException {
        CoreAdminRequest statusRequest = new CoreAdminRequest();
        statusRequest.setAction(CoreAdminParams.CoreAdminAction.STATUS);
        CoreAdminResponse statusResponse = statusRequest.process(solrClient);

        boolean coreExists = statusResponse.getCoreStatus(collectionName) != null;
        if (!coreExists) {
            CoreAdminRequest.Create createCore = new CoreAdminRequest.Create();
            createCore.setCoreName(collectionName);
            createCore.setConfigSet("_default");
            createCore.process(solrClient);

            LOGGER.info("Created new core '{}'.", collectionName);
        } else {
            LOGGER.info("Core '{}' already exists.", collectionName);
        }
    }

    private void ensureSchemaFields(SolrClient solrClient) {
        // TODO
    }
}
