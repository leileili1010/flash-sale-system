package com.flashsale.order;

/** Persisted order states. Transitions: PENDING_PAYMENT to PAID or CLOSED; FAILED is terminal. */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    CLOSED,
    FAILED
}
