package com.flashsale.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.flashsale.catalog.Drop;
import com.flashsale.inventory.InventoryPreheater;
import com.flashsale.inventory.InventoryService;
import com.flashsale.inventory.StockReconciliationService;
import com.flashsale.order.OrderMessagingConfig;
import com.flashsale.order.OrderRequestedEvent;
import com.flashsale.payment.OrderExpiryService;
import com.flashsale.support.TestData;
import com.flashsale.support.TestData.StockRow;
import com.flashsale.support.TestcontainersConfiguration;
import com.flashsale.user.User;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * A complete drop over real HTTP: a burst of shoppers, payments, expiries, retries and
 * redelivered events, followed by a check of every invariant the system promises.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestData.class})
class DropLifecycleTest {

    private static final int STOCK = 50;
    private static final int SHOPPERS = 400;
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;
    @Autowired
    private TestData data;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private InventoryPreheater preheater;
    @Autowired
    private InventoryService inventory;
    @Autowired
    private StockReconciliationService reconciliation;
    @Autowired
    private KafkaTemplate<String, Object> kafka;
    @Autowired
    private OrderExpiryService expiryService;

    private RestClient http;

    @BeforeEach
    void setUp() {
        data.reset();
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
    }

    @Test
    void aFullDropNeverOversellsAndEndsConsistent() throws Exception {
        Drop drop = data.drop(Instant.now().minusSeconds(1), Instant.now().plus(Duration.ofHours(1)), STOCK);
        preheater.preheat();
        List<Shopper> shoppers = new ArrayList<>();
        for (int i = 0; i < SHOPPERS; i++) {
            User user = data.user();
            shoppers.add(new Shopper(user, data.tokenFor(user)));
        }

        // 1. Release: every shopper hits "buy" at the same instant, some twice.
        Map<Integer, AtomicInteger> statusCounts = new ConcurrentHashMap<>();
        Map<Long, String> winners = new ConcurrentHashMap<>();
        CountDownLatch startGun = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Shopper shopper : shoppers) {
                int attempts = shopper.user().getId() % 4 == 0 ? 2 : 1;
                for (int a = 0; a < attempts; a++) {
                    executor.submit(() -> {
                        startGun.await();
                        var response = http.post().uri("/api/drops/{id}/purchase", drop.getId())
                                .header("Authorization", shopper.token())
                                .retrieve().toEntity(JsonNode.class);
                        statusCounts.computeIfAbsent(response.getStatusCode().value(), k -> new AtomicInteger())
                                .incrementAndGet();
                        if (response.getStatusCode().value() == 202) {
                            winners.put(shopper.user().getId(), response.getBody().get("orderNo").asString());
                        }
                        return null;
                    });
                }
            }
            startGun.countDown();
        }

        assertThat(statusCounts.get(202).get()).isEqualTo(STOCK);
        assertThat(winners).hasSize(STOCK);
        assertThat(statusCounts.keySet()).containsOnly(202, 409);
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(data.countOrders(drop.getId(), "PENDING_PAYMENT")).isEqualTo(STOCK));
        assertThat(data.stock(drop.getId())).isEqualTo(new StockRow(0, STOCK, 0));

        // 2. Kafka redelivers every order event: nothing changes.
        jdbc.query("SELECT order_no, user_id, amount FROM orders WHERE drop_id = ?", rs -> {
            kafka.send(OrderMessagingConfig.ORDER_REQUESTS_TOPIC, String.valueOf(drop.getId()),
                    new OrderRequestedEvent(rs.getLong(1), drop.getId(), rs.getLong(2), rs.getBigDecimal(3), Instant.now()));
        }, drop.getId());

        // 3. The first half of the winners pay; the rest let their payment window lapse.
        List<Long> winnerIds = new ArrayList<>(winners.keySet());
        int payers = STOCK / 2;
        for (int i = 0; i < payers; i++) {
            Shopper shopper = find(shoppers, winnerIds.get(i));
            var paid = http.post().uri("/api/orders/{n}/pay", winners.get(shopper.user().getId()))
                    .header("Authorization", shopper.token()).retrieve().toEntity(JsonNode.class);
            assertThat(paid.getStatusCode().value()).isEqualTo(200);
        }
        jdbc.update("UPDATE orders SET expires_at = now() - interval '1 second' "
                + "WHERE drop_id = ? AND status = 'PENDING_PAYMENT'", drop.getId());

        // 4. The sweeper (forced to run promptly) returns the lapsed units to stock.
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            forceSweep();
            assertThat(data.countOrders(drop.getId(), "CLOSED")).isEqualTo(STOCK - payers);
        });
        assertThat(data.stock(drop.getId())).isEqualTo(new StockRow(STOCK - payers, 0, payers));
        assertThat(inventory.availableStock(drop.getId())).hasValue(STOCK - payers);

        // 5. Everyone who did not win tries again; the released units sell exactly once more.
        AtomicInteger secondWave = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Shopper shopper : shoppers) {
                if (winners.containsKey(shopper.user().getId())) {
                    continue;
                }
                executor.submit(() -> {
                    var response = http.post().uri("/api/drops/{id}/purchase", drop.getId())
                            .header("Authorization", shopper.token()).retrieve().toEntity(JsonNode.class);
                    if (response.getStatusCode().value() == 202) {
                        secondWave.incrementAndGet();
                    }
                    return null;
                });
            }
        }
        assertThat(secondWave.get()).isEqualTo(STOCK - payers);
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(data.countOrders(drop.getId(), "PENDING_PAYMENT")).isEqualTo(STOCK - payers));

        // 6. Final invariants.
        assertThat(data.stock(drop.getId())).isEqualTo(new StockRow(0, STOCK - payers, payers));
        assertThat(data.countOrders(drop.getId(), "PAID")).isEqualTo(payers);
        assertThat(data.countOrders(drop.getId(), "FAILED")).isZero();
        Integer customersWithTwoLiveOrders = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT user_id FROM orders
                    WHERE drop_id = ? AND status IN ('PENDING_PAYMENT', 'PAID')
                    GROUP BY user_id HAVING count(*) > 1) duplicates
                """, Integer.class, drop.getId());
        assertThat(customersWithTwoLiveOrders).isZero();
        BigDecimal revenue = jdbc.queryForObject(
                "SELECT coalesce(sum(amount), 0) FROM orders WHERE drop_id = ? AND status = 'PAID'",
                BigDecimal.class, drop.getId());
        assertThat(revenue).isEqualByComparingTo(drop.getDropPrice().multiply(BigDecimal.valueOf(payers)));
        assertThat(inventory.availableStock(drop.getId())).hasValue(0);
        assertThat(reconciliation.report()).filteredOn(r -> r.dropId() == drop.getId())
                .singleElement().satisfies(report -> assertThat(report.drift()).isZero());
    }

    private void forceSweep() {
        expiryService.closeExpired(500).forEach(order -> inventory.release(order.dropId(), order.userId()));
    }

    private static Shopper find(List<Shopper> shoppers, long userId) {
        return shoppers.stream().filter(s -> s.user().getId() == userId).findFirst().orElseThrow();
    }

    private record Shopper(User user, String token) {
    }
}
