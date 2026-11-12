package com.flashsale.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashsale.catalog.CatalogService;
import com.flashsale.catalog.Drop;
import com.flashsale.catalog.DropRepository;
import com.flashsale.catalog.DropStatus;
import com.flashsale.catalog.Product;
import com.flashsale.catalog.ProductRepository;
import com.flashsale.inventory.InventoryService.Reservation;
import com.flashsale.support.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InventoryServiceTest {

    private static final long DROP = 42L;

    @Autowired
    private InventoryService inventory;
    @Autowired
    private InventoryPreheater preheater;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DropRepository drops;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CatalogService catalog;

    private final Instant start = Instant.parse("2026-12-01T18:00:00Z");
    private final Instant end = start.plus(Duration.ofHours(1));
    private final Instant duringDrop = start.plus(Duration.ofMinutes(1));

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM drops");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void reservesAndDecrementsStock() {
        inventory.load(DROP, 3, start, end, List.of());

        Reservation reservation = inventory.reserve(DROP, 1, duringDrop);

        assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.remaining()).isEqualTo(2);
        assertThat(inventory.availableStock(DROP)).hasValue(2);
    }

    @Test
    void allowsOneReservationPerCustomer() {
        inventory.load(DROP, 3, start, end, List.of());
        inventory.reserve(DROP, 1, duringDrop);

        assertThat(inventory.reserve(DROP, 1, duringDrop).status()).isEqualTo(ReservationStatus.ALREADY_PURCHASED);
        assertThat(inventory.availableStock(DROP)).hasValue(2);
    }

    @Test
    void reportsSoldOutOnceStockIsGone() {
        inventory.load(DROP, 1, start, end, List.of());
        inventory.reserve(DROP, 1, duringDrop);

        Reservation late = inventory.reserve(DROP, 2, duringDrop);
        assertThat(late.status()).isEqualTo(ReservationStatus.SOLD_OUT);
        assertThat(inventory.availableStock(DROP)).hasValue(0);
    }

    @Test
    void enforcesTheReleaseWindow() {
        inventory.load(DROP, 5, start, end, List.of());

        assertThat(inventory.reserve(DROP, 1, start.minusMillis(1)).status()).isEqualTo(ReservationStatus.NOT_STARTED);
        assertThat(inventory.reserve(DROP, 1, start).status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(inventory.reserve(DROP, 2, end).status()).isEqualTo(ReservationStatus.ENDED);
        assertThat(inventory.availableStock(DROP)).hasValue(4);
    }

    @Test
    void reportsDropsThatAreNotLoaded() {
        assertThat(inventory.reserve(999, 1, duringDrop).status()).isEqualTo(ReservationStatus.NOT_LOADED);
    }

    @Test
    void releaseIsIdempotent() {
        inventory.load(DROP, 2, start, end, List.of());
        inventory.reserve(DROP, 1, duringDrop);

        assertThat(inventory.release(DROP, 1)).isTrue();
        assertThat(inventory.release(DROP, 1)).isFalse();
        assertThat(inventory.release(DROP, 7)).isFalse(); // never reserved

        assertThat(inventory.availableStock(DROP)).hasValue(2);
        assertThat(inventory.reserve(DROP, 1, duringDrop).status()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void neverOversellsUnderConcurrentLoad() throws Exception {
        int stock = 100;
        int shoppers = 2_000;
        inventory.load(DROP, stock, start, end, List.of());

        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<ReservationStatus>> attempts = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < shoppers; i++) {
                long userId = i + 1;
                attempts.add(executor.submit(() -> {
                    startGun.await();
                    return inventory.reserve(DROP, userId, duringDrop).status();
                }));
            }
            startGun.countDown();
        }

        Map<ReservationStatus, Integer> outcomes = new EnumMap<>(ReservationStatus.class);
        for (Future<ReservationStatus> attempt : attempts) {
            outcomes.merge(attempt.get(), 1, Integer::sum);
        }

        assertThat(outcomes).containsEntry(ReservationStatus.RESERVED, stock)
                .containsEntry(ReservationStatus.SOLD_OUT, shoppers - stock);
        assertThat(inventory.availableStock(DROP)).hasValue(0);
        assertThat(redis.opsForSet().size(InventoryKeys.buyers(DROP))).isEqualTo(stock);
    }

    @Test
    void preheatLoadsActiveDropsWithoutOverwritingLiveCounters() {
        Drop live = saveDrop(Instant.now().minus(Duration.ofMinutes(1)), Instant.now().plus(Duration.ofHours(1)), 10);
        Drop ended = saveDrop(Instant.now().minus(Duration.ofHours(2)), Instant.now().minus(Duration.ofHours(1)), 10);

        assertThat(preheater.preheat()).isEqualTo(1);
        assertThat(inventory.availableStock(live.getId())).hasValue(10);
        assertThat(inventory.availableStock(ended.getId())).isEmpty();

        inventory.reserve(live.getId(), 1, Instant.now());
        preheater.preheat();

        assertThat(inventory.availableStock(live.getId())).hasValue(9);
    }

    @Test
    void preheatRestoresBuyersOfLiveOrdersAfterRedisDataLoss() {
        Drop live = saveDrop(Instant.now().minus(Duration.ofMinutes(1)), Instant.now().plus(Duration.ofHours(1)), 10);
        long userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, display_name) VALUES ('a@example.com', 'x', 'A') RETURNING id
                """, Long.class);
        jdbc.update("UPDATE drops SET available_stock = 9, locked_stock = 1 WHERE id = ?", live.getId());
        jdbc.update("""
                INSERT INTO orders (order_no, drop_id, user_id, amount, status, expires_at)
                VALUES (1, ?, ?, 9.99, 'PENDING_PAYMENT', now() + interval '5 minutes')
                """, live.getId(), userId);

        preheater.preheat();

        assertThat(inventory.availableStock(live.getId())).hasValue(9);
        assertThat(inventory.reserve(live.getId(), userId, Instant.now()).status())
                .isEqualTo(ReservationStatus.ALREADY_PURCHASED);
    }

    @Test
    void catalogReportsLiveRedisStock() {
        Drop live = saveDrop(Instant.now().minus(Duration.ofMinutes(1)), Instant.now().plus(Duration.ofHours(1)), 10);
        preheater.preheat();
        inventory.reserve(live.getId(), 1, Instant.now());

        assertThat(catalog.getDrop(live.getId()).availableStock()).isEqualTo(9);
    }

    @Test
    void keysExpireAfterTheDropEnds() {
        inventory.load(DROP, 5, start, end, List.of(3L));

        Long ttlSeconds = redis.getExpire(InventoryKeys.stock(DROP));
        long expected = Duration.between(Instant.now(), end.plus(InventoryService.RETENTION_AFTER_END)).toSeconds();
        assertThat(ttlSeconds).isBetween(expected - 5, expected + 5);
    }

    private Drop saveDrop(Instant start, Instant end, int stock) {
        Product product = products.findBySku("FF-MOSS-001").orElseThrow();
        return drops.save(new Drop(product, "Drop", new BigDecimal("9.99"), product.getRetailPrice(),
                DropStatus.PUBLISHED, start, end, stock, Instant.now()));
    }
}
