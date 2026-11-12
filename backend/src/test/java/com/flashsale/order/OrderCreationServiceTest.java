package com.flashsale.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashsale.catalog.Drop;
import com.flashsale.order.OrderCreationService.Outcome;
import com.flashsale.support.TestData;
import com.flashsale.support.TestcontainersConfiguration;
import com.flashsale.user.User;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestData.class})
class OrderCreationServiceTest {

    private static final AtomicLong ORDER_NUMBERS = new AtomicLong(System.nanoTime());

    @Autowired
    private OrderCreationService service;
    @Autowired
    private PurchaseOrderRepository orders;
    @Autowired
    private OrderProperties properties;
    @Autowired
    private TestData data;

    private Drop drop;
    private User user;

    @BeforeEach
    void setUp() {
        data.reset();
        drop = data.drop(Instant.now().minusSeconds(60), Instant.now().plus(Duration.ofHours(1)), 2);
        user = data.user();
    }

    @Test
    void createsAPendingOrderAndLocksOneUnit() {
        OrderRequestedEvent event = event(user);

        assertThat(service.create(event)).isEqualTo(Outcome.CREATED);

        PurchaseOrder order = orders.findByOrderNo(event.orderNo()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getAmount()).isEqualByComparingTo("11.99");
        assertThat(Duration.between(order.getCreatedAt(), order.getExpiresAt())).isEqualTo(properties.paymentWindow());
        assertThat(data.stock(drop.getId())).isEqualTo(new TestData.StockRow(1, 1, 0));
    }

    @Test
    void redeliveredEventsAreIgnored() {
        OrderRequestedEvent event = event(user);

        assertThat(service.create(event)).isEqualTo(Outcome.CREATED);
        assertThat(service.create(event)).isEqualTo(Outcome.DUPLICATE_EVENT);
        assertThat(service.create(event)).isEqualTo(Outcome.DUPLICATE_EVENT);

        assertThat(data.countOrders(drop.getId(), "PENDING_PAYMENT")).isEqualTo(1);
        assertThat(data.stock(drop.getId())).isEqualTo(new TestData.StockRow(1, 1, 0));
    }

    @Test
    void failsInsteadOfOversellingWhenTheDatabaseIsOutOfStock() {
        service.create(event(data.user()));
        service.create(event(data.user()));

        OrderRequestedEvent late = event(user);
        assertThat(service.create(late)).isEqualTo(Outcome.SOLD_OUT);

        PurchaseOrder order = orders.findByOrderNo(late.orderNo()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureReason()).isEqualTo(OrderCreationService.SOLD_OUT);
        assertThat(data.stock(drop.getId())).isEqualTo(new TestData.StockRow(0, 2, 0));
    }

    @Test
    void recordsAFailedOrderWhenTheCustomerAlreadyHoldsALiveOrder() {
        service.create(event(user));

        OrderRequestedEvent second = event(user);
        assertThat(service.create(second)).isEqualTo(Outcome.CUSTOMER_HAS_LIVE_ORDER);

        PurchaseOrder order = orders.findByOrderNo(second.orderNo()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureReason()).isEqualTo(OrderCreationService.DUPLICATE_PURCHASE);
        assertThat(data.stock(drop.getId())).isEqualTo(new TestData.StockRow(1, 1, 0));
    }

    private OrderRequestedEvent event(User buyer) {
        return new OrderRequestedEvent(ORDER_NUMBERS.incrementAndGet(), drop.getId(), buyer.getId(),
                new BigDecimal("11.99"), Instant.now());
    }
}
