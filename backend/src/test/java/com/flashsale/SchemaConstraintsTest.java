package com.flashsale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashsale.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies that the database itself rejects states that would mean overselling or duplicate
 * purchases, independent of any application logic.
 */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class SchemaConstraintsTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private long dropId;

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, display_name)
                VALUES ('shopper@example.com', 'x', 'Shopper') RETURNING id
                """, Long.class);
        dropId = insertDrop(10, 10, 0, 0);
    }

    @Test
    void seedCatalogIsLoaded() {
        Integer products = jdbc.queryForObject("SELECT count(*) FROM products", Integer.class);
        assertThat(products).isEqualTo(10);
    }

    @Test
    void stockCannotGoNegative() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE drops SET available_stock = available_stock - 11, locked_stock = locked_stock + 11 WHERE id = ?",
                dropId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stockBucketsMustAlwaysSumToTotal() {
        assertThatThrownBy(() -> jdbc.update("UPDATE drops SET available_stock = available_stock - 1 WHERE id = ?", dropId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void conditionalReservationStopsAtZero() {
        int reserved = 0;
        for (int i = 0; i < 15; i++) {
            reserved += jdbc.update("""
                    UPDATE drops SET available_stock = available_stock - 1, locked_stock = locked_stock + 1
                    WHERE id = ? AND available_stock > 0
                    """, dropId);
        }
        assertThat(reserved).isEqualTo(10);
    }

    @Test
    void customerCannotHoldTwoLiveOrdersForTheSameDrop() {
        insertOrder(1001L, "PENDING_PAYMENT");
        assertThatThrownBy(() -> insertOrder(1002L, "PAID"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void customerMayRetryAfterAnOrderIsClosed() {
        insertOrder(1001L, "CLOSED");
        insertOrder(1002L, "FAILED");
        insertOrder(1003L, "PENDING_PAYMENT");

        Integer orders = jdbc.queryForObject("SELECT count(*) FROM orders WHERE drop_id = ?", Integer.class, dropId);
        assertThat(orders).isEqualTo(3);
    }

    @Test
    void orderNumberIsAnIdempotencyKey() {
        insertOrder(1001L, "FAILED");
        Integer inserted = jdbc.update("""
                INSERT INTO orders (order_no, drop_id, user_id, amount, status, expires_at)
                VALUES (1001, ?, ?, 9.99, 'PENDING_PAYMENT', now() + interval '5 minutes')
                ON CONFLICT (order_no) DO NOTHING
                """, dropId, userId);
        assertThat(inserted).isZero();
    }

    private long insertDrop(int total, int available, int locked, int sold) {
        return jdbc.queryForObject("""
                INSERT INTO drops (product_id, title, drop_price, original_price, status, start_time, end_time,
                                   total_stock, available_stock, locked_stock, sold_stock)
                VALUES ((SELECT min(id) FROM products), 'Test drop', 9.99, 14.99, 'PUBLISHED',
                        now(), now() + interval '1 hour', ?, ?, ?, ?)
                RETURNING id
                """, Long.class, total, available, locked, sold);
    }

    private void insertOrder(long orderNo, String status) {
        jdbc.update("""
                INSERT INTO orders (order_no, drop_id, user_id, amount, status, expires_at)
                VALUES (?, ?, ?, 9.99, ?, now() + interval '5 minutes')
                """, orderNo, dropId, userId, status);
    }
}
