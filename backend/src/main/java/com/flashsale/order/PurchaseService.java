package com.flashsale.order;

import com.flashsale.catalog.CatalogCache;
import com.flashsale.catalog.DropInfo;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.common.id.SnowflakeIdGenerator;
import com.flashsale.inventory.InventoryService;
import com.flashsale.inventory.InventoryService.Reservation;
import com.flashsale.ratelimit.PurchaseRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * The synchronous half of a purchase. It touches only Redis and Kafka, never the database,
 * so request latency stays flat however many shoppers arrive at once:
 *
 * <ol>
 *   <li>rate limit the caller,</li>
 *   <li>atomically reserve a unit in Redis (window, one-per-customer and stock checks),</li>
 *   <li>publish an {@link OrderRequestedEvent} and wait for Kafka to acknowledge it.</li>
 * </ol>
 *
 * If the event cannot be published, the reservation is released so the unit is not lost.
 */
@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final PurchaseRateLimiter rateLimiter;
    private final CatalogCache catalog;
    private final InventoryService inventory;
    private final SnowflakeIdGenerator ids;
    private final KafkaTemplate<String, Object> kafka;
    private final PendingOrderTracker pendingOrders;
    private final OrderProperties properties;
    private final Clock clock;

    PurchaseService(PurchaseRateLimiter rateLimiter, CatalogCache catalog, InventoryService inventory,
                    SnowflakeIdGenerator ids, KafkaTemplate<String, Object> kafka,
                    PendingOrderTracker pendingOrders, OrderProperties properties, Clock clock) {
        this.rateLimiter = rateLimiter;
        this.catalog = catalog;
        this.inventory = inventory;
        this.ids = ids;
        this.kafka = kafka;
        this.pendingOrders = pendingOrders;
        this.properties = properties;
        this.clock = clock;
    }

    public PurchaseAccepted purchase(long dropId, long userId) {
        rateLimiter.checkAllowed(userId);
        DropInfo drop = catalog.dropInfo(dropId);
        Instant now = clock.instant();

        Reservation reservation = inventory.reserve(dropId, userId, now);
        if (!reservation.reserved()) {
            throw new ApiException(reservation.status().errorCode());
        }

        var event = new OrderRequestedEvent(ids.nextId(), dropId, userId, drop.dropPrice(), now);
        pendingOrders.track(event.orderNo(), userId);
        try {
            // Keyed by drop: one drop's orders stay in order on one partition, and its stock row
            // is updated by one consumer at a time instead of contended by many.
            kafka.send(OrderMessagingConfig.ORDER_REQUESTS_TOPIC, String.valueOf(dropId), event)
                    .get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            inventory.release(dropId, userId);
            pendingOrders.forget(event.orderNo());
            log.error("Failed to publish order request {}; reservation released", event.orderNo(), ex);
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Your purchase could not be submitted. Please try again.");
        }
        return new PurchaseAccepted(String.valueOf(event.orderNo()), dropId, "QUEUED");
    }

    /** Order numbers are 64-bit, beyond JavaScript's safe-integer range, so they travel as strings. */
    public record PurchaseAccepted(String orderNo, long dropId, String status) {
    }
}
