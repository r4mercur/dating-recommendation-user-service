package com.bjarne.datingrecommendationsuserservice.external;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRateLimiterTest {

    @Test
    void requestsAreSpacedSoThatTheLimitIsNeverExceeded() throws Exception {
        RequestRateLimiter limiter = new RequestRateLimiter(20, Duration.ofSeconds(10));
        List<Long> times = Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int request = 0; request < 12; request++) {
                futures.add(executor.submit(() -> {
                    boolean acquired = limiter.tryAcquire();
                    times.add(System.nanoTime());
                    assertThat(acquired).isTrue();
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }

        List<Long> sorted = times.stream().sorted().toList();
        // 20 per second means at least 50 ms between two requests
        for (int index = 1; index < sorted.size(); index++) {
            assertThat(Duration.ofNanos(sorted.get(index) - sorted.get(index - 1))).isGreaterThanOrEqualTo(Duration.ofMillis(45));
        }
    }

    @Test
    void fourPerSecondTakesAboutAQuarterSecondPerRequest() {
        RequestRateLimiter limiter = new RequestRateLimiter(4, Duration.ofSeconds(5));
        long start = System.nanoTime();
        for (int request = 0; request < 5; request++) {
            assertThat(limiter.tryAcquire()).isTrue();
        }
        // the first one is free: 4 gaps of at least 250 ms
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(1000));
    }

    @Test
    void aRequestThatWouldWaitTooLongIsRefusedInsteadOfQueued() {
        RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofMillis(100));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }
}
