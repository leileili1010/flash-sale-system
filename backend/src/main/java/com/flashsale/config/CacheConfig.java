package com.flashsale.config;

import com.flashsale.catalog.CatalogCache;
import com.flashsale.catalog.DropInfo;
import com.flashsale.catalog.ProductView;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis-backed caches. Each cache stores one concrete type as plain JSON (no embedded class
 * names), which keeps entries readable in redis-cli and safe to deserialize.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    static final String KEY_PREFIX = "flashsale:cache:";

    @Bean
    RedisCacheManagerBuilderCustomizer catalogCaches(JsonMapper jsonMapper) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .prefixCacheNameWith(KEY_PREFIX);

        return builder -> builder
                .withCacheConfiguration(CatalogCache.PRODUCTS, base
                        .entryTtl(Duration.ofMinutes(10))
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(jsonMapper, ProductView.class))))
                // Short TTL so schedule or price edits propagate even without explicit eviction.
                .withCacheConfiguration(CatalogCache.DROP_INFO, base
                        .entryTtl(Duration.ofSeconds(30))
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(jsonMapper, DropInfo.class))));
    }
}
