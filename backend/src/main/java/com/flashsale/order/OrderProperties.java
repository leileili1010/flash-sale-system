package com.flashsale.order;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param paymentWindow   how long a customer has to pay before the reservation is released
 * @param publishTimeout  how long the purchase request waits for Kafka to acknowledge the event
 * @param expiryBatchSize maximum number of expired orders closed per sweep transaction
 */
@ConfigurationProperties(prefix = "flashsale.orders")
public record OrderProperties(Duration paymentWindow, Duration publishTimeout, int expiryBatchSize) {
}
