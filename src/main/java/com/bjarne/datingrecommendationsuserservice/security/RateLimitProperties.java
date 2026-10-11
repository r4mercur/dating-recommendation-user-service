package com.bjarne.datingrecommendationsuserservice.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code app.rate-limits.<name>=<count>/<ISO-8601 duration>}, see {@link RateLimiter}.
 */
@ConfigurationProperties("app")
public record RateLimitProperties(Map<String, String> rateLimits) {

    Map<String, RateLimiter.Limit> parsed() {
        Map<String, RateLimiter.Limit> parsed = new HashMap<>();
        if (rateLimits != null) {
            rateLimits.forEach((name, value) -> parsed.put(name, RateLimiter.Limit.parse(name, value)));
        }
        return parsed;
    }
}
