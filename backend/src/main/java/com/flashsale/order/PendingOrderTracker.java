package com.flashsale.order;

import java.time.Duration;
import java.util.OptionalLong;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Remembers which customer owns an order number between the purchase response and the moment
 * the consumer persists the order, so the customer can poll their order while it is queued.
 */
@Component
class PendingOrderTracker {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    PendingOrderTracker(StringRedisTemplate redis) {
        this.redis = redis;
    }

    void track(long orderNo, long userId) {
        redis.opsForValue().set(key(orderNo), String.valueOf(userId), TTL);
    }

    void forget(long orderNo) {
        redis.delete(key(orderNo));
    }

    OptionalLong owner(long orderNo) {
        String value = redis.opsForValue().get(key(orderNo));
        return value == null ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(value));
    }

    private static String key(long orderNo) {
        return "order:pending:" + orderNo;
    }
}
