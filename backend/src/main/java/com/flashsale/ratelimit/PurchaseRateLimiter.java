package com.flashsale.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Two token buckets guard the purchase endpoint:
 * <ul>
 *   <li><b>Global, per instance, in memory.</b> Caps the request rate one instance accepts,
 *       shedding excess load before it touches Redis. Kept local on purpose: a single shared
 *       global bucket would itself become a hot key during a spike.</li>
 *   <li><b>Per customer, in Redis.</b> Shared by all instances, so a bot cannot multiply its
 *       allowance by spreading requests across servers.</li>
 * </ul>
 */
@Component
public class PurchaseRateLimiter {

    static final String KEY_PREFIX = "ratelimit:purchase:";

    private final Bucket globalBucket;
    private final RateLimitBucketStore store;
    private final BucketConfiguration perUserConfig;

    PurchaseRateLimiter(RateLimitProperties properties, RateLimitBucketStore store) {
        this.store = store;
        this.globalBucket = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.globalPerSecond())
                        .refillGreedy(properties.globalPerSecond(), Duration.ofSeconds(1))
                        .build())
                .build();
        this.perUserConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.perUserCapacity())
                        .refillGreedy(properties.perUserCapacity(), properties.perUserPeriod())
                        .build())
                .build();
    }

    /** @throws RateLimitedException if either bucket is empty */
    public void checkAllowed(long userId) {
        ConsumptionProbe global = globalBucket.tryConsumeAndReturnRemaining(1);
        if (!global.isConsumed()) {
            throw new RateLimitedException(Duration.ofNanos(global.getNanosToWaitForRefill()));
        }
        ConsumptionProbe user = store.buckets().getProxy(KEY_PREFIX + userId, () -> perUserConfig)
                .tryConsumeAndReturnRemaining(1);
        if (!user.isConsumed()) {
            throw new RateLimitedException(Duration.ofNanos(user.getNanosToWaitForRefill()));
        }
    }
}
