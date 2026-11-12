package com.flashsale.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.catalog.Drop;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.inventory.InventoryPreheater;
import com.flashsale.inventory.InventoryService;
import com.flashsale.inventory.ReservationStatus;
import com.flashsale.inventory.StockReconciliationService;
import com.flashsale.inventory.StockReconciliationService.DropStockReport;
import com.flashsale.order.OrderCreationService;
import com.flashsale.order.OrderRequestedEvent;
import com.flashsale.support.TestData;
import com.flashsale.support.TestData.StockRow;
import com.flashsale.support.TestcontainersConfiguration;
import com.flashsale.user.Role;
import com.flashsale.user.User;
import com.flashsale.user.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestData.class})
class PaymentAndExpiryTest {

    private static final AtomicLong ORDER_NUMBERS = new AtomicLong(System.nanoTime());

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData data;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private InventoryPreheater preheater;
    @Autowired
    private InventoryService inventory;
    @Autowired
    private OrderCreationService orderCreation;
    @Autowired
    private PaymentService payments;
    @Autowired
    private OrderExpirySweeper sweeper;
    @Autowired
    private StockReconciliationService reconciliation;
    @Autowired
    private UserRepository users;

    private Drop drop;

    @BeforeEach
    void setUp() {
        data.reset();
        drop = data.drop(Instant.now().minusSeconds(60), Instant.now().plus(Duration.ofHours(1)), 5);
        preheater.preheat();
    }

    @Test
    void paymentMovesTheUnitFromLockedToSold() throws Exception {
        User user = data.user();
        long orderNo = placeOrder(user);

        mockMvc.perform(post("/api/orders/{n}/pay", orderNo).header("Authorization", data.tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        assertThat(data.stock(drop.getId())).isEqualTo(new StockRow(4, 0, 1));
    }

    @Test
    void payingTwiceIsIdempotent() {
        User user = data.user();
        long orderNo = placeOrder(user);

        payments.pay(orderNo, user.getId());
        assertThat(payments.pay(orderNo, user.getId()).status()).isEqualTo("PAID");

        assertThat(data.stock(drop.getId())).isEqualTo(new StockRow(4, 0, 1));
    }

    @Test
    void cannotPayAfterThePaymentWindow() {
        User user = data.user();
        long orderNo = placeOrder(user);
        expire(orderNo);

        assertError(() -> payments.pay(orderNo, user.getId()), ErrorCode.ORDER_EXPIRED);
        assertThat(data.stock(drop.getId())).isEqualTo(new StockRow(4, 1, 0));
    }

    @Test
    void cannotPaySomeoneElsesOrder() {
        long orderNo = placeOrder(data.user());
        User stranger = data.user();

        assertError(() -> payments.pay(orderNo, stranger.getId()), ErrorCode.NOT_FOUND);
    }

    @Test
    void cannotPayAFailedOrder() {
        User user = data.user();
        long orderNo = placeOrder(user);
        jdbc.update("UPDATE drops SET available_stock = available_stock + 1, locked_stock = locked_stock - 1 WHERE id = ?",
                drop.getId());
        jdbc.update("UPDATE orders SET status = 'FAILED', failure_reason = 'SOLD_OUT' WHERE order_no = ?", orderNo);

        assertError(() -> payments.pay(orderNo, user.getId()), ErrorCode.INVALID_ORDER_STATE);
    }

    @Test
    void sweeperClosesExpiredOrdersAndReleasesTheirStockOnce() {
        List<User> late = List.of(data.user(), data.user(), data.user());
        User onTime = data.user();
        List<Long> lateOrders = late.stream().map(this::placeOrder).toList();
        placeOrder(onTime);
        lateOrders.forEach(this::expire);

        assertThat(sweeper.sweepOnce()).isEqualTo(3);
        assertThat(sweeper.sweepOnce()).isZero();

        assertThat(data.countOrders(drop.getId(), "CLOSED")).isEqualTo(3);
        assertThat(data.countOrders(drop.getId(), "PENDING_PAYMENT")).isEqualTo(1);
        assertThat(data.stock(drop.getId())).isEqualTo(new StockRow(4, 1, 0));
        assertThat(inventory.availableStock(drop.getId())).hasValue(4);
        // An expired customer may try again.
        assertThat(inventory.reserve(drop.getId(), late.getFirst().getId(), Instant.now()).status())
                .isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void paymentAndExpiryRacesAlwaysHaveExactlyOneWinner() throws Exception {
        Drop hot = data.drop(Instant.now().minusSeconds(60), Instant.now().plus(Duration.ofHours(1)), 60);
        preheater.preheat();
        List<User> buyers = new ArrayList<>();
        List<Long> orderNos = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            User buyer = data.user();
            buyers.add(buyer);
            orderNos.add(placeOrder(buyer, hot));
        }
        // Every order expires at the same instant, while payments and sweeps run concurrently.
        jdbc.update("UPDATE orders SET expires_at = now() + interval '300 milliseconds' WHERE drop_id = ?", hot.getId());

        CountDownLatch startGun = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < buyers.size(); i++) {
                long orderNo = orderNos.get(i);
                long userId = buyers.get(i).getId();
                long delayMillis = 250 + (i % 10) * 10L; // straddles the expiry instant
                executor.submit(() -> {
                    startGun.await();
                    Thread.sleep(delayMillis);
                    try {
                        payments.pay(orderNo, userId);
                    } catch (ApiException expected) {
                        // lost the race to expiry
                    }
                    return null;
                });
            }
            for (int s = 0; s < 4; s++) {
                long delayMillis = 280 + s * 20L;
                executor.submit(() -> {
                    startGun.await();
                    Thread.sleep(delayMillis);
                    sweeper.sweepOnce();
                    return null;
                });
            }
            startGun.countDown();
        }
        Thread.sleep(400);
        sweeper.sweepOnce();

        int paid = data.countOrders(hot.getId(), "PAID");
        int closed = data.countOrders(hot.getId(), "CLOSED");
        assertThat(paid + closed).isEqualTo(60);
        assertThat(data.stock(hot.getId())).isEqualTo(new StockRow(60 - paid, 0, paid));
        assertThat(inventory.availableStock(hot.getId())).hasValue(60 - paid);
        assertThat(reconciliation.report()).filteredOn(r -> r.dropId() == hot.getId())
                .singleElement().extracting(DropStockReport::drift).isEqualTo(0);
    }

