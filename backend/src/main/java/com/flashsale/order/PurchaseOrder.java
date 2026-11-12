package com.flashsale.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model of an order. Orders are written exclusively through conditional SQL in
 * {@link OrderCreationService} and the payment module, so this entity is immutable.
 */
@Entity
@Table(name = "orders")
public class PurchaseOrder {

    @Id
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, updatable = false)
    private long orderNo;

    @Column(name = "drop_id", nullable = false, updatable = false)
    private long dropId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private OrderStatus status;

    @Column(name = "failure_reason", length = 100, updatable = false)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "paid_at", updatable = false)
    private Instant paidAt;

    @Column(name = "closed_at", updatable = false)
    private Instant closedAt;

    protected PurchaseOrder() {
    }

    public long getOrderNo() {
        return orderNo;
    }

    public long getDropId() {
        return dropId;
    }

    public long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
