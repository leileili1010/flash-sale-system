package com.flashsale.support;

import com.flashsale.auth.TokenService;
import com.flashsale.catalog.Drop;
import com.flashsale.catalog.DropRepository;
import com.flashsale.catalog.DropStatus;
import com.flashsale.catalog.Product;
import com.flashsale.catalog.ProductRepository;
import com.flashsale.user.Role;
import com.flashsale.user.User;
import com.flashsale.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Builds and cleans up integration-test data. Import alongside {@link TestcontainersConfiguration}. */
@Component
public class TestData {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final UserRepository users;
    private final ProductRepository products;
    private final DropRepository drops;
    private final TokenService tokens;

    TestData(JdbcTemplate jdbc, StringRedisTemplate redis, UserRepository users, ProductRepository products,
             DropRepository drops, TokenService tokens) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.users = users;
        this.products = products;
        this.drops = drops;
        this.tokens = tokens;
    }

    public void reset() {
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM drops");
        jdbc.update("DELETE FROM users");
        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    public User user() {
        String email = "shopper-" + UUID.randomUUID() + "@example.com";
        return users.save(new User(email, "not-a-real-hash", "Shopper", Role.USER, Instant.now()));
    }

    public User admin() {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        return users.save(new User(email, "not-a-real-hash", "Admin", Role.ADMIN, Instant.now()));
    }

    public String tokenFor(User user) {
        return "Bearer " + tokens.issue(user).value();
    }

    public Drop drop(Instant start, Instant end, int stock) {
        Product product = products.findBySku("FF-MOSS-001").orElseThrow();
        return drops.save(new Drop(product, "Test drop", new BigDecimal("11.99"), product.getRetailPrice(),
                DropStatus.PUBLISHED, start, end, stock, Instant.now()));
    }

    public StockRow stock(long dropId) {
        return jdbc.queryForObject(
                "SELECT available_stock, locked_stock, sold_stock FROM drops WHERE id = ?",
                (rs, row) -> new StockRow(rs.getInt(1), rs.getInt(2), rs.getInt(3)), dropId);
    }

    public int countOrders(long dropId, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE drop_id = ? AND status = ?",
                Integer.class, dropId, status);
    }

    public record StockRow(int available, int locked, int sold) {
    }
}
