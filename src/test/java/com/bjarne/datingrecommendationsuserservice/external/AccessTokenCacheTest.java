package com.bjarne.datingrecommendationsuserservice.external;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenCacheTest {

    private final MutableClock clock = new MutableClock();
    private final AtomicInteger loads = new AtomicInteger();
    private final AccessTokenCache cache = new AccessTokenCache(
            () -> new AccessTokenCache.Token("token-" + loads.incrementAndGet(), Duration.ofHours(1)), Duration.ofMinutes(5), clock);

    @Test
    void theTokenIsLoadedOnceAndRenewedBeforeItExpires() {
        assertThat(cache.get()).isEqualTo("token-1");
        clock.advance(Duration.ofMinutes(54));
        assertThat(cache.get()).isEqualTo("token-1");
        assertThat(loads.get()).isEqualTo(1);

        // five minutes before the end of the hour
        clock.advance(Duration.ofMinutes(1));
        assertThat(cache.get()).isEqualTo("token-2");
        assertThat(cache.get()).isEqualTo("token-2");
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void invalidatingForcesANewToken() {
        assertThat(cache.get()).isEqualTo("token-1");
        cache.invalidate();
        assertThat(cache.get()).isEqualTo("token-2");
    }

    @Test
    void shortLivedTokensAreRenewedHalfwayAtTheLatest() {
        AtomicInteger shortLoads = new AtomicInteger();
        AccessTokenCache shortLived = new AccessTokenCache(
                () -> new AccessTokenCache.Token("short-" + shortLoads.incrementAndGet(), Duration.ofMinutes(4)), Duration.ofMinutes(5), clock);

        assertThat(shortLived.get()).isEqualTo("short-1");
        clock.advance(Duration.ofMinutes(1));
        assertThat(shortLived.get()).isEqualTo("short-1");
        clock.advance(Duration.ofMinutes(1));
        assertThat(shortLived.get()).isEqualTo("short-2");
    }
}
