package com.bjarne.datingrecommendationsuserservice.security;

import com.bjarne.datingrecommendationsuserservice.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limits per key (user, email or IP), kept in memory of this instance.
 * Limits are configured as {@code app.rate-limits.<name>=<count>/<ISO-8601 duration>}, e.g. {@code login-email=10/PT5M}.
 * Several instances each count on their own; a shared store (e.g. Redis) would be needed to limit across instances.
 */
@Component
public class RateLimiter {

    public static final String LOGIN_IP = "login-ip";
    public static final String LOGIN_EMAIL = "login-email";
    public static final String REGISTRATION_IP = "registration-ip";
    public static final String PASSWORD_CHANGE = "password-change";
    public static final String REACTIONS = "reactions";
    public static final String CHAT_MESSAGES = "chat-messages";
    public static final String REPORTS = "reports";
    public static final String EXTERNAL_SEARCH = "external-search";

    private final Map<String, Limit> limits;
    private final Map<String, Cache<String, Window>> windows = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitProperties properties) {
        this.limits = Map.copyOf(properties.parsed());
    }

    /**
     * Counts one request for the key and refuses it with 429 once the limit of the window is used up.
     * Unknown limit names are not limited, so a missing property never blocks a request.
     */
    public void check(String limitName, String key) {
        Limit limit = limits.get(limitName);
        if (limit == null || key == null) {
            return;
        }

        Window window = windows
                .computeIfAbsent(limitName, _ -> Caffeine.newBuilder().expireAfterWrite(limit.window()).build())
                .get(key, _ -> new Window(System.nanoTime()));

        long retryAfterNanos = window.tryAcquire(limit);
        if (retryAfterNanos > 0) {
            throw new TooManyRequestsException("Too many requests, please try again later", Duration.ofNanos(retryAfterNanos));
        }
    }

    public record Limit(int count, Duration window) {

        public static Limit parse(String name, String value) {
            String[] parts = value.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Rate limit " + name + " must look like <count>/<duration>, e.g. 10/PT1M: " + value);
            }
            return new Limit(Integer.parseInt(parts[0].trim()), Duration.parse(parts[1].trim()));
        }
    }

    private static final class Window {
        private long startNanos;
        private int used;

        Window(long startNanos) {
            this.startNanos = startNanos;
        }

        /** Returns 0 if the request is allowed, otherwise the nanoseconds until the window resets. */
        synchronized long tryAcquire(Limit limit) {
            long now = System.nanoTime();
            long length = limit.window().toNanos();
            if (now - startNanos >= length) {
                startNanos = now;
                used = 0;
            }
            if (used >= limit.count()) {
                return startNanos + length - now;
            }
            used++;
            return 0;
        }
    }
}
