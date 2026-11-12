package com.flashsale.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param perUserCapacity  purchase attempts one customer may burst
 * @param perUserPeriod    time for a customer's allowance to fully refill
 * @param globalPerSecond  purchase attempts one application instance accepts per second
 */
@ConfigurationProperties(prefix = "flashsale.rate-limit.purchase")
public record RateLimitProperties(long perUserCapacity, Duration perUserPeriod, long globalPerSecond) {
}
