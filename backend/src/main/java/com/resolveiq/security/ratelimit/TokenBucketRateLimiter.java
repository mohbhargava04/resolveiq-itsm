package com.resolveiq.security.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * <b>Token bucket</b> rate limiting. Each key (rule + client address) has a bucket holding up to
 * {@code capacity} tokens that refills continuously at {@code capacity / window}. Every request takes
 * one token; with none left the request is refused and the caller is told how long to wait.
 *
 * <p>Bursts up to the capacity are allowed (someone mistyping a password three times quickly), while
 * the long-run rate is capped (a script trying thousands of passwords). Buckets live in memory; with
 * several server instances each would limit separately, which a shared store such as Redis fixes.</p>
 */
public class TokenBucketRateLimiter {

    /** Result of one attempt. */
    public record Decision(boolean allowed, long retryAfterSeconds, long remaining) {
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;
        long lastSeenNanos;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillNanos = now;
            this.lastSeenNanos = now;
        }
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoClock;
    private long lastSweep;

    public TokenBucketRateLimiter() {
        this(System::nanoTime);
    }

    public TokenBucketRateLimiter(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        this.lastSweep = nanoClock.getAsLong();
    }

    public Decision tryAcquire(String key, int capacity, Duration window) {
        long now = nanoClock.getAsLong();
        double refillPerNano = (double) capacity / window.toNanos();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        Decision decision;
        synchronized (bucket) {
            double refilled = bucket.tokens + (now - bucket.lastRefillNanos) * refillPerNano;
            bucket.tokens = Math.min(capacity, refilled);
            bucket.lastRefillNanos = now;
            bucket.lastSeenNanos = now;
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                decision = new Decision(true, 0, (long) Math.floor(bucket.tokens));
            } else {
                double nanosUntilToken = (1 - bucket.tokens) / refillPerNano;
                decision = new Decision(false, Math.max(1, (long) Math.ceil(nanosUntilToken / 1_000_000_000d)), 0);
            }
        }
        sweepIfDue(now);
        return decision;
    }

    public int trackedKeys() {
        return buckets.size();
    }

    public void reset() {
        buckets.clear();
    }

    /** Forget buckets idle for over an hour so memory does not grow with every address ever seen. */
    private void sweepIfDue(long now) {
        if (now - lastSweep < Duration.ofMinutes(5).toNanos()) {
            return;
        }
        lastSweep = now;
        long cutoff = now - Duration.ofHours(1).toNanos();
        buckets.entrySet().removeIf(e -> e.getValue().lastSeenNanos < cutoff);
    }
}
