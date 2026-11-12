package com.flashsale.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flashsale.catalog.CatalogCache;
import com.flashsale.catalog.DropInfo;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.common.id.IdProperties;
import com.flashsale.common.id.SnowflakeIdGenerator;
import com.flashsale.inventory.InventoryService;
import com.flashsale.inventory.InventoryService.Reservation;
import com.flashsale.inventory.ReservationStatus;
import com.flashsale.ratelimit.PurchaseRateLimiter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/** If Kafka does not acknowledge the order event, the reserved unit must go back to the pool. */
class PurchaseServicePublishFailureTest {

    @Test
    @SuppressWarnings("unchecked")
    void releasesTheReservationWhenTheEventCannotBePublished() {
        InventoryService inventory = mock(InventoryService.class);
        CatalogCache catalog = mock(CatalogCache.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        PendingOrderTracker pending = mock(PendingOrderTracker.class);

        when(catalog.dropInfo(7L)).thenReturn(new DropInfo(7L, "Drop", null, new BigDecimal("9.99"),
                new BigDecimal("14.99"), Instant.now(), Instant.now().plusSeconds(60), 10));
        when(inventory.reserve(eq(7L), eq(3L), any())).thenReturn(new Reservation(ReservationStatus.RESERVED, 9));
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.<SendResult<String, Object>>failedFuture(new IllegalStateException("broker down")));

        Clock clock = Clock.systemUTC();
        PurchaseService service = new PurchaseService(mock(PurchaseRateLimiter.class), catalog, inventory,
                new SnowflakeIdGenerator(new IdProperties(1), clock), kafka, pending,
                new OrderProperties(Duration.ofMinutes(10), Duration.ofSeconds(1), 500), clock);

        assertThatThrownBy(() -> service.purchase(7L, 3L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.errorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        verify(inventory).release(7L, 3L);
    }
}
