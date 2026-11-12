package com.flashsale.payment;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.order.OrderQueryService;
import com.flashsale.order.OrderStatus;
import com.flashsale.order.OrderView;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completes payment for a pending order. This is a simulated gateway: a real integration would
 * call this after the provider confirms the charge, keyed by the same order number.
 *
 * <p>The status change is a compare-and-set ({@code WHERE status = 'PENDING_PAYMENT' AND
 * expires_at > now}), so a payment and the expiry sweeper can never both succeed for one order,
 * and paying twice changes nothing. The reserved unit moves from locked to sold in the same
 * transaction.
 */
@Service
public class PaymentService {

    private final JdbcTemplate jdbc;
    private final OrderQueryService orders;
    private final Clock clock;

    PaymentService(JdbcTemplate jdbc, OrderQueryService orders, Clock clock) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional
    public OrderView pay(long orderNo, long userId) {
        Timestamp now = Timestamp.from(clock.instant());
        List<Long> paidDrop = jdbc.queryForList("""
                UPDATE orders SET status = 'PAID', paid_at = ?
                WHERE order_no = ? AND user_id = ? AND status = 'PENDING_PAYMENT' AND expires_at > ?
                RETURNING drop_id
                """, Long.class, now, orderNo, userId, now);

        if (!paidDrop.isEmpty()) {
            jdbc.update("""
                    UPDATE drops SET locked_stock = locked_stock - 1, sold_stock = sold_stock + 1, updated_at = ?
                    WHERE id = ?
                    """, now, paidDrop.getFirst());
            return orders.getOrder(orderNo, userId);
        }

        // Nothing changed: explain why. Ownership is checked inside getOrder (404 for strangers).
        OrderView order = orders.getOrder(orderNo, userId);
        return switch (order.status()) {
            case "PAID" -> order; // idempotent: a retried payment succeeds without charging twice
            case "QUEUED" -> throw new ApiException(ErrorCode.INVALID_ORDER_STATE,
                    "The order is still being created. Please try again in a moment.");
            case "PENDING_PAYMENT", "CLOSED" -> throw new ApiException(ErrorCode.ORDER_EXPIRED);
            default -> throw new ApiException(ErrorCode.INVALID_ORDER_STATE,
                    "Order " + orderNo + " is " + OrderStatus.valueOf(order.status()).name().toLowerCase() + ".");
        };
    }
}
