package com.flashsale.order;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

    static final int RECENT_ORDERS_LIMIT = 50;

    private final PurchaseOrderRepository orders;
    private final PendingOrderTracker pendingOrders;

    OrderQueryService(PurchaseOrderRepository orders, PendingOrderTracker pendingOrders) {
        this.orders = orders;
        this.pendingOrders = pendingOrders;
    }

    /**
     * Returns the caller's order. Other customers' orders are reported as not found rather than
     * forbidden, so order numbers cannot be probed.
     */
    @Transactional(readOnly = true)
    public OrderView getOrder(long orderNo, long userId) {
        Optional<PurchaseOrder> order = orders.findByOrderNo(orderNo);
        if (order.isPresent()) {
            if (order.get().getUserId() != userId) {
                throw notFound(orderNo);
            }
            return OrderView.from(order.get());
        }
        if (pendingOrders.owner(orderNo).orElse(-1) == userId) {
            return OrderView.queued(orderNo);
        }
        throw notFound(orderNo);
    }

    @Transactional(readOnly = true)
    public List<OrderView> recentOrders(long userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(RECENT_ORDERS_LIMIT)).stream()
                .map(OrderView::from)
                .toList();
    }

    private static ApiException notFound(long orderNo) {
        return new ApiException(ErrorCode.NOT_FOUND, "Order " + orderNo + " was not found.");
    }
}
