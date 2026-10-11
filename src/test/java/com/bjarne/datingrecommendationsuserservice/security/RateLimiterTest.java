package com.bjarne.datingrecommendationsuserservice.security;

import com.bjarne.datingrecommendationsuserservice.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter(new RateLimitProperties(Map.of(
            "two-per-minute", "2/PT1M",
            "short-window", "1/PT0.2S")));

    @Test
    void requestsAboveTheLimitAreRefusedWithRetryAfter() {
        rateLimiter.check("two-per-minute", "anna");
        rateLimiter.check("two-per-minute", "anna");

        assertThatThrownBy(() -> rateLimiter.check("two-per-minute", "anna"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e ->
                        assertThat(e.getRetryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1)));
    }

    @Test
    void keysAreCountedSeparately() {
        rateLimiter.check("two-per-minute", "anna");
        rateLimiter.check("two-per-minute", "anna");

        assertThatCode(() -> rateLimiter.check("two-per-minute", "ben")).doesNotThrowAnyException();
    }

    @Test
    void theLimitResetsWithTheNextWindow() throws InterruptedException {
        rateLimiter.check("short-window", "anna");
        assertThatThrownBy(() -> rateLimiter.check("short-window", "anna")).isInstanceOf(TooManyRequestsException.class);

        Thread.sleep(300);

        assertThatCode(() -> rateLimiter.check("short-window", "anna")).doesNotThrowAnyException();
    }

    @Test
    void unconfiguredLimitsDoNotBlock() {
        for (int i = 0; i < 1000; i++) {
            rateLimiter.check("not-configured", "anna");
        }
    }

    @Test
    void invalidConfigurationFailsOnStartup() {
        assertThatThrownBy(() -> new RateLimiter(new RateLimitProperties(Map.of("broken", "ten per minute"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("broken");
    }
}
