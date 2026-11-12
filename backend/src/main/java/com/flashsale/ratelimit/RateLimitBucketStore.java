package com.flashsale.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Owns the Redis connection used for distributed rate-limit buckets.
 *
 * <p>The connection is opened from Spring's Lettuce client, which the connection factory
 * recreates whenever it is stopped and started again. Following the factory's lifecycle
 * (starting after it, stopping before it) keeps this connection valid across restarts
 * instead of holding on to a closed one.
 */
@Component
public class RateLimitBucketStore implements SmartLifecycle {

    private final LettuceConnectionFactory connectionFactory;

    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile ProxyManager<String> buckets;

    RateLimitBucketStore(LettuceConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    ProxyManager<String> buckets() {
        ProxyManager<String> current = buckets;
        if (current == null) {
            throw new IllegalStateException("Rate-limit store is not running");
        }
        return current;
    }

    @Override
    public void start() {
        RedisClient client = (RedisClient) connectionFactory.getNativeClient();
        connection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        buckets = Bucket4jLettuce.casBasedBuilder(connection)
                // Idle buckets expire once they would have refilled anyway, so Redis does not accumulate keys.
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                .build();
    }

    @Override
    public void stop() {
        buckets = null;
        StatefulRedisConnection<String, byte[]> current = connection;
        connection = null;
        if (current != null) {
            current.close();
        }
    }

    @Override
    public boolean isRunning() {
        return connection != null;
    }

    @Override
    public int getPhase() {
        return connectionFactory.getPhase() + 1;
    }
}
