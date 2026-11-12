package com.flashsale.order;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published after a customer wins a Redis reservation. The order number is assigned before
 * publishing and doubles as the idempotency key, so redelivered events create no duplicates.
 */
public record OrderRequestedEvent(long orderNo, long dropId, long userId, BigDecimal amount, Instant requestedAt) {
}
