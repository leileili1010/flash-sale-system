package com.flashsale.order;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An order as shown to its owner. {@code status} is one of the persisted {@link OrderStatus}
 * values, or {@code QUEUED} while the order request is still waiting in Kafka.
 */
public record OrderView(
        String orderNo,
        long dropId,
        String status,
        BigDecimal amount,
        String failureReason,
        Instant createdAt,
        Instant expiresAt,
        Instant paidAt) {

    static final String QUEUED = "QUEUED";

    static OrderView from(PurchaseOrder order) {
        return new OrderView(
                String.valueOf(order.getOrderNo()),
                order.getDropId(),
                order.getStatus().name(),
                order.getAmount(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getExpiresAt(),
                order.getPaidAt());
    }

    static OrderView queued(long orderNo) {
        return new OrderView(String.valueOf(orderNo), 0, QUEUED, null, null, null, null, null);
    }
}
