package com.bjarne.datingrecommendationsuserservice.external;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Holds one client-credentials access token and fetches a new one shortly before the old one expires.
 */
public final class AccessTokenCache {

    /** @param validFor lifetime reported by the token endpoint */
    public record Token(String value, Duration validFor) {
    }

    private final Supplier<Token> loader;
    private final Duration renewBefore;
    private final Clock clock;

    private String token;
    private Instant renewAt = Instant.MIN;

    public AccessTokenCache(Supplier<Token> loader, Duration renewBefore, Clock clock) {
        this.loader = loader;
        this.renewBefore = renewBefore;
        this.clock = clock;
    }

    public synchronized String get() {
        Instant now = clock.instant();
        if (token == null || !now.isBefore(renewAt)) {
            Token fresh = loader.get();
            // never renew later than halfway through a short-lived token
            Duration margin = renewBefore.compareTo(fresh.validFor().dividedBy(2)) < 0 ? renewBefore : fresh.validFor().dividedBy(2);
            token = fresh.value();
            renewAt = now.plus(fresh.validFor()).minus(margin);
        }
        return token;
    }

    /** Forces a new token on the next call, e.g. after the service rejected the current one. */
    public synchronized void invalidate() {
        token = null;
        renewAt = Instant.MIN;
    }
}
