package com.flashsale.order;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The asynchronous half of a purchase: turns an {@link OrderRequestedEvent} into a persisted
 * order and locks one unit of database stock, in a single transaction.
 *
 * <p>Every step is a single conditional statement, so the outcome is correct even when events
 * are redelivered or Redis and the database disagree:
 * <ol>
 *   <li>{@code INSERT ... ON CONFLICT DO NOTHING} claims the order number. A conflict means the
 *       event was already processed, or the customer already holds a live order (partial unique
 *       index), which is recorded as a failed order.</li>
 *   <li>{@code UPDATE drops ... WHERE available_stock > 0} locks a unit. If none is left the
 *       order is marked failed instead of overselling.</li>
 * </ol>
 */
@Service
public class OrderCreationService {

    static final String SOLD_OUT = "SOLD_OUT";
    static final String DUPLICATE_PURCHASE = "DUPLICATE_PURCHASE";

    private final JdbcTemplate jdbc;
    private final OrderProperties properties;
    private final Clock clock;

    OrderCreationService(JdbcTemplate jdbc, OrderProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Outcome create(OrderRequestedEvent event) {
        Instant now = clock.instant();
        // The payment window starts when the order exists, so queueing delay never eats into it.
        Timestamp expiresAt = Timestamp.from(now.plus(properties.paymentWindow()));

        int inserted = jdbc.update("""
                INSERT INTO orders (order_no, drop_id, user_id, amount, status, created_at, expires_at)
                VALUES (?, ?, ?, ?, 'PENDING_PAYMENT', ?, ?)
                ON CONFLICT DO NOTHING
                """, event.orderNo(), event.dropId(), event.userId(), event.amount(), Timestamp.from(now), expiresAt);

        if (inserted == 0) {
            if (orderExists(event.orderNo())) {
                return Outcome.DUPLICATE_EVENT;
            }
            insertFailed(event, DUPLICATE_PURCHASE, now, expiresAt);
            return Outcome.CUSTOMER_HAS_LIVE_ORDER;
        }

        int locked = jdbc.update("""
                UPDATE drops
                SET available_stock = available_stock - 1, locked_stock = locked_stock + 1, updated_at = ?
                WHERE id = ? AND available_stock > 0
                """, Timestamp.from(now), event.dropId());

        if (locked == 0) {
            jdbc.update("""
                    UPDATE orders SET status = 'FAILED', failure_reason = ?, closed_at = ?
                    WHERE order_no = ?
                    """, SOLD_OUT, Timestamp.from(now), event.orderNo());
            return Outcome.SOLD_OUT;
        }
        return Outcome.CREATED;
    }

    private boolean orderExists(long orderNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM orders WHERE order_no = ?)", Boolean.class, orderNo));
    }

    private void insertFailed(OrderRequestedEvent event, String reason, Instant now, Timestamp expiresAt) {
        jdbc.update("""
                INSERT INTO orders (order_no, drop_id, user_id, amount, status, failure_reason,
                                    created_at, expires_at, closed_at)
                VALUES (?, ?, ?, ?, 'FAILED', ?, ?, ?, ?)
                """, event.orderNo(), event.dropId(), event.userId(), event.amount(), reason,
                Timestamp.from(now), expiresAt, Timestamp.from(now));
    }

    public enum Outcome {
        /** Order persisted and one unit of stock locked. */
        CREATED,
        /** The event was processed before; nothing changed. */
        DUPLICATE_EVENT,
        /** Redis granted a unit the database no longer has; the order failed. */
        SOLD_OUT,
        /** The customer already holds a live order for this drop; the order failed. */
        CUSTOMER_HAS_LIVE_ORDER
    }
}
