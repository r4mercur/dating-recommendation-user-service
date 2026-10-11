package com.bjarne.datingrecommendationsuserservice.external;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/**
 * Spaces requests evenly so that a service limit like "4 requests per second" is never exceeded, across all threads.
 * Callers wait for their slot; if the wait would exceed {@code maxWait}, the request is refused instead of queueing up.
 */
public final class RequestRateLimiter {

    /** Keeps a little below the real limit, so clock jitter and window boundaries cannot push us over it. */
    private static final double SAFETY_FACTOR = 1.1;

    private final long intervalNanos;
    private final long maxWaitNanos;
    private long nextSlotNanos = System.nanoTime();

    public RequestRateLimiter(int requestsPerSecond, Duration maxWait) {
        this.intervalNanos = (long) (1_000_000_000d / requestsPerSecond * SAFETY_FACTOR);
        this.maxWaitNanos = maxWait.toNanos();
    }

    /** Blocks until a request may be sent. Returns false if the wait would be longer than the allowed maximum. */
    public boolean tryAcquire() {
        long wait;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextSlotNanos);
            wait = slot - now;
            if (wait > maxWaitNanos) {
                return false;
            }
            nextSlotNanos = slot + intervalNanos;
        }

        // parkNanos may return early (spuriously or because of a leftover unpark permit of a pooled thread),
        // so park again until the slot is really reached; an interrupt ends the wait and stays set for the caller
        long deadline = System.nanoTime() + wait;
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0 && !Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(remaining);
        }
        return true;
    }
}
