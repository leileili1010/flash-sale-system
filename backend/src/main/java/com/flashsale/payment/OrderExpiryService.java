package com.flashsale.payment;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes unpaid orders whose payment window has passed and returns their units to available
 * stock, in one transaction per batch. Replaces the reference design's delayed "pay check"
 * message with a durable, indexed query.
 */
@Service
public class OrderExpiryService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    OrderExpiryService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Only rows this statement actually moves from {@code PENDING_PAYMENT} to {@code CLOSED} are
     * returned and restocked, so a concurrent payment, a second sweeper, or a rerun can never
     * release the same unit twice. {@code SKIP LOCKED} lets sweeps run alongside payments
     * without waiting on rows another transaction is changing.
     *
     * @return the orders closed by this call
     */
    @Transactional
    public List<ExpiredOrder> closeExpired(int batchSize) {
        Timestamp now = Timestamp.from(clock.instant());
        List<ExpiredOrder> closed = jdbc.query("""
                UPDATE orders SET status = 'CLOSED', closed_at = ?
                WHERE id IN (
                    SELECT id FROM orders
                    WHERE status = 'PENDING_PAYMENT' AND expires_at <= ?
                    ORDER BY expires_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
                  AND status = 'PENDING_PAYMENT'
                RETURNING order_no, drop_id, user_id
                """,
                (rs, row) -> new ExpiredOrder(rs.getLong("order_no"), rs.getLong("drop_id"), rs.getLong("user_id")),
                now, now, batchSize);

        Map<Long, Long> unitsPerDrop = closed.stream()
                .collect(Collectors.groupingBy(ExpiredOrder::dropId, Collectors.counting()));
        unitsPerDrop.forEach((dropId, units) -> jdbc.update("""
                UPDATE drops
                SET available_stock = available_stock + ?, locked_stock = locked_stock - ?, updated_at = ?
                WHERE id = ?
                """, units, units, now, dropId));
        return closed;
    }

    public record ExpiredOrder(long orderNo, long dropId, long userId) {
    }
}
