package com.flashsale.payment;

import com.flashsale.inventory.InventoryService;
import com.flashsale.order.OrderProperties;
import com.flashsale.payment.OrderExpiryService.ExpiredOrder;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically closes expired orders. ShedLock ensures one instance sweeps at a time; the
 * conditional SQL would stay correct without it, but this avoids redundant work.
 */
@Component
class OrderExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirySweeper.class);
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final OrderExpiryService expiry;
    private final InventoryService inventory;
    private final OrderProperties properties;

    OrderExpirySweeper(OrderExpiryService expiry, InventoryService inventory, OrderProperties properties) {
        this.expiry = expiry;
        this.inventory = inventory;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${flashsale.orders.expiry-sweep-interval}")
    @SchedulerLock(name = "order-expiry-sweep", lockAtMostFor = "PT1M")
    void sweep() {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int closed = sweepOnce();
            total += closed;
            if (closed < properties.expiryBatchSize()) {
                break;
            }
        }
        if (total > 0) {
            log.info("Closed {} expired orders and released their stock", total);
        }
    }

    /** Closes one batch and releases each unit in Redis after the database commit. */
    int sweepOnce() {
        List<ExpiredOrder> closed = expiry.closeExpired(properties.expiryBatchSize());
        for (ExpiredOrder order : closed) {
            try {
                inventory.release(order.dropId(), order.userId());
            } catch (RuntimeException ex) {
                // The database is already correct; reconciliation reports the Redis drift.
                log.error("Could not release Redis stock for expired order {}", order.orderNo(), ex);
            }
        }
        return closed.size();
    }
}
