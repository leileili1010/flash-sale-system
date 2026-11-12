package com.flashsale.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashsale.support.TestcontainersConfiguration;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PurchaseRateLimiterTest {

    @Autowired
    private RateLimitBucketStore buckets;

    @Test
    void limitsEachCustomerIndependently() {
        PurchaseRateLimiter limiter = new PurchaseRateLimiter(
                new RateLimitProperties(3, Duration.ofMinutes(1), 1_000), buckets);
        long alice = uniqueUser();
        long bob = uniqueUser();

        for (int i = 0; i < 3; i++) {
            limiter.checkAllowed(alice);
        }
        assertThatThrownBy(() -> limiter.checkAllowed(alice))
                .isInstanceOfSatisfying(RateLimitedException.class,
                        ex -> assertThat(ex.retryAfterSeconds()).isBetween(1L, 20L));

        assertThatCode(() -> limiter.checkAllowed(bob)).doesNotThrowAnyException();
    }

    @Test
    void customerLimitIsSharedAcrossInstances() {
        RateLimitProperties properties = new RateLimitProperties(2, Duration.ofMinutes(1), 1_000);
        PurchaseRateLimiter instanceA = new PurchaseRateLimiter(properties, buckets);
        PurchaseRateLimiter instanceB = new PurchaseRateLimiter(properties, buckets);
        long user = uniqueUser();

        instanceA.checkAllowed(user);
        instanceB.checkAllowed(user);

        assertThatThrownBy(() -> instanceA.checkAllowed(user)).isInstanceOf(RateLimitedException.class);
    }

    @Test
    void globalLimitShedsLoadAcrossAllCustomers() {
        PurchaseRateLimiter limiter = new PurchaseRateLimiter(
                new RateLimitProperties(100, Duration.ofSeconds(1), 5), buckets);

        for (int i = 0; i < 5; i++) {
            limiter.checkAllowed(uniqueUser());
        }
        assertThatThrownBy(() -> limiter.checkAllowed(uniqueUser())).isInstanceOf(RateLimitedException.class);
    }

    private static long uniqueUser() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
