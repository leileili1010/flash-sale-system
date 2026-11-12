package com.flashsale.order;

import com.flashsale.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order requests. Database work commits first; Redis is corrected afterwards for the
 * rare outcomes where the reservation and the database disagreed.
 */
@Component
class OrderRequestListener {

    private static final Logger log = LoggerFactory.getLogger(OrderRequestListener.class);

    private final OrderCreationService orders;
    private final InventoryService inventory;

    OrderRequestListener(OrderCreationService orders, InventoryService inventory) {
        this.orders = orders;
        this.inventory = inventory;
    }

    @KafkaListener(topics = OrderMessagingConfig.ORDER_REQUESTS_TOPIC, groupId = "order-creation")
    void onOrderRequested(OrderRequestedEvent event) {
        OrderCreationService.Outcome outcome = orders.create(event);
        switch (outcome) {
            case CREATED, DUPLICATE_EVENT -> log.debug("Order {} {}", event.orderNo(), outcome);
            case SOLD_OUT -> {
                // Redis over-counted; drop the buyer so they are not blocked, but do not add stock back.
                inventory.forgetBuyer(event.dropId(), event.userId());
                log.warn("Order {} failed: drop {} has no database stock left", event.orderNo(), event.dropId());
            }
            case CUSTOMER_HAS_LIVE_ORDER -> {
                // The customer keeps their existing order; only the extra unit goes back.
                inventory.restock(event.dropId());
                log.warn("Order {} failed: user {} already holds a live order for drop {}",
                        event.orderNo(), event.userId(), event.dropId());
            }
        }
    }
}
