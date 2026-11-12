package com.flashsale.order;

import com.flashsale.auth.AuthenticatedUser;
import com.flashsale.order.PurchaseService.PurchaseAccepted;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final PurchaseService purchases;
    private final OrderQueryService orders;

    OrderController(PurchaseService purchases, OrderQueryService orders) {
        this.purchases = purchases;
        this.orders = orders;
    }

    /**
     * Returns {@code 202 Accepted}: the unit is reserved and the order is being created
     * asynchronously. Poll the {@code Location} URL for the result.
     */
    @PostMapping("/drops/{dropId}/purchase")
    public ResponseEntity<PurchaseAccepted> purchase(@PathVariable long dropId, @AuthenticatedUser long userId) {
        PurchaseAccepted accepted = purchases.purchase(dropId, userId);
        return ResponseEntity.accepted().location(URI.create("/api/orders/" + accepted.orderNo())).body(accepted);
    }

    @GetMapping("/orders/{orderNo}")
    public OrderView getOrder(@PathVariable long orderNo, @AuthenticatedUser long userId) {
        return orders.getOrder(orderNo, userId);
    }

    @GetMapping("/orders")
    public List<OrderView> myOrders(@AuthenticatedUser long userId) {
        return orders.recentOrders(userId);
    }
}
