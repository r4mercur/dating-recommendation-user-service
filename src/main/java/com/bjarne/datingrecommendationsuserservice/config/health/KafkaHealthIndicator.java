package com.bjarne.datingrecommendationsuserservice.config.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Checks that the Kafka cluster answers. Without Kafka, index updates and chat pushes are lost after commit.
 */
@Component("kafka")
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private static final int TIMEOUT_MS = 3000;

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        super("Kafka health check failed");
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var cluster = admin.describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MS));
            builder.up()
                    .withDetail("clusterId", cluster.clusterId().get(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .withDetail("brokers", cluster.nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).size());
        }
    }
}