    @Test
    void reconciliationDetectsAndRepairsRedisDrift() throws Exception {
        placeOrder(data.user());
        assertThat(reportFor(drop.getId()).drift()).isZero();

        redis.opsForValue().set("drop:{" + drop.getId() + "}:stock", "100");
        assertThat(reportFor(drop.getId()).drift()).isEqualTo(96);

        User admin = users.save(new User("admin-" + System.nanoTime() + "@example.com", "x", "Admin", Role.ADMIN,
                Instant.now()));
        mockMvc.perform(get("/api/admin/inventory").header("Authorization", data.tokenFor(data.user())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/inventory/{id}/resync", drop.getId())
                        .header("Authorization", data.tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redisStock").value(4))
                .andExpect(jsonPath("$.drift").value(0));
    }

    private long placeOrder(User user) {
        return placeOrder(user, drop);
    }

    /** Mirrors the real flow: reserve in Redis, then persist via the consumer's service. */
    private long placeOrder(User user, Drop target) {
        assertThat(inventory.reserve(target.getId(), user.getId(), Instant.now()).reserved()).isTrue();
        long orderNo = ORDER_NUMBERS.incrementAndGet();
        assertThat(orderCreation.create(new OrderRequestedEvent(orderNo, target.getId(), user.getId(),
                new BigDecimal("11.99"), Instant.now()))).isEqualTo(OrderCreationService.Outcome.CREATED);
        return orderNo;
    }

    private void expire(long orderNo) {
        jdbc.update("UPDATE orders SET expires_at = now() - interval '1 second' WHERE order_no = ?", orderNo);
    }

    private DropStockReport reportFor(long dropId) {
        return reconciliation.report().stream().filter(r -> r.dropId() == dropId).findFirst().orElseThrow();
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo(expected));
    }
}
