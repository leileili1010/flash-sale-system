package com.flashsale.payment;

import com.flashsale.auth.AuthenticatedUser;
import com.flashsale.order.OrderView;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class PaymentController {

    private final PaymentService payments;

    PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** Simulated checkout: marks the order paid. Safe to retry. */
    @PostMapping("/{orderNo}/pay")
    public OrderView pay(@PathVariable long orderNo, @AuthenticatedUser long userId) {
        return payments.pay(orderNo, userId);
    }
}
