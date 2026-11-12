package com.flashsale.inventory;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Compares Redis inventory with the database.
 *
 * <p>A reservation decrements Redis immediately but reaches the database only when its order
 * event is consumed. Those in-flight reservations are the buyers Redis knows about that have
 * no live order yet, so in a consistent system:
 * <pre>redis stock = database available stock - in-flight reservations</pre>
 * Any difference is drift, for example from a Redis correction that failed after a database
 * commit. Readings taken during heavy traffic can show transient drift; persistent drift is
 * what matters.
 */
@Service
public class StockReconciliationService {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final Clock clock;

    StockReconciliationService(JdbcTemplate jdbc, StringRedisTemplate redis, Clock clock) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.clock = clock;
    }

    public List<DropStockReport> report() {
        return jdbc.query("""
                SELECT d.id, d.title, d.total_stock, d.available_stock, d.locked_stock, d.sold_stock,
                       (SELECT count(*) FROM orders o
                        WHERE o.drop_id = d.id AND o.status IN ('PENDING_PAYMENT', 'PAID')) AS live_orders
                FROM drops d
                WHERE d.status = 'PUBLISHED' AND d.end_time > ?
                ORDER BY d.start_time
                """, (rs, row) -> build(
                        rs.getLong("id"), rs.getString("title"), rs.getInt("total_stock"),
                        rs.getInt("available_stock"), rs.getInt("locked_stock"), rs.getInt("sold_stock"),
                        rs.getInt("live_orders")),
                Timestamp.from(clock.instant()));
    }

    /**
     * Overwrites a drop's Redis state from the database: stock from available stock, buyers from
     * live orders. Any in-flight reservations are discarded, so run it only while the drop is
     * quiet (for example after its consumers have drained).
     */
    public DropStockReport resync(long dropId) {
        List<Integer> available = jdbc.queryForList(
                "SELECT available_stock FROM drops WHERE id = ? AND status = 'PUBLISHED'", Integer.class, dropId);
        if (available.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Drop " + dropId + " was not found.");
        }
        List<String> buyers = jdbc.queryForList("""
                SELECT user_id::text FROM orders WHERE drop_id = ? AND status IN ('PENDING_PAYMENT', 'PAID')
                """, String.class, dropId);

        redis.opsForValue().set(InventoryKeys.stock(dropId), String.valueOf(available.getFirst()));
        redis.delete(InventoryKeys.buyers(dropId));
        if (!buyers.isEmpty()) {
            redis.opsForSet().add(InventoryKeys.buyers(dropId), buyers.toArray(String[]::new));
        }
        return report().stream().filter(r -> r.dropId() == dropId).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Drop " + dropId + " is not active."));
    }

    private DropStockReport build(long dropId, String title, int total, int available, int locked, int sold,
                                  int liveOrders) {
        String stock = redis.opsForValue().get(InventoryKeys.stock(dropId));
        Long buyerCount = redis.opsForSet().size(InventoryKeys.buyers(dropId));
        int buyers = buyerCount == null ? 0 : buyerCount.intValue();
        if (stock == null) {
            return new DropStockReport(dropId, title, total, available, locked, sold, null, buyers, liveOrders, 0, null);
        }
        int redisStock = Integer.parseInt(stock);
        int inFlight = buyers - liveOrders;
        int drift = redisStock - (available - inFlight);
        return new DropStockReport(dropId, title, total, available, locked, sold, redisStock, buyers, liveOrders,
                inFlight, drift);
    }

    /**
     * @param redisStock  {@code null} when the drop is not loaded in Redis
     * @param drift       {@code null} when not loaded; otherwise 0 when consistent. Positive means
     *                    Redis would sell more than the database holds (orders would then fail
     *                    safely); negative means units are stranded.
     */
    public record DropStockReport(
            long dropId,
            String title,
            int totalStock,
            int databaseAvailable,
            int databaseLocked,
            int databaseSold,
            Integer redisStock,
            int redisBuyers,
            int liveOrders,
            int inFlightReservations,
            Integer drift) {

        public boolean consistent() {
            return drift == null || drift == 0;
        }
    }
}
