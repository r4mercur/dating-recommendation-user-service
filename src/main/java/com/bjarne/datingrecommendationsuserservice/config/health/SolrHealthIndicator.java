package com.bjarne.datingrecommendationsuserservice.config.health;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrPing;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

/**
 * Pings the recommendation collection. Part of /actuator/health when Solr is enabled.
 */
@Component("solr")
@ConditionalOnProperty(name = "recommender.solr.enabled", havingValue = "true")
public class SolrHealthIndicator extends AbstractHealthIndicator {

    private final SolrClient solrClient;

    public SolrHealthIndicator(SolrClient solrClient) {
        super("Solr health check failed");
        this.solrClient = solrClient;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        int status = new SolrPing().process(solrClient).getStatus();
        builder.status(status == 0 ? Status.UP : Status.DOWN)
                .withDetail("status", status);
    }
}
