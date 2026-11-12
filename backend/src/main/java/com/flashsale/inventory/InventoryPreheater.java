package com.flashsale.inventory;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps Redis loaded with every published drop that has not ended, so the purchase path
 * never has to fall back to the database. Runs at startup and then periodically to pick up
 * newly published or rescheduled drops. Loading is idempotent, so every instance can run it
 * without coordination.
 */
@Component
public class InventoryPreheater {

    private static final Logger log = LoggerFactory.getLogger(InventoryPreheater.class);

    private final JdbcTemplate jdbc;
    private final InventoryService inventory;
    private final Clock clock;

    InventoryPreheater(JdbcTemplate jdbc, InventoryService inventory, Clock clock) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preheatOnStartup() {
        preheat();
    }

    @Scheduled(
            initialDelayString = "${flashsale.inventory.preheat-interval}",
            fixedDelayString = "${flashsale.inventory.preheat-interval}")
    public void preheatPeriodically() {
        preheat();
    }

    public int preheat() {
        List<ActiveDrop> drops = jdbc.query("""
                        SELECT id, available_stock, start_time, end_time FROM drops
                        WHERE status = 'PUBLISHED' AND end_time > ?
                        """,
                (rs, row) -> new ActiveDrop(
                        rs.getLong("id"),
                        rs.getInt("available_stock"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time")),
                Timestamp.from(clock.instant()));

        for (ActiveDrop drop : drops) {
            // Customers who already hold a live order must stay blocked even if Redis lost its data.
            List<Long> buyers = jdbc.queryForList("""
                    SELECT user_id FROM orders
                    WHERE drop_id = ? AND status IN ('PENDING_PAYMENT', 'PAID')
                    """, Long.class, drop.id());
            inventory.load(drop.id(), drop.availableStock(),
                    drop.startTime().toInstant(), drop.endTime().toInstant(), buyers);
        }
        log.debug("Preheated {} drops into Redis", drops.size());
        return drops.size();
    }

    private record ActiveDrop(long id, int availableStock, Timestamp startTime, Timestamp endTime) {
    }
}
