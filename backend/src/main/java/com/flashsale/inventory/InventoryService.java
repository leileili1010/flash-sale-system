package com.flashsale.inventory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Live inventory held in Redis: the first and fastest gate in the purchase path.
 *
 * <p>Redis absorbs the traffic spike at release time. Only requests that win a unit here
 * continue to order creation, so the database sees roughly as many writes as there is
 * stock, no matter how many shoppers try at once. PostgreSQL still enforces the same
 * invariants, so Redis is an optimization for load, never the last line of defense.
 */
@Service
public class InventoryService {

    /** Keys outlive the drop briefly so late expiry releases still find them. */
    static final Duration RETENTION_AFTER_END = Duration.ofDays(1);

    private static final RedisScript<List> RESERVE =
            RedisScript.of(new ClassPathResource("redis/reserve.lua"), List.class);
    private static final RedisScript<Long> RELEASE =
            RedisScript.of(new ClassPathResource("redis/release.lua"), Long.class);

    private final StringRedisTemplate redis;

    InventoryService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Reservation reserve(long dropId, long userId, Instant now) {
        List<?> result = redis.execute(RESERVE,
                List.of(InventoryKeys.stock(dropId), InventoryKeys.buyers(dropId), InventoryKeys.meta(dropId)),
                String.valueOf(userId), String.valueOf(now.toEpochMilli()));
        return new Reservation(
                ReservationStatus.fromScriptCode((Long) result.get(0)),
                (Long) result.get(1));
    }

    /**
     * Returns a reserved unit to the pool. Safe to call more than once for the same
     * customer and drop.
     *
     * @return {@code true} if a unit was released by this call
     */
    public boolean release(long dropId, long userId) {
        Long result = redis.execute(RELEASE,
                List.of(InventoryKeys.stock(dropId), InventoryKeys.buyers(dropId)),
                String.valueOf(userId));
        return result != null && result >= 0;
    }

    public OptionalInt availableStock(long dropId) {
        String value = redis.opsForValue().get(InventoryKeys.stock(dropId));
        return value == null ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(value));
    }

    /**
     * Loads a drop into Redis. The stock counter is only initialized if absent: once a drop
     * is live, Redis holds reservations the database has not seen yet, so overwriting the
     * counter from the database would hand out the same units twice.
     */
    public void load(long dropId, int availableStock, Instant startTime, Instant endTime,
                     Collection<Long> existingBuyers) {
        redis.opsForValue().setIfAbsent(InventoryKeys.stock(dropId), String.valueOf(availableStock));
        redis.opsForHash().putAll(InventoryKeys.meta(dropId), Map.of(
                "start", String.valueOf(startTime.toEpochMilli()),
                "end", String.valueOf(endTime.toEpochMilli())));
        if (!existingBuyers.isEmpty()) {
            redis.opsForSet().add(InventoryKeys.buyers(dropId),
                    existingBuyers.stream().map(String::valueOf).toArray(String[]::new));
        }
        Instant expireAt = endTime.plus(RETENTION_AFTER_END);
        for (String key : InventoryKeys.all(dropId)) {
            redis.expireAt(key, expireAt);
        }
    }

    public record Reservation(ReservationStatus status, long remaining) {

        public boolean reserved() {
            return status == ReservationStatus.RESERVED;
        }
    }
}
